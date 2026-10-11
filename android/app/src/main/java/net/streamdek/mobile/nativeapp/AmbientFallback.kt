package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max

/**
 * The ambient glow drawn behind a title that has no artwork of its own to blur.
 *
 * The long-press preview normally blurs the card's backdrop or poster across the whole window. A
 * card with neither (a channel, a plugin result, a catalogue entry without images) used to fall
 * back to bare black, which read as something having failed to load. This paints the same kind of
 * soft, out-of-focus wash from colour alone: a few broad glows in the theme's accent and in a hue
 * derived from the title, so each title keeps the same colours every time it is opened and two
 * neighbouring titles do not look identical.
 *
 * Radial gradients rather than a blur: they are already soft, cost one draw each, and look the same
 * on every Android version (the blur modifier only blurs on Android 12 and later).
 */
@Composable
internal fun GenericAmbientBackground(seed: String, modifier: Modifier = Modifier) {
  val accent = LocalStreamDekThemeColors.current.accent
  val palette = remember(seed, accent) { ambientPalette(seed, accent) }
  Canvas(modifier = modifier) {
    drawRect(palette.ground)
    val reach = max(size.width, size.height)
    palette.glows.forEach { glow ->
      val center = Offset(size.width * glow.x, size.height * glow.y)
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(glow.color.copy(alpha = glow.alpha), glow.color.copy(alpha = 0f)),
          center = center,
          radius = reach * glow.radius,
        ),
        radius = reach * glow.radius,
        center = center,
      )
    }
  }
}

private data class AmbientGlow(val color: Color, val x: Float, val y: Float, val radius: Float, val alpha: Float)

private data class AmbientPalette(val ground: Color, val glows: List<AmbientGlow>)

private fun ambientPalette(seed: String, accent: Color): AmbientPalette {
  val hash = abs(seed.trim().lowercase().hashCode())
  // A hue of the title's own, kept rich but not neon: a backdrop, not a highlight.
  val hue = (hash % 360).toFloat()
  val titleColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.78f)))
  val companion = Color(android.graphics.Color.HSVToColor(floatArrayOf((hue + 48f) % 360f, 0.55f, 0.58f)))
  // Pull the accent toward the title's hue a little, so the theme is present without dominating.
  val tintedAccent = lerp(accent, titleColor, 0.35f)
  // Where the glows sit shifts with the title too, within a layout that always leaves the lower
  // half - where the actions are read - darkest.
  val shift = ((hash / 360) % 100) / 100f
  return AmbientPalette(
    ground = Color(0xFF07080B),
    glows = listOf(
      AmbientGlow(titleColor, x = 0.20f + shift * 0.25f, y = 0.18f, radius = 0.62f, alpha = 0.55f),
      AmbientGlow(tintedAccent, x = 0.86f - shift * 0.2f, y = 0.34f, radius = 0.55f, alpha = 0.42f),
      AmbientGlow(companion, x = 0.45f, y = 0.72f, radius = 0.70f, alpha = 0.30f),
    ),
  )
}
