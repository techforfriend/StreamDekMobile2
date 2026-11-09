package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.streamdek.mobile.R

enum class FuseCategoryKind {
  Sports,
  News,
  Movies,
  Entertainment,
  Kids,
  Music,
  Documentary,
  Religion,
  Regional,
  Custom,
  Other;
}

data class FuseEntryRef(
  val scope: String,
  val identity: String,
)

data class FuseCategoryGroup(
  val id: String,
  val label: String,
  val kind: FuseCategoryKind,
  val refs: List<FuseEntryRef> = emptyList(),
  val rawNames: List<String> = emptyList(),
  val alternates: Map<FuseEntryRef, List<FuseEntryRef>> = emptyMap(),
)

data class FuseBrowseModel(
  val identity: Any,
  val live: Boolean,
  val groups: List<FuseCategoryGroup>,
  val samples: Map<String, List<MediaItem>>,
  val seriesGroups: Set<String>,
)

@Composable
fun fuseCategoryLabel(group: FuseCategoryGroup): String {
  return when (group.kind) {
    FuseCategoryKind.Sports -> stringResource(R.string.fuse_category_sports)
    FuseCategoryKind.News -> stringResource(R.string.fuse_category_news)
    FuseCategoryKind.Movies -> stringResource(R.string.fuse_category_movies)
    FuseCategoryKind.Entertainment -> stringResource(R.string.fuse_category_entertainment)
    FuseCategoryKind.Kids -> stringResource(R.string.fuse_category_kids)
    FuseCategoryKind.Music -> stringResource(R.string.fuse_category_music)
    FuseCategoryKind.Documentary -> stringResource(R.string.fuse_category_documentary)
    FuseCategoryKind.Religion -> stringResource(R.string.fuse_category_religion)
    FuseCategoryKind.Other -> stringResource(R.string.fuse_category_other)
    FuseCategoryKind.Regional,
    FuseCategoryKind.Custom -> group.label
  }
}

@Composable
fun FuseRecentChannels(
  channels: List<MediaItem>,
  liveGrid: Boolean,
  onOpen: (MediaItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (channels.isEmpty()) return
  val ink = MaterialTheme.colorScheme.onBackground

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Rounded.History,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = ink.copy(alpha = 0.70f),
      )
      Text(
        stringResource(R.string.fuse_recently_watched),
        color = ink,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(channels, key = { it.id }) { channel ->
        Row(
          modifier = Modifier
            .height(44.dp)
            .clip(StreamDekRadius.pill)
            .background(ink.copy(alpha = 0.08f))
            .clickable { onOpen(channel) }
            .padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            Icons.Rounded.LiveTv,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ink.copy(alpha = 0.75f),
          )
          Text(
            channel.title,
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
fun FuseSourcePicker(
  title: String,
  sources: List<Pair<String, String>>,
  activeSourceKey: String?,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val ink = MaterialTheme.colorScheme.onSurface
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      stringResource(R.string.fuse_choose_source),
      color = ink.copy(alpha = 0.62f),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      title,
      color = ink,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Black,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      sources.forEach { (key, label) ->
        val selected = key == activeSourceKey
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(StreamDekRadius.cardShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else ink.copy(alpha = 0.06f))
            .clickable {
              onSelect(key)
              onDismiss()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
          )
          if (selected) {
            Text(
              stringResource(R.string.fuse_last_used),
              color = MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

object FuseCategorizer {
  private val sportsRegex = Regex("(?i)sports?|football|soccer|nba|nfl|nhl|mlb|cricket|f1|formula 1|motorsports?|racing|fight|ufc|boxing|wwe|wrestling|golf|tennis|rugby|espn|bein|dazn|ppv")
  private val newsRegex = Regex("(?i)news|weather|business news|cnn|bbc news|sky news|al jazeera|aljazeera|euronews|france 24|cnbc|bloomberg")
  private val kidsRegex = Regex("(?i)kids?|children|childrens|cartoons?|family|junior|toons?|nick|nickelodeon|disney junior|cbeebies|baby")
  private val musicRegex = Regex("(?i)music|musique|hits|mtv|radio|songs?|vh1")
  private val moviesRegex = Regex("(?i)movies?|films?|cinema|cine|hbo|cinemax|pelicula|peliculas")
  private val docRegex = Regex("(?i)documentar(y|ies)|docs?|docu|science|nature|history|knowledge|discovery|national geographic|nat geo|travel|education(al)?|infotainment|lifestyle")
  private val religionRegex = Regex("(?i)religio(n|us)|faith|christian|gospel|islamic|quran|church|spiritual|devotional")
  private val entertainmentRegex = Regex("(?i)entertainment|general|variety|comedy|drama|series|reality|shows?|fashion")

  fun classify(name: String): FuseCategoryKind {
    return when {
      sportsRegex.containsMatchIn(name) -> FuseCategoryKind.Sports
      newsRegex.containsMatchIn(name) -> FuseCategoryKind.News
      moviesRegex.containsMatchIn(name) -> FuseCategoryKind.Movies
      kidsRegex.containsMatchIn(name) -> FuseCategoryKind.Kids
      musicRegex.containsMatchIn(name) -> FuseCategoryKind.Music
      docRegex.containsMatchIn(name) -> FuseCategoryKind.Documentary
      religionRegex.containsMatchIn(name) -> FuseCategoryKind.Religion
      entertainmentRegex.containsMatchIn(name) -> FuseCategoryKind.Entertainment
      else -> FuseCategoryKind.Other
    }
  }
}
