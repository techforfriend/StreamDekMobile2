package net.streamdek.mobile.nativeapp

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Manages the disposable 250 MB Media3 video resume cache for Fast Resume.
 */
@OptIn(UnstableApi::class)
object FastResumeCache {

  private const val CACHE_DIR_NAME = "media3-resume-v1"
  private const val CACHE_SIZE_BYTES = 262_144_000L // 250 MB
  private const val MIN_FREE_SPACE_BYTES = 128 * 1024 * 1024L // 128 MB
  private const val PREFS_NAME = "streamdek_resume_settings"
  private const val KEY_PREFIX = "video:"

  @Volatile
  private var simpleCache: SimpleCache? = null
  private val lock = Any()

  private fun getOrCreateCache(context: Context): SimpleCache? {
    simpleCache?.let { return it }
    synchronized(lock) {
      simpleCache?.let { return it }
      val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
      if (context.cacheDir.usableSpace < MIN_FREE_SPACE_BYTES && !cacheDir.exists()) {
        return null
      }
      return runCatching {
        val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
        val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
        SimpleCache(cacheDir, evictor, databaseProvider).also {
          simpleCache = it
        }
      }.getOrNull()
    }
  }

  fun isEnabled(context: Context, owner: String): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_PREFIX + owner, true)
  }

  fun setEnabled(context: Context, owner: String, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_PREFIX + owner, enabled)
      .apply()
    if (!enabled) {
      clear(context)
    }
  }

  fun clear(context: Context) {
    synchronized(lock) {
      runCatching {
        simpleCache?.release()
      }
      simpleCache = null
      runCatching {
        File(context.cacheDir, CACHE_DIR_NAME).deleteRecursively()
      }
    }
  }

  fun wrapWithResumeCache(
    context: Context,
    upstreamFactory: DataSource.Factory,
    owner: String
  ): DataSource.Factory {
    if (!isEnabled(context, owner)) return upstreamFactory
    val cache = getOrCreateCache(context) ?: return upstreamFactory

    return CacheDataSource.Factory()
      .setCache(cache)
      .setUpstreamDataSourceFactory(upstreamFactory)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }
}
