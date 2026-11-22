package app.aether.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aether.core.data.LocalPlaylist
import app.aether.core.data.Played
import app.aether.core.designsystem.component.GlassSegments
import app.aether.core.designsystem.component.Txt
import app.aether.core.designsystem.theme.Ink
import app.aether.core.designsystem.theme.Space
import app.aether.core.designsystem.theme.Type
import app.aether.core.model.Track
import app.aether.ui.LocalActions
import app.aether.ui.common.LocalContainer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY = 24L * 60 * 60 * 1000

/** What a song is, for telling copies apart: its title and first artist, lower case, brackets and marks dropped. */
private fun keyOf(t: Track): String {
    val title = t.title.lowercase().replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), "")
    val artist = (t.artists.firstOrNull()?.name ?: "").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
    return if (title.isEmpty()) t.id.value else "$title|$artist"
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Txt(label, Type.Aside, color = Ink.Grey, modifier = Modifier.weight(1f), maxLines = 1)
        Txt(value, Type.Aside, maxLines = 1)
    }
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(18.dp))
    Txt(text, Type.Aside)
    Spacer(Modifier.height(4.dp))
}

private fun minutesLabel(ms: Long): String {
    val m = ms / 60000
    return if (m >= 120) "${m / 60} h ${m % 60} min" else "$m min"
}

/**
 * The three tools over what you have played and kept, shown under History: Stats (what you listened to),
 * Smart (lists made from your listening, which you can play or keep as a playlist) and Duplicates
 * (songs that are in a playlist or in your likes more than once). All of it is worked out on this phone
 * from the library's own records. Plays are counted past their first half-minute.
 */
@Composable
internal fun InsightsPane(view: Int) {
    val container = LocalContainer.current
    val historyFlow = remember(container) { container.library.history(5000) }
    val history by historyFlow.collectAsStateWithLifecycle(emptyList())
    val liked by container.library.liked.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = 8.dp)) {
        when (view) {
            1 -> StatsView(history)
            2 -> SmartView(history, liked)
            else -> DuplicatesView(liked)
        }
    }
}

@Composable
private fun StatsView(history: List<Played>) {
    var period by rememberSaveable { mutableIntStateOf(0) }
    GlassSegments(listOf("7 days", "30 days", "All time"), period, { period = it })
    val now = System.currentTimeMillis()
    val plays = remember(history, period) {
        val cutoff = when (period) { 0 -> now - 7 * DAY; 1 -> now - 30 * DAY; else -> 0L }
        history.filter { it.at >= cutoff }
    }
    if (plays.isEmpty()) {
        Spacer(Modifier.height(18.dp))
        Txt("Nothing played in this time yet.", Type.Aside, color = Ink.Grey)
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val summary = remember(plays) {
        val ms = plays.sumOf { it.track.durationMs ?: 0L }
        val songs = plays.map { it.track.id.value }.toSet().size
        val days = plays.map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.toSet()
        val topSongs = plays.groupBy { it.track.id.value }.values.sortedByDescending { it.size }.take(5)
            .map { g -> g.first().track to g.size }
        val topArtists = plays.flatMap { p -> p.track.artists.map { it.name } }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(5)
        val hour = plays.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).hour }.eachCount().maxByOrNull { it.value }?.key
        val weekday = plays.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).dayOfWeek }.eachCount().maxByOrNull { it.value }?.key
        listOf(ms, songs, days.size, topSongs, topArtists, hour, weekday)
    }
    @Suppress("UNCHECKED_CAST")
    val topSongs = summary[3] as List<Pair<Track, Int>>
    @Suppress("UNCHECKED_CAST")
    val topArtists = summary[4] as List<Map.Entry<String, Int>>
    Spacer(Modifier.height(12.dp))
    Line("Plays", plays.size.toString())
    Line("Time listening", minutesLabel(summary[0] as Long))
    Line("Different songs", (summary[1] as Int).toString())
    Line("Days with music", (summary[2] as Int).toString())
    (summary[5] as Int?)?.let { h -> Line("Busiest hour", String.format("%02d:00", h)) }
    (summary[6] as java.time.DayOfWeek?)?.let { d -> Line("Busiest day", d.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())) }
    Heading("Most played songs")
    topSongs.forEachIndexed { i, (t, n) -> Line("${i + 1}.  ${t.title}  \u00B7  ${t.artistLine}", "$n") }
    Heading("Most played artists")
    topArtists.forEachIndexed { i, e -> Line("${i + 1}.  ${e.key}", "${e.value}") }
}

private class Smart(val name: String, val about: String, val tracks: List<Track>)

