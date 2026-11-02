package net.streamdek.mobile.nativeapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.streamdek.mobile.R

/**
 * Settings rows for Fast Resume (video cache and stream URL retention).
 */
@Composable
fun ResumeSettingsRows(owner: String) {
  val context = LocalContext.current
  val prefs = remember(context) {
    context.getSharedPreferences("streamdek_resume_settings", Context.MODE_PRIVATE)
  }
  val videoKey = "video:$owner"
  val retentionKey = "retention:$owner"

  var videoResumeEnabled by remember(owner) {
    mutableStateOf(prefs.getBoolean(videoKey, true))
  }
  var retentionMode by remember(owner) {
    mutableStateOf(prefs.getString(retentionKey, "auto") ?: "auto")
  }

  val retentionOptions = StreamRetention.modes
  val retentionLabels = mapOf(
    "auto" to stringResource(R.string.resume_auto),
    "playback" to stringResource(R.string.resume_playback),
    "1h" to stringResource(R.string.resume_1h),
    "6h" to stringResource(R.string.resume_6h),
    "12h" to stringResource(R.string.resume_12h),
    "24h" to stringResource(R.string.resume_24h),
    "3d" to stringResource(R.string.resume_3d),
    "7d" to stringResource(R.string.resume_7d),
    "expiry" to stringResource(R.string.resume_expiry),
  )

  SettingsSwitchRow(
    icon = "IR",
    iconColor = Color(0xFF0284C7),
    title = stringResource(R.string.resume_enable),
    subtitle = stringResource(R.string.resume_video_description),
    checked = videoResumeEnabled,
    onCheckedChange = { checked ->
      if (prefs.edit().putBoolean(videoKey, checked).commit()) {
        videoResumeEnabled = checked
        if (!checked) {
          FastResumeCache.clear(context)
        }
      }
    }
  )

  SettingsDivider()

  SettingsChoiceRow(
    icon = "URL",
    iconColor = Color(0xFF38BDF8),
    title = stringResource(R.string.resume_retention),
    subtitle = stringResource(R.string.resume_retention_description),
    options = retentionOptions,
    selected = retentionMode,
    optionLabel = { retentionLabels[it] ?: it },
    onSelected = { next ->
      if (prefs.edit().putString(retentionKey, next).commit()) {
        retentionMode = next
        if (next == "playback") {
          RetainedStreamStore(context).clearAll()
        }
      }
    }
  )

  SettingsDivider()

  SettingsNavRow(
    icon = "CLR",
    iconColor = Color(0xFFEF4444),
    title = stringResource(R.string.resume_clear_video),
    subtitle = stringResource(R.string.resume_video_description),
    onClick = {
      FastResumeCache.clear(context)
    }
  )

  SettingsDivider()

  SettingsNavRow(
    icon = "DEL",
    iconColor = Color(0xFFF97316),
    title = stringResource(R.string.resume_clear_links),
    subtitle = stringResource(R.string.resume_retention_description),
    onClick = {
      RetainedStreamStore(context).clearAll()
    }
  )
}
