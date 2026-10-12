package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.streamdek.mobile.nativeapp.mediaserver.EMBY_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.JELLYFIN_PROVIDER_ID
import net.streamdek.mobile.nativeapp.mediaserver.PLEX_PROVIDER_ID

/**
 * The navigation mark for "My Media" when more than one kind of media server is switched on: the
 * marks of exactly those servers, so the bar says which ones are behind the tab.
 *
 * Two marks overlap like a pair of avatars, the first behind at reduced strength and the second in
 * front. Three sit in a small triangle - two above, one below - each a little smaller and none
 * overlapping, since three overlapping single-colour marks at icon size become one shape. Every mark
 * is single-colour, so the whole thing takes the bar's tint and selection treatment like any other
 * destination; the difference in strength is what keeps overlapping marks legible in one colour.
 *
 * Built once per combination and kept.
 */
internal object MediaServerStackMark {
  private val cache = java.util.concurrent.ConcurrentHashMap<String, ImageVector>()

  /** The mark for [providers], in the order given (the first sits furthest back). */
  fun forProviders(providers: List<String>): ImageVector {
    val known = providers.filter { it in MARKS }.distinct()
    return cache.getOrPut(known.joinToString("+")) { build(known) }
  }

  /** A mark's geometry: its paths and the part of its own canvas it actually occupies. */
  private class Mark(
    val paths: List<Pair<String, PathFillType>>,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
  )

  private val MARKS: Map<String, Mark> = mapOf(
    PLEX_PROVIDER_ID to Mark(listOf(JellyfinIcons.PLEX_CHEVRON to PathFillType.NonZero), 5.6f, 3.2f, 13.45f, 17.6f),
    JELLYFIN_PROVIDER_ID to Mark(
      listOf(JellyfinIcons.INNER to PathFillType.NonZero, JellyfinIcons.OUTER to PathFillType.EvenOdd),
      0f, 0f, 72f, 71f,
    ),
    EMBY_PROVIDER_ID to Mark(listOf(EmbyIcons.SHAPE to PathFillType.EvenOdd), 0f, 0f, 512f, 512f),
  )

  /** Where each mark goes on the 24-unit canvas: a square box (x, y, size) and its strength. */
  private data class Slot(val x: Float, val y: Float, val size: Float, val alpha: Float)

  private fun slotsFor(count: Int): List<Slot> = when (count) {
    // Overlapping pair: back mark up and to the left, front mark down and to the right.
    2 -> listOf(
      Slot(x = 0.5f, y = 2.5f, size = 14f, alpha = 0.5f),
      Slot(x = 9.5f, y = 7f, size = 14.5f, alpha = 1f),
    )
    // Triangle: two above, one centred below, each 11 units with a unit's gap between them.
    3 -> listOf(
      Slot(x = 0.5f, y = 0.5f, size = 11f, alpha = 0.72f),
      Slot(x = 12.5f, y = 0.5f, size = 11f, alpha = 0.72f),
      Slot(x = 6.5f, y = 12.5f, size = 11f, alpha = 1f),
    )
    else -> listOf(Slot(x = 0f, y = 0f, size = 24f, alpha = 1f))
  }

  private fun build(providers: List<String>): ImageVector {
    val builder = ImageVector.Builder(
      name = "MediaServers_" + providers.joinToString("_"),
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    providers.zip(slotsFor(providers.size)).forEach { (provider, slot) ->
      val mark = MARKS.getValue(provider)
      // Fit the mark's own extent into the slot, keeping its proportions and centring it.
      val scale = slot.size / maxOf(mark.width, mark.height)
      val tx = slot.x + (slot.size - mark.width * scale) / 2f - mark.left * scale
      val ty = slot.y + (slot.size - mark.height * scale) / 2f - mark.top * scale
      builder.addGroup(name = provider, scaleX = scale, scaleY = scale, translationX = tx, translationY = ty)
      mark.paths.forEach { (data, fillType) ->
        builder.addPath(addPathNodes(data), pathFillType = fillType, fill = SolidColor(Color.White), fillAlpha = slot.alpha)
      }
      builder.clearGroup()
    }
    return builder.build()
  }
}