@Composable
private fun SmartView(history: List<Played>, liked: List<Track>) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val lists = remember(history, liked) {
        val now = System.currentTimeMillis()
        val byId = history.groupBy { it.track.id.value }
        val most = byId.values.sortedByDescending { it.size }.take(50).map { it.first().track }
        val onRepeat = history.filter { it.at >= now - 30 * DAY }.groupBy { it.track.id.value }.values
            .filter { it.size >= 3 }.sortedByDescending { it.size }.map { it.first().track }
        val forgotten = liked.filter { t -> byId[t.id.value]?.none { it.at >= now - 90 * DAY } ?: false }
        val unheard = liked.filter { it.id.value !in byId }
        val quick = liked.filter { (it.durationMs ?: Long.MAX_VALUE) in 1L..(3 * 60_000L) }
        val long = liked.filter { (it.durationMs ?: 0L) >= 6 * 60_000L }
        listOf(
            Smart("Most played", "Your top songs of all time, as far as this phone has seen", most),
            Smart("On repeat", "Played three times or more in the last 30 days", onRepeat),
            Smart("Liked, but not lately", "Liked songs you haven't played in 90 days", forgotten),
            Smart("Liked, never played", "Liked songs this phone has no play of", unheard),
            Smart("Liked, under 3 minutes", "The short ones from your likes", quick),
            Smart("Liked, 6 minutes or more", "The long ones from your likes", long),
        )
    }
    var saved by remember { mutableStateOf(setOf<String>()) }
    Txt("Lists made from your listening, worked out on this phone each time you open this. Play one, or keep it as a playlist (a copy as it is now).", Type.Aside, color = Ink.Grey)
    lists.forEach { s ->
        Heading(s.name)
        Txt(if (s.tracks.isEmpty()) "${s.about}. Nothing fits yet." else "${s.about}. ${s.tracks.size} songs.", Type.Aside, color = Ink.Grey)
        if (s.tracks.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Txt("Play", Type.Aside, modifier = Modifier.clickable { actions.play(s.tracks, 0) }.padding(vertical = 10.dp))
                Txt(
                    if (s.name in saved) "Kept as a playlist" else "Keep as a playlist",
                    Type.Aside,
                    modifier = Modifier.clickable {
                        if (s.name !in saved) {
                            saved = saved + s.name
                            scope.launch { container.library.createPlaylist(s.name, tracks = s.tracks) }
                        }
                    }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

private class Dups(val playlist: LocalPlaylist, val extras: List<Long>, val examples: List<String>)

@Composable
private fun DuplicatesView(liked: List<Track>) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val playlists by container.library.playlists.collectAsStateWithLifecycle(emptyList())
    var round by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf<String?>(null) }
    val found by produceState<List<Dups>?>(null, playlists, round) {
        value = withContext(Dispatchers.Default) {
            playlists.mapNotNull { p ->
                val entries = container.library.entries(p.id).first()
                val seen = HashSet<String>()
                val extra = ArrayList<Long>()
                val names = ArrayList<String>()
                entries.forEach { e -> if (!seen.add(keyOf(e.track))) { extra.add(e.entryId); if (names.size < 2) names.add(e.track.title) } }
                if (extra.isEmpty()) null else Dups(p, extra, names)
            }
        }
    }
    val likedExtras = remember(liked) {
        val seen = HashSet<String>()
        liked.filter { !seen.add(keyOf(it)) }
    }
    Txt("Songs that appear more than once: the same song id, or the same title and first artist. The first one stays; only the later copies go.", Type.Aside, color = Ink.Grey)
    Heading("In your playlists")
    val f = found
    if (f == null) Txt("Looking.", Type.Aside, color = Ink.Grey)
    else if (f.isEmpty()) Txt("No playlist has a song twice.", Type.Aside, color = Ink.Grey)
    else f.forEach { d ->
        val key = "p${d.playlist.id}"
        Line(d.playlist.title, "${d.extras.size} extra")
        Txt("e.g. " + d.examples.joinToString(", "), Type.Aside, color = Ink.Grey, maxLines = 1)
        Txt(
            if (asked == key) "Tap again to remove ${d.extras.size}" else "Remove the extras",
            Type.Aside,
            modifier = Modifier.clickable {
                if (asked == key) {
                    asked = null
                    scope.launch { d.extras.forEach { container.library.remove(d.playlist.id, it) }; round++ }
                } else asked = key
            }.padding(vertical = 10.dp),
        )
    }
    Heading("In your liked songs")
    if (likedExtras.isEmpty()) Txt("No song is liked twice.", Type.Aside, color = Ink.Grey)
    else {
        Line("Copies", "${likedExtras.size}")
        Txt("e.g. " + likedExtras.take(2).joinToString(", ") { it.title }, Type.Aside, color = Ink.Grey, maxLines = 1)
        Txt(
            if (asked == "liked") "Tap again to unlike ${likedExtras.size}" else "Unlike the extras",
            Type.Aside,
            modifier = Modifier.clickable {
                if (asked == "liked") {
                    asked = null
                    scope.launch { likedExtras.forEach { container.library.setLiked(it, false) } }
                } else asked = "liked"
            }.padding(vertical = 10.dp),
        )
    }
}
