package net.streamdek.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import net.streamdek.mobile.nativeapp.EpisodeNotificationSystem

/**
 * The app, and the one image loader everything in it draws through.
 *
 * Without an [ImageLoaderFactory] Coil builds a default loader on first use, and two of those
 * defaults are wrong for a screen that is mostly artwork:
 *
 * The default OkHttp dispatcher allows **five** concurrent requests per host. Every poster, every
 * backdrop and every episode still comes from `image.tmdb.org` — one host — so the whole app was
 * loading artwork five images at a time no matter how many rows were on screen. A home screen
 * asking for a hundred posters queued ninety-five of them behind the first five, which is why a
 * row often only filled in as it was scrolled to: scrolling did not start those requests, it just
 * gave the queue time to reach them.
 *
 * And the default respects cache headers. TMDB artwork at a given path never changes — the size is
 * in the URL — so revalidating it costs a round trip to be told nothing happened.
 */
class MainApplication : Application(), ImageLoaderFactory {
  override fun onCreate() {
    net.streamdek.mobile.nativeapp.Perf.startupMark("application.onCreate")
    super.onCreate()
    // First thing the process does, because what it affects is the *next* launch: the system builds
    // the splash from the manifest theme before any of this runs, and this is what tells it which
    // night mode to build it in.
    net.streamdek.mobile.nativeapp.applyAppNightMode(this)
    // As early as the process allows: a crash before this runs cannot be recorded. It only
    // installs a handler in front of the existing one -- see Stability -- so it changes nothing
    // about what the user sees when the app does die.
    net.streamdek.mobile.nativeapp.Stability.install(this, BuildConfig.VERSION_NAME)
    EpisodeNotificationSystem.ensureBackgroundWork(this)
  }

  override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
    .okHttpClient {
      OkHttpClient.Builder()
        .dispatcher(
          Dispatcher().apply {
            maxRequests = 96
            maxRequestsPerHost = 32
          },
        )
        .cache(okhttp3.Cache(cacheDir.resolve("image_http_cache"), 256L * 1024 * 1024))
        .connectionPool(okhttp3.ConnectionPool(48, 10, TimeUnit.MINUTES))
        .addNetworkInterceptor { chain ->
          val response = chain.proceed(chain.request())
          response.newBuilder()
            .header("Cache-Control", "public, max-age=2592000, max-stale=2592000")
            .removeHeader("Pragma")
            .build()
        }
        .addNetworkInterceptor(net.streamdek.mobile.nativeapp.mediaserver.MediaServerAuth.interceptor)
        .build()
    }
    .memoryCache {
      MemoryCache.Builder(this)
        .maxSizePercent(0.35)
        .strongReferencesEnabled(true)
        .build()
    }
    .diskCache {
      DiskCache.Builder()
        .directory(cacheDir.resolve("image_cache"))
        .maxSizeBytes(512L * 1024 * 1024)
        .build()
    }
    .allowHardware(true)
    .allowRgb565(true)
    .respectCacheHeaders(false)
    .crossfade(if (animationsDisabled()) 0 else 140)
    .build()

  /**
   * Whether the device has been asked to stop animating.
   *
   * The same animator duration scale the UI reads through `LocalReducedMotion`. The image loader
   * is built before any of the Compose tree exists, so it has to ask the setting directly rather
   * than through a composition local.
   */
  private fun animationsDisabled(): Boolean = runCatching {
    android.provider.Settings.Global.getFloat(
      contentResolver,
      android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
      1f,
    )
  }.getOrDefault(1f) == 0f
}
