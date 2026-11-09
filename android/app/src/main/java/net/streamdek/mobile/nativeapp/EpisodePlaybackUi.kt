package net.streamdek.mobile.nativeapp

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import net.streamdek.mobile.R

enum class EpisodePlaybackKind {
  Play,
  PlayDownloaded,
  Resume,
  ResumeDownloaded,
  PlayAgain;
}

data class EpisodePlaybackSession(
  val kind: EpisodePlaybackKind,
  val onPlay: () -> Unit,
  val onRestart: (() -> Unit)? = null,
  val isDownloaded: Boolean = (kind == EpisodePlaybackKind.PlayDownloaded || kind == EpisodePlaybackKind.ResumeDownloaded),
)

enum class PreviousEpisodesWatchedStatus {
  Available,
  AllWatched,
  None;
}

@Composable
fun rememberCompactEpisodeControls(): Boolean {
  val configuration = LocalConfiguration.current
  return configuration.screenWidthDp < 400 || configuration.fontScale > 1.15f
}

private fun textContrastOn(color: Color): Color {
  return if (color.luminance() > 0.45f) Color(0xFF111827) else Color.White
}

@Composable
fun EpisodePlaybackPill(
  playback: EpisodePlaybackSession,
  foreground: Color,
  modifier: Modifier = Modifier,
) {
  val compact = rememberCompactEpisodeControls()
  val haptics = LocalHapticFeedback.current

  val primaryLabel = when (playback.kind) {
    EpisodePlaybackKind.Play -> if (compact) stringResource(R.string.action_play) else stringResource(R.string.action_play_episode)
    EpisodePlaybackKind.PlayDownloaded -> stringResource(R.string.action_play_from_downloads)
    EpisodePlaybackKind.Resume -> if (compact) stringResource(R.string.action_resume) else stringResource(R.string.action_resume_episode)
    EpisodePlaybackKind.ResumeDownloaded -> stringResource(R.string.action_resume_from_downloads)
    EpisodePlaybackKind.PlayAgain -> stringResource(R.string.action_play_again)
  }

  val containerColor = foreground.copy(alpha = 0.14f)
  val icon = if (playback.isDownloaded) Icons.Rounded.Download else Icons.Rounded.PlayArrow

  Row(
    modifier = modifier
      .height(44.dp)
      .widthIn(max = 340.dp)
      .clip(StreamDekRadius.pill)
      .background(containerColor),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier
        .weight(1f)
        .clickable(
          role = Role.Button,
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            playback.onPlay()
          },
        )
        .padding(start = 14.dp, end = if (playback.onRestart != null) 12.dp else 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = foreground,
      )
      Spacer(modifier = Modifier.width(7.dp))
      Text(
        primaryLabel,
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (playback.onRestart != null) {
      Box(
        modifier = Modifier
          .width(1.dp)
          .height(20.dp)
          .background(foreground.copy(alpha = 0.24f)),
      )
      Box(
        modifier = Modifier
          .size(44.dp)
          .clickable(
            role = Role.Button,
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              playback.onRestart.invoke()
            },
          )
          .semantics { contentDescription = "Restart" },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Rounded.Replay,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = foreground.copy(alpha = 0.82f),
        )
      }
    }
  }
}

private val SliderWatchedGreen = Color(0xFF22C55E)

