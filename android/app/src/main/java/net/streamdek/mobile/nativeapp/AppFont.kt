package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.streamdek.mobile.R

/*
 * The App Font setting and the type the whole app is set in.
 *
 * Two StreamDek families ship in res/font, each in its six real weights (Light to ExtraBold), so a
 * SemiBold title is the SemiBold the family was drawn with rather than a Regular the renderer has
 * thickened. The third choice is the phone's own font.
 *
 * The choice reaches every screen through MaterialTheme.typography: the app sets almost all of its
 * text from those styles, and a Text with only a size or weight of its own still takes its family
 * from the theme's text style. Only the family changes between choices; sizes, line heights and
 * weights stay the theme's, so the hierarchy reads the same in all three. Glyphs a StreamDek family
 * does not have (other scripts, emoji) fall back to the system font character by character.
 *
 * Shared with the TV app, which reads the same setting from the same place under the same keys.
 */

/** Persisted under this key, on the device and in the account's `app` settings as `appFont`. */
internal const val APP_FONT_PREFERENCE = "app_font"

enum class AppFont(
  /** The stored form. Stable across releases; the enum name and the label are not the storage contract. */
  val key: String,
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
) {
  System("system", R.string.app_font_system, R.string.app_font_system_description),
  StreamDekSans("streamdek_sans", R.string.app_font_streamdek_sans, R.string.app_font_streamdek_sans_description),
  StreamDekSansRounded("streamdek_sans_rounded", R.string.app_font_streamdek_sans_rounded, R.string.app_font_streamdek_sans_rounded_description);

  /** The family this choice sets text in. Built once per family and shared. */
  val fontFamily: FontFamily get() = when (this) {
    StreamDekSans -> StreamDekFonts.sans
    StreamDekSansRounded -> StreamDekFonts.sansRounded
    System -> FontFamily.Default
  }

  companion object {
    /**
     * Everyone who has not chosen, new or existing, keeps the device's own font, as the app has
     * always looked. The StreamDek faces are there to opt into.
     */
    val Default = System

    /** Anything missing, misspelt or from a newer build falls back to [Default]. */
    fun fromKey(key: String?): AppFont {
      val normalized = key?.trim()?.lowercase().orEmpty()
      return entries.firstOrNull { it.key == normalized } ?: Default
    }
  }
}

/** The two bundled families, created once for the life of the process. */
internal object StreamDekFonts {
  val sans: FontFamily = FontFamily(
    Font(R.font.streamdek_sans_light, FontWeight.Light),
    Font(R.font.streamdek_sans_regular, FontWeight.Normal),
    Font(R.font.streamdek_sans_medium, FontWeight.Medium),
    Font(R.font.streamdek_sans_semibold, FontWeight.SemiBold),
    Font(R.font.streamdek_sans_bold, FontWeight.Bold),
    Font(R.font.streamdek_sans_extrabold, FontWeight.ExtraBold),
  )

  val sansRounded: FontFamily = FontFamily(
    Font(R.font.streamdek_sans_rounded_light, FontWeight.Light),
    Font(R.font.streamdek_sans_rounded_regular, FontWeight.Normal),
    Font(R.font.streamdek_sans_rounded_medium, FontWeight.Medium),
    Font(R.font.streamdek_sans_rounded_semibold, FontWeight.SemiBold),
    Font(R.font.streamdek_sans_rounded_bold, FontWeight.Bold),
    Font(R.font.streamdek_sans_rounded_extrabold, FontWeight.ExtraBold),
  )
}

/**
 * The type scale for [font]: Material's scale, which the app's layouts are built against, in the
 * chosen family. One instance per font for the whole process, so switching back and forth hands
 * the theme the same object and nothing below it is rebuilt for an identical value.
 */
internal fun appTypography(font: AppFont): Typography = appTypographies.getValue(font)

private val appTypographies: Map<AppFont, Typography> by lazy {
  val base = Typography()
  AppFont.entries.associateWith { font -> if (font == AppFont.System) base else base.withFamily(font.fontFamily) }
}

private fun Typography.withFamily(family: FontFamily): Typography = Typography(
  displayLarge = displayLarge.copy(fontFamily = family),
  displayMedium = displayMedium.copy(fontFamily = family),
  displaySmall = displaySmall.copy(fontFamily = family),
  headlineLarge = headlineLarge.copy(fontFamily = family),
  headlineMedium = headlineMedium.copy(fontFamily = family),
  headlineSmall = headlineSmall.copy(fontFamily = family),
  titleLarge = titleLarge.copy(fontFamily = family),
  titleMedium = titleMedium.copy(fontFamily = family),
  titleSmall = titleSmall.copy(fontFamily = family),
  bodyLarge = bodyLarge.copy(fontFamily = family),
  bodyMedium = bodyMedium.copy(fontFamily = family),
  bodySmall = bodySmall.copy(fontFamily = family),
  labelLarge = labelLarge.copy(fontFamily = family),
  labelMedium = labelMedium.copy(fontFamily = family),
  labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * The App Font as this phone last stored it, for screens composed outside the main app's theme:
 * the version gate and the CloudStream plugin settings window.
 */
internal fun savedAppFont(context: android.content.Context): AppFont =
  AppFont.fromKey(context.getSharedPreferences(APP_SETTINGS_PREFERENCES, android.content.Context.MODE_PRIVATE).getString(APP_FONT_PREFERENCE, null))

/** For the rare style built from scratch rather than from the theme: the theme's family, nothing else. */
@Composable
internal fun appTextStyle(style: TextStyle): TextStyle =
  style.copy(fontFamily = style.fontFamily ?: MaterialTheme.typography.bodyLarge.fontFamily)

/**
 * The App Font picker in Settings → Appearance and Language → Typography: one card per font, each
 * set in its own family, so the viewer reads the choice rather than a description of it.
 */
@Composable
internal fun AppFontPicker(selected: AppFont, onSelected: (AppFont) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      SettingsIcon("FMT", LocalStreamDekThemeColors.current.accent)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.app_font), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.app_font_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      AppFont.entries.forEach { font ->
        AppFontCard(font = font, selected = font == selected, onClick = { onSelected(font) })
      }
    }
  }
}

@Composable
private fun AppFontCard(font: AppFont, selected: Boolean, onClick: () -> Unit) {
  val theme = LocalStreamDekThemeColors.current
  val family = font.fontFamily
  val onSurface = MaterialTheme.colorScheme.onSurface
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(StreamDekRadius.cardShape)
      .background(if (selected) theme.selectedContainer else onSurface.copy(alpha = 0.04f))
      .border(if (selected) 1.5.dp else 1.dp, if (selected) theme.selectedBorder else onSurface.copy(alpha = 0.08f), StreamDekRadius.cardShape)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(font.labelRes),
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        color = onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        stringResource(font.descriptionRes),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = family),
        color = onSurface.copy(alpha = 0.64f),
      )
      // The sample, in the font itself: every lower-case letter and every figure.
      Text(
        stringResource(R.string.app_font_preview),
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family),
        color = onSurface.copy(alpha = 0.9f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    Box(
      modifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(if (selected) theme.accent else onSurface.copy(alpha = 0.08f)),
      contentAlignment = Alignment.Center,
    ) {
      if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(15.dp))
    }
  }
}
