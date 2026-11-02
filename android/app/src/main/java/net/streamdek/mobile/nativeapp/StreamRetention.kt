package net.streamdek.mobile.nativeapp

import java.net.URI
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Handles retention policies and URL expiry parsing for Fast Resume streaming links.
 */
object StreamRetention {
  val modes: List<String> = listOf("auto", "playback", "1h", "6h", "12h", "24h", "3d", "7d", "expiry")

  /**
   * Attempts to determine the expiry timestamp in epoch milliseconds from signed streaming URLs
   * (e.g. AWS S3 presigned URLs, AWS CloudFront signed URLs).
   */
  fun parseExpiry(url: String): Long? {
    return runCatching {
      val rawQuery = URI(url).rawQuery ?: return null
      val params = rawQuery.split('&').associate { pair ->
        val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
        val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        key to value
      }
      if (params.keys.containsAll(listOf("x-amz-signature", "x-amz-date", "x-amz-expires", "x-amz-credential"))) {
        val date = LocalDateTime.parse(params.getValue("x-amz-date"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
          .toInstant(ZoneOffset.UTC).toEpochMilli()
        val expiresSec = params.getValue("x-amz-expires").toLong()
        if (expiresSec !in 1..604800) return null
        Math.addExact(date, expiresSec * 1000L)
      } else if (params.keys.containsAll(listOf("signature", "key-pair-id", "expires")) && !params.containsKey("policy")) {
        val expiresSec = params.getValue("expires").toLong()
        val expiry = Math.multiplyExact(expiresSec, 1000L)
        if (expiry <= 0) null else expiry
      } else {
        null
      }
    }.getOrNull()
  }

  /**
   * Fallback TTL in milliseconds for the given mode when no explicit URL expiry timestamp is known.
   */
  fun fallbackRetentionMillis(mode: String, successfulResumes: Int, consecutiveErrors: Int): Long {
    return when (mode) {
      "1h" -> 3600_000L
      "3d" -> 259_200_000L
      "6h" -> 21_600_000L
      "7d" -> 604_800_000L
      "12h" -> 43_200_000L
      "24h" -> 86_400_000L
      "playback" -> 0L
      else -> {
        if (consecutiveErrors >= 2) 300_000L // 5 minutes after repeated errors
        else if (successfulResumes < 5 || consecutiveErrors != 0) 1800_000L // 30 minutes
        else 3600_000L // 1 hour default
      }
    }
  }

  /**
   * Checks whether a retained streaming link is still within its validity window.
   */
  fun isRetainedStreamValid(
    mode: String,
    savedAtMillis: Long,
    nowMillis: Long,
    expiresAtMillis: Long?,
    successfulResumes: Int,
    consecutiveErrors: Int
  ): Boolean {
    if (savedAtMillis <= 0 || nowMillis < savedAtMillis) return false
    val elapsed = nowMillis - savedAtMillis
    // Hard cap at 7 days or 5 minutes prior to known URL expiration
    if (elapsed >= 604_800_000L || (expiresAtMillis != null && nowMillis >= expiresAtMillis - 300_000L)) {
      return false
    }
    val maxAge = if ((mode == "expiry" || mode == "auto") && expiresAtMillis != null) {
      604_800_000L
    } else {
      fallbackRetentionMillis(mode, successfulResumes, consecutiveErrors)
    }
    return elapsed < maxAge
  }
}
