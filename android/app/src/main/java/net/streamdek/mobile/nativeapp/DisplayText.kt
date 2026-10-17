package net.streamdek.mobile.nativeapp

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

private val mojibakeMarkers = setOf('\u00C3', '\u00C2', '\u00E2', '\u00F0', '\uFFFD')

internal fun sanitizeDisplayText(value: String?): String? {
  var current = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
  val windows1252 = Charset.forName("windows-1252")
  repeat(8) {
    if (current.none(mojibakeMarkers::contains)) return@repeat
    val candidate = runCatching {
      val bytes = windows1252.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(java.nio.CharBuffer.wrap(current))
      StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(ByteArray(bytes.remaining()).also(bytes::get)))
        .toString()
    }.getOrNull() ?: return@repeat
    if (candidate == current) return@repeat
    current = candidate
  }
  return current.replace('\uFFFD', ' ').replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ").trim()
}

/**
 * The stored form of an audio-language choice.
 *
 * Delegates to [Languages], which knows every ISO language rather than the nine that used to be
 * listed here. That mattered beyond tidiness: anything outside the nine fell through to English, so
 * a viewer who chose Vietnamese got English audio and no indication why.
 */
internal fun normalizePreferredAudioLanguage(value: String?): String =
  when (val normalized = Languages.normalize(value?.trim()?.lowercase().let {
    if (it == "default" || it == "auto") Languages.ORIGINAL else it
  })) {
    Languages.ORIGINAL -> Languages.ORIGINAL
    Languages.NONE, "" -> "en"
    else -> normalized
  }

/** Track tags for one audio-language choice; empty means "leave the release alone". */
internal fun preferredAudioLanguageTags(value: String?): List<String> =
  when (val normalized = normalizePreferredAudioLanguage(value)) {
    Languages.ORIGINAL -> emptyList()
    else -> Languages.tags(normalized)
  }

/**
 * Track tags for a first and second choice, in order of preference.
 *
 * A player asked for several languages takes the earliest one it can satisfy, which is exactly what
 * a secondary preference means: use it when the release carries nothing in the first.
 */
internal fun orderedLanguageTags(primary: String?, secondary: String?): List<String> =
  (preferredAudioLanguageTags(primary) + Languages.tags(secondary)).distinct()

internal fun addonStreamPlaybackIdentity(stream: AddonStream): String {
  stream.url?.trim()?.takeIf(String::isNotBlank)?.let { return "url:${it.lowercase()}" }
  stream.infoHash?.trim()?.takeIf(String::isNotBlank)?.let {
    return "torrent:${it.lowercase()}:${stream.fileIdx ?: -1}:${stream.filename.orEmpty().trim().lowercase()}"
  }
  return listOf(stream.addonId, stream.bingeGroup, stream.filename, stream.name, stream.title)
    .joinToString("|") { it.orEmpty().trim().lowercase() }
}

internal val AUDIO_LANGUAGE_KEYWORDS: Map<String, List<String>> = mapOf(
  "hi" to listOf("hindi", "hin"),
  "en" to listOf("english", "eng"),
  "ta" to listOf("tamil", "tam"),
  "te" to listOf("telugu", "tel"),
  "ml" to listOf("malayalam", "mal"),
  "kn" to listOf("kannada", "kan"),
  "mr" to listOf("marathi", "mar"),
  "bn" to listOf("bengali", "ben", "bangla"),
  "pa" to listOf("punjabi", "pan"),
  "gu" to listOf("gujarati", "guj"),
  "ur" to listOf("urdu", "urd"),
  "es" to listOf("spanish", "esp", "espanol", "español", "castellano", "spa"),
  "fr" to listOf("french", "francais", "français", "fra", "fre"),
  "de" to listOf("german", "deutsch", "deu", "ger"),
  "it" to listOf("italian", "italiano", "ita"),
  "pt" to listOf("portuguese", "portugues", "português", "por"),
  "ru" to listOf("russian", "rus"),
  "ja" to listOf("japanese", "jpn", "jap"),
  "ko" to listOf("korean", "kor"),
  "zh" to listOf("chinese", "mandarin", "cantonese", "zho", "chi"),
  "ar" to listOf("arabic", "ara"),
  "tr" to listOf("turkish", "tur")
)

/**
 * Checks whether an audio track matches a preferred language.
 * Checks both the normalized track language code and searches track title/name for language keywords.
 */
internal fun matchesAudioLanguage(track: net.streamdek.mobile.mpv.MpvTrackInfo, preferredLang: String?): Boolean {
  val normalizedPref = normalizePreferredAudioLanguage(preferredLang)
  if (normalizedPref == Languages.ORIGINAL || normalizedPref.isBlank() || normalizedPref == Languages.NONE) return false

  val allowedTags = preferredAudioLanguageTags(normalizedPref).map(Languages::normalize)
  val trackLangNorm = track.language?.let(Languages::normalize).orEmpty()

  // 1. Exact or tag match on track language
  if (trackLangNorm.isNotEmpty() && trackLangNorm != "und" && trackLangNorm != "unknown") {
    if (trackLangNorm in allowedTags || Languages.normalize(trackLangNorm) == normalizedPref) {
      return true
    }
  }

  // 2. Keyword check in track title / name if language tag is missing, "und", or mismatched
  val trackTitle = track.title?.lowercase() ?: return false
  val keywords = AUDIO_LANGUAGE_KEYWORDS[normalizedPref] ?: listOf(normalizedPref)
  val labelName = Languages.label(normalizedPref).lowercase()
  val allKeywords = (keywords + listOf(labelName)).distinct().filter { it.length >= 2 }

  return allKeywords.any { kw ->
    val regex = Regex("""(?i)(?:^|[^a-zA-Z0-9])${Regex.escape(kw)}(?:[^a-zA-Z0-9]|$)""")
    regex.containsMatchIn(trackTitle)
  }
}

/**
 * Finds the best audio track matching primary preference first, then secondary preference.
 */
internal fun findBestAudioTrack(
  tracks: List<net.streamdek.mobile.mpv.MpvTrackInfo>,
  primaryLang: String?,
  secondaryLang: String? = null
): net.streamdek.mobile.mpv.MpvTrackInfo? {
  if (tracks.isEmpty()) return null
  val primaryNorm = normalizePreferredAudioLanguage(primaryLang)
  if (primaryNorm != Languages.ORIGINAL && primaryNorm.isNotBlank()) {
    val match = tracks.firstOrNull { matchesAudioLanguage(it, primaryNorm) }
    if (match != null) return match
  }
  val secondaryNorm = secondaryLang?.takeIf { it.isNotBlank() && it != "none" }?.let(::normalizePreferredAudioLanguage)
  if (secondaryNorm != null && secondaryNorm != Languages.ORIGINAL && secondaryNorm != primaryNorm) {
    val match = tracks.firstOrNull { matchesAudioLanguage(it, secondaryNorm) }
    if (match != null) return match
  }
  return null
}
