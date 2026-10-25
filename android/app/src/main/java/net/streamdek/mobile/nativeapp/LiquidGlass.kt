package net.streamdek.mobile.nativeapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * Authentic Liquid Glass styling inspired by Kyant0/AndroidLiquidGlass (Backdrop).
 *
 * Implements:
 * 1. 45-degree angle specular highlight bevel catching ambient light at the top-left edge.
 * 2. Lens curvature reflection sheen along the top edge simulating physical optical refraction.
 * 3. Dynamic accent caustic radial glow focused through the glass body.
 * 4. Dual-layer inset bevel for tangible glass depth and physical thickness.
 * 5. AGSL RuntimeShader highlight on Android 13+ (API 33+) with ultra-fast GPU fallback on all devices.
 * 6. Crystal clear transparency without milky frost or opaque white glare.
 */
object LiquidGlassTokens {
  const val SPECULAR_ANGLE_DEG = 45f
  const val SPECULAR_FALLOFF = 1.25f

  val defaultCardShape: Shape = StreamDekRadius.panelShape
  val defaultSheetShape: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
  val defaultDialogShape: Shape = StreamDekRadius.panelShape
  val defaultBadgeShape: Shape = StreamDekRadius.controlShape
  val defaultPillShape: Shape = StreamDekRadius.pill
}

/**
 * AGSL RuntimeShader cache for Android 13+ (API 33+).
 */
internal object LiquidGlassShaders {
  private const val HighlightShaderSource = """
    uniform float2 size;
    uniform float cornerRadius;
    layout(color) uniform half4 highlightColor;
    uniform float angle;
    uniform float falloff;

    float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
        float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
        float outside = length(max(cornerCoord, 0.0)) - radius;
        float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
        return outside + inside;
    }

    float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
        float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
        if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
            return sign(coord) * normalize(max(cornerCoord, 0.0));
        } else {
            float gradX = step(cornerCoord.y, cornerCoord.x);
            return sign(coord) * float2(gradX, 1.0 - gradX);
        }
    }

    half4 main(float2 coord) {
        float2 halfSize = size * 0.5;
        float2 centeredCoord = coord - halfSize;
        float gradRadius = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y));
        float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
        float2 normal = float2(cos(angle), sin(angle));
        float d = dot(grad, normal);
        float intensity = pow(max(abs(d), 0.0), falloff);
        return highlightColor * intensity;
    }
  """

  @Volatile
  private var cachedHighlightShader: android.graphics.RuntimeShader? = null
  private var shaderInitFailed = false

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  fun obtainHighlightShader(
    width: Float,
    height: Float,
    cornerRadius: Float,
    color: Color,
    angleDeg: Float = LiquidGlassTokens.SPECULAR_ANGLE_DEG,
    falloff: Float = LiquidGlassTokens.SPECULAR_FALLOFF,
  ): android.graphics.RuntimeShader? {
    if (shaderInitFailed) return null
    return try {
      var shader = cachedHighlightShader
      if (shader == null) {
        shader = android.graphics.RuntimeShader(HighlightShaderSource)
        cachedHighlightShader = shader
      }
      shader.setFloatUniform("size", width, height)
      shader.setFloatUniform("cornerRadius", cornerRadius.coerceAtLeast(1f))
      shader.setColorUniform("highlightColor", color.toArgb())
      shader.setFloatUniform("angle", (angleDeg * (PI / 180.0)).toFloat())
      shader.setFloatUniform("falloff", falloff)
      shader
    } catch (_: Throwable) {
      shaderInitFailed = true
      null
    }
  }
}

/**
 * Creates a 45-degree angle specular bevel border brush catching ambient light.
 */
