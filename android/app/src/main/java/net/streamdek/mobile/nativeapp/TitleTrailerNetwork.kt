package net.streamdek.mobile.nativeapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose

/**
 * Monitors active network connectivity to enforce Wi-Fi only restriction on hero trailer playback.
 */
@Composable
fun rememberTitleTrailerNetworkAllowed(wifiOnly: Boolean): Boolean {
  val context = LocalContext.current.applicationContext
  val isWifiOnlyAllowed = produceState(initialValue = false, context, wifiOnly) {
    if (!wifiOnly) {
      value = true
      return@produceState
    }
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    if (connectivityManager == null) {
      value = false
      return@produceState
    }
    fun update() {
      val caps = runCatching {
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
      }.getOrNull()
      value = caps != null &&
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) { update() }
      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) { update() }
      override fun onLost(network: Network) { value = false }
    }
    val registered = runCatching {
      connectivityManager.registerDefaultNetworkCallback(callback)
    }.isSuccess
    update()
    awaitClose {
      if (registered) {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
      }
    }
  }
  return !wifiOnly || isWifiOnlyAllowed.value
}
