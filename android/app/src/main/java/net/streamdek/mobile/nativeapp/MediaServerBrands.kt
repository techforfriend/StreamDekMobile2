package net.streamdek.mobile.nativeapp

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.streamdek.mobile.R
import net.streamdek.mobile.nativeapp.mediaserver.EMBY_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID

/**
 * How each personal media server looks wherever the app names it - its name, accent, mark, logo and
 * the few sentences that name it - so screens ask for "this provider's" and never branch on which
 * provider it is. A new provider is one more entry here.
 */
internal data class MediaServerBrand(
    val provider: String,
    @StringRes val name: Int,
    @StringRes val sectionLabel: Int,
    val accent: Color,
    val mark: ImageVector,
    /** A full-colour logo the app may show, or null to draw [mark] in [accent]. */
    @DrawableRes val logo: Int?,
    @StringRes val searchBadge: Int,
    @StringRes val pageOfflineTitle: Int,
    @StringRes val pageEmptyNote: Int,
    @StringRes val pageServerRefused: Int,
    @StringRes val removeServerBody: Int,
)

/** Jellyfin's purple, from its logo. */
internal val JellyfinBrandPurple = Color(0xFFAA5CC3)

/** Emby's green, from its logo. */
internal val EmbyBrandGreen = Color(0xFF52B54B)

internal fun mediaServerBrand(provider: String?): MediaServerBrand = when (provider) {
    JELLYFIN_PROVIDER_ID -> MediaServerBrand(
        provider = JELLYFIN_PROVIDER_ID,
        name = R.string.media_server_jellyfin,
        sectionLabel = R.string.media_server_section_jellyfin,
        accent = JellyfinBrandPurple,
        mark = JellyfinIcons.Mark,
        logo = R.drawable.jellyfin_logo,
        searchBadge = R.string.jellyfin_search_badge,
        pageOfflineTitle = R.string.jellyfin_page_offline_title,
        pageEmptyNote = R.string.jellyfin_page_empty_note,
        pageServerRefused = R.string.jellyfin_page_server_refused,
        removeServerBody = R.string.jellyfin_remove_server_body,
    )
    EMBY_PROVIDER_ID -> MediaServerBrand(
        provider = EMBY_PROVIDER_ID,
        name = R.string.media_server_emby,
        sectionLabel = R.string.media_server_section_emby,
        accent = EmbyBrandGreen,
        mark = EmbyIcons.Mark,
        logo = R.drawable.emby_logo,
        searchBadge = R.string.emby_search_badge,
        pageOfflineTitle = R.string.emby_page_offline_title,
        pageEmptyNote = R.string.emby_page_empty_note,
        pageServerRefused = R.string.emby_page_server_refused,
        removeServerBody = R.string.emby_remove_server_body,
    )
    else -> MediaServerBrand(
        provider = net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID,
        name = R.string.media_server_plex,
        sectionLabel = R.string.media_server_section_plex,
        accent = PlexGold,
        mark = PlexIcons.Chevron,
        logo = R.drawable.plex_logo,
        searchBadge = R.string.plex_search_badge,
        pageOfflineTitle = R.string.plex_page_offline_title,
        pageEmptyNote = R.string.plex_page_empty_note,
        pageServerRefused = R.string.plex_page_server_refused,
        removeServerBody = R.string.media_server_remove_server_body,
    )
}

/** A provider's logo at [size]: its full-colour logo where it has one the app may show, its mark in its accent otherwise. */
@Composable
internal fun MediaServerLogo(provider: String?, size: Dp, contentDescription: String? = null, modifier: Modifier = Modifier) {
    val brand = mediaServerBrand(provider)
    val logo = brand.logo
    if (logo != null) {
        Image(painterResource(logo), contentDescription = contentDescription, modifier = modifier.size(size))
    } else {
        Icon(brand.mark, contentDescription = contentDescription, tint = brand.accent, modifier = modifier.size(size))
    }
}