@Composable
fun EpisodeWatchedSlider(
  episodeKey: String,
  watched: Boolean,
  previous: PreviousEpisodesWatchedStatus,
  foreground: Color,
  accent: Color,
  onToggleWatched: () -> Unit,
  onMarkPreviousWatched: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = LocalHapticFeedback.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val canMarkPrevious = previous == PreviousEpisodesWatchedStatus.Available
  val rightLabel = if (watched) stringResource(R.string.slider_mark_episode_unwatched) else stringResource(R.string.slider_mark_episode_watched)
  val leftLabel = when (previous) {
    PreviousEpisodesWatchedStatus.Available -> stringResource(R.string.slider_mark_previous_watched)
    PreviousEpisodesWatchedStatus.AllWatched -> stringResource(R.string.slider_previous_all_watched)
    PreviousEpisodesWatchedStatus.None -> stringResource(R.string.slider_no_previous_episodes)
  }

  val thumbOffset = remember(episodeKey) { Animatable(0f) }
  var isArmed by remember(episodeKey) { mutableStateOf(false) }
  var isCommitting by remember(episodeKey) { mutableStateOf(false) }

  val targetThumbColor = if (watched) SliderWatchedGreen else foreground.copy(alpha = 0.55f)
  val thumbColor by animateColorAsState(targetThumbColor, label = "thumbColor")

  val accessibilityText = stringResource(R.string.a11y_watched_slider)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .height(52.dp)
      .clip(StreamDekRadius.pill)
      .background(foreground.copy(alpha = 0.07f))
      .semantics { contentDescription = accessibilityText },
    contentAlignment = Alignment.Center,
  ) {
    val totalWidth = constraints.maxWidth.toFloat()
    val thumbWidthPx = with(density) { 44.dp.toPx() }
    val maxDragPx = (totalWidth - thumbWidthPx) / 2f - with(density) { 4.dp.toPx() }

    // Left action track (Mark previous)
    Row(
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        Icons.Rounded.History,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = if (canMarkPrevious) foreground.copy(alpha = 0.86f) else foreground.copy(alpha = 0.38f),
      )
      Text(
        leftLabel,
        color = if (canMarkPrevious) foreground.copy(alpha = 0.86f) else foreground.copy(alpha = 0.38f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }

    // Right action track (Mark episode)
    Row(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(end = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        rightLabel,
        color = foreground.copy(alpha = 0.86f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
      Icon(
        Icons.Rounded.Check,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = foreground.copy(alpha = 0.86f),
      )
    }

    // Centered draggable thumb
    Box(
      modifier = Modifier
        .offset { IntOffset(thumbOffset.value.roundToInt(), 0) }
        .size(44.dp)
        .clip(StreamDekRadius.pill)
        .background(if (isArmed) accent else thumbColor)
        .pointerInput(episodeKey, canMarkPrevious) {
          detectHorizontalDragGestures(
            onDragStart = {
              isArmed = false
              isCommitting = false
            },
            onDragEnd = {
              val currentOffset = thumbOffset.value
              val progress = abs(currentOffset) / maxDragPx
              if (progress >= 0.8f && !isCommitting) {
                isCommitting = true
                scope.launch {
                  if (currentOffset > 0) {
                    onToggleWatched()
                  } else if (currentOffset < 0 && canMarkPrevious) {
                    onMarkPreviousWatched()
                  }
                  thumbOffset.animateTo(0f, spring())
                  isArmed = false
                  isCommitting = false
                }
              } else {
                scope.launch {
                  thumbOffset.animateTo(0f, spring())
                  isArmed = false
                }
              }
            },
            onDragCancel = {
              scope.launch {
                thumbOffset.animateTo(0f, spring())
                isArmed = false
              }
            },
            onHorizontalDrag = { _, dragAmount ->
              val newOffset = (thumbOffset.value + dragAmount).let { candidate ->
                if (!canMarkPrevious && candidate < 0f) {
                  0f
                } else {
                  candidate.coerceIn(-maxDragPx, maxDragPx)
                }
              }
              val progress = abs(newOffset) / maxDragPx
              val nowArmed = progress >= 0.8f
              if (nowArmed != isArmed) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isArmed = nowArmed
              }
              scope.launch { thumbOffset.snapTo(newOffset) }
            },
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      val icon = if (watched) Icons.Rounded.CheckCircle else Icons.Rounded.Check
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = textContrastOn(if (isArmed) accent else thumbColor),
      )
    }
  }
}