fun liquidSpecularBorderBrush(
  accentColor: Color,
  lightSurface: Boolean,
  alphaMultiplier: Float = 1.0f,
): Brush {
  val topWhiteAlpha = (if (lightSurface) 0.52f else 0.40f) * alphaMultiplier
  val topAccentAlpha = (if (lightSurface) 0.36f else 0.28f) * alphaMultiplier
  val midAccentAlpha = (if (lightSurface) 0.16f else 0.12f) * alphaMultiplier
  val midSurfaceAlpha = (if (lightSurface) 0.08f else 0.05f) * alphaMultiplier
  val shadowAlpha = (if (lightSurface) 0.06f else 0.22f) * alphaMultiplier

  return Brush.linearGradient(
    colors = listOf(
      Color.White.copy(alpha = topWhiteAlpha),
      accentColor.copy(alpha = topAccentAlpha),
      accentColor.copy(alpha = midAccentAlpha),
      (if (lightSurface) Color(0xFF6B7280) else Color(0xFF94A3B8)).copy(alpha = midSurfaceAlpha),
      Color.Black.copy(alpha = shadowAlpha),
    ),
    start = Offset(0f, 0f),
    end = Offset.Infinite,
  )
}

/**
 * Applies the authentic Kyant0-inspired Liquid Glass layers to a composable.
 */
fun Modifier.liquidGlassSurface(
  shape: Shape,
  accentColor: Color,
  lightSurface: Boolean,
  accentTinted: Boolean = true,
  baseAlpha: Float = if (lightSurface) 0.65f else 0.45f,
  borderWidth: Dp = 1.dp,
  showLensSheen: Boolean = true,
  showCausticGlow: Boolean = true,
  cornerRadiusPx: Float = 52f,
): Modifier {
  val baseGroundColor = if (lightSurface) Color(0xFFF7F8FA) else Color(0xFF0C0E14)
  val specularBorder = liquidSpecularBorderBrush(accentColor, lightSurface)

  return this
    .clip(shape)
    .background(baseGroundColor.copy(alpha = baseAlpha))
    .then(
      if (accentTinted) {
        Modifier.background(accentColor.copy(alpha = if (lightSurface) 0.035f else 0.045f))
      } else Modifier
    )
    .then(
      if (showCausticGlow) {
        Modifier.drawBehind {
          val maxDim = maxOf(size.width, size.height)
          if (maxDim > 0f) {
            drawRect(
              brush = Brush.radialGradient(
                colors = listOf(
                  accentColor.copy(alpha = if (lightSurface) 0.07f else 0.08f),
                  accentColor.copy(alpha = if (lightSurface) 0.02f else 0.025f),
                  Color.Transparent,
                ),
                center = Offset(size.width * 0.15f, size.height * 0.08f),
                radius = maxDim * 0.85f,
              ),
            )
          }
        }
      } else Modifier
    )
    .then(
      if (showLensSheen) {
        Modifier.drawBehind {
          val sheenHeight = minOf(size.height * 0.38f, 48.dp.toPx())
          if (sheenHeight > 0f) {
            drawRect(
              brush = Brush.verticalGradient(
                colors = listOf(
                  Color.White.copy(alpha = if (lightSurface) 0.20f else 0.14f),
                  accentColor.copy(alpha = if (lightSurface) 0.10f else 0.07f),
                  Color.Transparent,
                ),
                startY = 0f,
                endY = sheenHeight,
              ),
              size = Size(size.width, sheenHeight),
            )
          }
        }
      } else Modifier
    )
    // Physical inner bevel stroke for glass depth
    .padding(0.65.dp)
    .border(
      width = 0.65.dp,
      brush = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = if (lightSurface) 0.22f else 0.12f),
          Color.Transparent,
          Color.Black.copy(alpha = if (lightSurface) 0.02f else 0.08f),
        ),
        start = Offset(0f, 0f),
        end = Offset.Infinite,
      ),
      shape = shape,
    )
    // Primary 45-degree specular bevel border
    .border(borderWidth, specularBorder, shape)
    .then(
      // Optional AGSL hardware specular highlight enhancement on Android 13+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier.drawWithContent {
          drawContent()
          val agslShader = LiquidGlassShaders.obtainHighlightShader(
            width = size.width,
            height = size.height,
            cornerRadius = cornerRadiusPx,
            color = Color.White.copy(alpha = if (lightSurface) 0.38f else 0.28f),
          )
          if (agslShader != null) {
            val paint = android.graphics.Paint().apply {
              shader = agslShader
              style = android.graphics.Paint.Style.STROKE
              strokeWidth = 1.dp.toPx()
              isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawRoundRect(
              0f,
              0f,
              size.width,
              size.height,
              cornerRadiusPx,
              cornerRadiusPx,
              paint,
            )
          }
        }
      } else Modifier
    )
}