/** The provider's colour wash behind its page and lists. */
@Composable
internal fun Modifier.mediaServerAmbientGlow(provider: String?): Modifier = when (provider) {
    JELLYFIN_PROVIDER_ID -> jellyfinAmbientGlow()
    EMBY_PROVIDER_ID -> embyAmbientGlow()
    else -> plexAmbientGlow()
}

/**
 * Emby's colour wash: an aurora. Emby's green rises in one corner and turns through aqua into a
 * soft violet, with a mint glow underneath - the colours of northern lights, which keeps the page
 * unmistakably Emby's (green leads) while being bright and clean rather than muddy. Drifts as Plex
 * and Jellyfin do; quieter on a light theme, and still with reduced motion.
 *
 * Every colour here is bright and saturated on purpose. Dark, desaturated tones at low opacity over
 * near-black are exactly where 8-bit gradients band into visible rings, which is what made the
 * previous slate wash look coarser than the other two. Each glow also fades along an eased curve
 * rather than in a straight line, so there is no edge where it meets the dark.
 */
@Composable
internal fun Modifier.embyAmbientGlow(): Modifier {
    val motionless = LocalMotionSettings.current.motionless
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strength = if (dark) 0.34f else 0.16f
    val drift: State<Float> = if (motionless) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "embyAmbient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "embyAmbientDrift",
        )
    }
    return drawBehind {
        if (dark) drawRect(Color.Black.copy(alpha = 0.55f))
        val d = drift.value
        val w = size.width
        val h = size.height
        val radius = maxOf(w, h) * 0.55f
        fun glow(color: Color, x: Float, y: Float, scale: Float = 1f, weight: Float = 1f) {
            val center = Offset(x * w, y * h)
            val a = strength * weight
            drawCircle(
                // An eased fall-off (close to smoothstep) instead of a straight line to transparent.
                brush = Brush.radialGradient(
                    0.00f to color.copy(alpha = a),
                    0.30f to color.copy(alpha = a * 0.78f),
                    0.55f to color.copy(alpha = a * 0.42f),
                    0.78f to color.copy(alpha = a * 0.14f),
                    1.00f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius * scale,
                ),
                radius = radius * scale,
                center = center,
            )
        }
        glow(EmbyAmbientGreen, 0.12f + 0.10f * d, 0.08f + 0.05f * d, 1.08f)
        glow(EmbyAmbientAqua, 0.90f - 0.08f * d, 0.18f + 0.08f * d, 1f, 0.85f)
        glow(EmbyAmbientViolet, 0.22f + 0.06f * d, 0.64f - 0.07f * d, 0.95f, 0.8f)
        glow(EmbyAmbientMint, 0.84f - 0.10f * d, 0.84f - 0.05f * d, 1f, 0.75f)
    }
}

/** Emby's own green, which leads. */
private val EmbyAmbientGreen = Color(0xFF52B54B)
private val EmbyAmbientAqua = Color(0xFF22D3EE)
private val EmbyAmbientViolet = Color(0xFFA78BFA)
private val EmbyAmbientMint = Color(0xFF6EE7B7)

/**
 * Emby's logo as a single-colour mark, the play symbol cut out of it, so it takes the same tint and
 * focus treatment as the other destination marks. The full-colour logo is R.drawable.emby_logo.
 */
internal object EmbyIcons {
    internal const val SHAPE = "M97.1,132.4l26.5,26.5L0,282.5l132.4,132.4l26.5,-26.5L282.5,512l141.2,-141.2l-26.5,-26.5L512,229.5L379.6,97.1l-26.5,26.5L229.5,0z" +
        "M196.8,351.2V158.2L366,254.7L281.4,303z"

    val Mark: ImageVector by lazy {
        ImageVector.Builder(name = "EmbyMark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 512f, viewportHeight = 512f)
            .addPath(addPathNodes(SHAPE), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.White))
            .build()
    }
}
