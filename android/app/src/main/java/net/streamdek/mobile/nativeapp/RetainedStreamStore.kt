package net.streamdek.mobile.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Encrypted persistent store for retained streaming links used by Fast Resume.
 */
class RetainedStreamStore(private val context: Context) {

  private val gson = Gson()
  private val baseDir = File(context.applicationContext.noBackupFilesDir, "resume-links-v1")

  companion object {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "streamdek_resume_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_RESUME_SETTINGS = "streamdek_resume_settings"
    private const val KEY_RETENTION_PREFIX = "retention:"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private val lock = Any()

    fun isEligible(stream: AddonStream, url: String): Boolean {
      val drm = stream.drmLicenseType
      return url.startsWith("https://", ignoreCase = true) &&
        !url.contains("127.0.0.1") &&
        stream.nzbUrl == null &&
        (drm == null || drm.isBlank()) &&
        stream.drmClearKeys.isEmpty()
    }

    fun ineligibilityReason(stream: AddonStream, url: String): String {
      if (!url.startsWith("https://", ignoreCase = true)) return "non_https"
      if (url.contains("127.0.0.1")) return "loopback"
      if (stream.nzbUrl != null) return "usenet"
      val drm = stream.drmLicenseType
      if (!drm.isNullOrBlank() || stream.drmClearKeys.isNotEmpty()) return "drm"
      return "eligible"
    }

    private fun hashKey(key: String): String {
      val md = MessageDigest.getInstance("SHA-256")
      val bytes = md.digest(key.toByteArray(StandardCharsets.UTF_8))
      return bytes.joinToString("") { "%02x".format(it) }
    }
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
    keyStore.load(null)
    val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    if (existingEntry != null) {
      return existingEntry.secretKey
    }
    val generator = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER)
    generator.init(
      KeyGenParameterSpec.Builder(KEY_ALIAS, 3)
        .setBlockModes("GCM")
        .setEncryptionPaddings("NoPadding")
        .build()
    )
    return generator.generateKey()
  }

  private fun fileForKey(key: String): AtomicFile {
    return AtomicFile(File(baseDir, hashKey(key)))
  }

  private fun readEncrypted(key: String): String? {
    synchronized(lock) {
      return runCatching {
        val atomicFile = fileForKey(key)
        val data = atomicFile.readFully()
        if (data.size < 29 || data.size > 1_048_576) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, data, 0, GCM_IV_LENGTH))
        val decrypted = cipher.doFinal(data, GCM_IV_LENGTH, data.size - GCM_IV_LENGTH)
        String(decrypted, StandardCharsets.UTF_8)
      }.getOrNull()
    }
  }

  private fun writeEncrypted(key: String, plain: String): Boolean {
    synchronized(lock) {
      return runCatching {
        val bytes = plain.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > 1_000_000) return false
        baseDir.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv ?: return false
        val ciphertext = cipher.doFinal(bytes)

        val atomicFile = fileForKey(key)
        var stream: FileOutputStream? = null
        try {
          stream = atomicFile.startWrite()
          stream.write(iv)
          stream.write(ciphertext)
          atomicFile.finishWrite(stream)
        } catch (e: Exception) {
          if (stream != null) atomicFile.failWrite(stream)
          throw e
        }

        // Clean up stale files older than 7 days
        val files = baseDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".bak") }.orEmpty()
        val now = System.currentTimeMillis()
        for (f in files) {
          if (now - f.lastModified() > 604_800_000L) {
            f.delete()
          }
        }
        true
      }.getOrDefault(false)
    }
  }

  private fun deleteKey(key: String) {
    synchronized(lock) {
      fileForKey(key).delete()
    }
  }

  fun clearAll() {
    synchronized(lock) {
      baseDir.listFiles()?.forEach { it.delete() }
    }
  }

  fun getRetentionMode(owner: String): String {
    return context.getSharedPreferences(PREFS_RESUME_SETTINGS, Context.MODE_PRIVATE)
      .getString(KEY_RETENTION_PREFIX + owner, "auto") ?: "auto"
  }

  private fun getStats(owner: String, provider: String): Pair<Int, Int> {
    val raw = readEncrypted("stats:$owner:$provider") ?: return 0 to 0
    return runCatching {
      val json = JSONObject(raw)
      json.optInt("ok", 0) to json.optInt("failed", 0)
    }.getOrDefault(0 to 0)
  }

  fun recordResult(owner: String, provider: String, success: Boolean) {
    val (ok, failed) = getStats(owner, provider)
    val nextOk = (ok + if (success) 1 else 0).coerceAtMost(10)
    val nextFailed = (failed + if (!success) 1 else 0).coerceAtMost(2)
    val json = JSONObject().put("ok", nextOk).put("failed", nextFailed).toString()
    writeEncrypted("stats:$owner:$provider", json)
  }

  fun remove(owner: String, contentId: String) {
    deleteKey("$owner|$contentId")
  }

  fun saveStream(owner: String, contentId: String, stream: AddonStream, url: String, auth: String) {
    val mode = getRetentionMode(owner)
    if (mode == "playback" || !isEligible(stream, url)) {
      return
    }
    val existingUrl = getSavedUrl(owner, contentId, auth)
    if (existingUrl == url) return

    val json = JSONObject()
      .put("at", System.currentTimeMillis())
      .put("auth", auth)
      .put("provider", stream.addonId)
      .put("url", url)
      .put("stream", gson.toJson(stream))
      .toString()
    writeEncrypted("$owner|$contentId", json)
  }

  fun getSavedStream(owner: String, contentId: String, auth: String?): AddonStream? {
    val raw = readEncrypted("$owner|$contentId") ?: return null
    return runCatching {
      val json = JSONObject(raw)
      if (auth != null && json.optString("auth") != auth) {
        remove(owner, contentId)
        return null
      }
      val url = json.getString("url")
      val provider = json.optString("provider", "")
      val (ok, failed) = getStats(owner, provider)
      val now = System.currentTimeMillis()
      val savedAt = json.getLong("at")
      val expiryAt = StreamRetention.parseExpiry(url)
      val mode = getRetentionMode(owner)

      if (StreamRetention.isRetainedStreamValid(mode, savedAt, now, expiryAt, ok, failed)) {
        val streamJson = json.getString("stream")
        gson.fromJson(streamJson, AddonStream::class.java)
      } else {
        remove(owner, contentId)
        null
      }
    }.getOrNull()
  }

  fun getSavedUrl(owner: String, contentId: String, auth: String): String? {
    if (getSavedStream(owner, contentId, auth) == null) return null
    val raw = readEncrypted("$owner|$contentId") ?: return null
    return runCatching { JSONObject(raw).getString("url") }.getOrNull()
  }
}
