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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aether.automix.AnalysisStore
import app.aether.core.data.Played
import app.aether.core.designsystem.component.Txt
import app.aether.core.designsystem.theme.Ink
import app.aether.core.designsystem.theme.Type
import app.aether.core.model.Track
import app.aether.ui.LocalActions
import app.aether.ui.common.LocalContainer
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What Automix measured for one song, and where it ranks among the songs measured. */
private class Feel(val track: Track, val bpm: Double, val key: String, val vocal: Double, val energy: Double, var rank: Double = 0.5)

private fun tempoWord(bpm: Double) = when {
    bpm < 90 -> "Slow"
    bpm < 120 -> "Mid-tempo"
    bpm < 140 -> "Upbeat"
    else -> "Fast"
}

private fun energyWord(rank: Double) = when {
    rank < .34 -> "Calm"
    rank < .67 -> "Medium"
    else -> "Intense"
}

private fun voiceWord(v: Double) = when {
    v >= .6 -> "Vocal"
    v <= .3 -> "Mostly instrumental"
    else -> "Some vocals"
}

/** Tempo gap in octaves, where half or double speed counts as the same pulse. */
private fun tempoGap(a: Double, b: Double): Double {
    val r = ln(a / b) / ln(2.0)
    return minOf(abs(r), abs(r - 1.0), abs(r + 1.0))
}

private class Mix(val name: String, val about: String, val tracks: List<Track>)

/**
 * Mixes and tags worked out on this phone from what Automix has already measured about a song: its
 * tempo, how loud it plays and whether it has singing. Nothing is sent anywhere and nothing is guessed
 * for a song that has not been measured, so the lists cover only songs Automix has looked at (it measures
 * the song playing and the one after it while Smart fade is on). The tags are plain rules over those
 * numbers, not a mood model.
 */
@Composable
internal fun MixesView(history: List<Played>, liked: List<Track>) {
    val container = LocalContainer.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val playing by container.playback.state.collectAsStateWithLifecycle()
    val candidates = remember(history, liked) {
        val seen = HashSet<String>()
        (liked + history.map { it.track }).filter { seen.add(it.id.value) }
    }
    val measured by produceState<List<Feel>?>(null, candidates) {
        value = withContext(Dispatchers.Default) {
            val store = AnalysisStore { context.filesDir }
            val feels = candidates.mapNotNull { t ->
                val a = store.load(t.id.value)?.takeIf { it.isUsable } ?: return@mapNotNull null
                val energy = if (a.energyCurve.isEmpty()) return@mapNotNull null else a.energyCurve.map { it.energy }.average()
                Feel(t, a.bpm, a.key, a.vocalProbability, energy)
            }
            // Rank each by energy among the measured ones: 0 the calmest, 1 the most intense.
            val order = feels.sortedBy { it.energy }
            order.forEachIndexed { i, f -> f.rank = if (order.size > 1) i.toDouble() / (order.size - 1) else .5 }
            feels
        }
    }
    var saved by remember { mutableStateOf(setOf<String>()) }
    val m = measured
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Txt("Mixes and tags from what Automix has measured on this phone: tempo, how intense a song plays, and whether it has singing. Everything stays on the phone.", Type.Aside, color = Ink.Grey)
        Spacer(Modifier.height(14.dp))
        if (m == null) { Txt("Reading.", Type.Aside, color = Ink.Grey); return@Column }
        Txt("${m.size} of ${candidates.size} songs measured.", Type.Aside)
        if (m.size < 6) {
            Spacer(Modifier.height(8.dp))
            Txt("Too few to make mixes from. Turn on Smart fade (Automix) and listen: it measures the song playing and the next one. Come back after a few sessions.", Type.Aside, color = Ink.Grey)
            return@Column
        }
        // Where the tempos fall.
        Spacer(Modifier.height(8.dp))
        val buckets = m.groupingBy { tempoWord(it.bpm) }.eachCount()
        Txt(listOf("Slow", "Mid-tempo", "Upbeat", "Fast").joinToString("  \u00B7  ") { "$it ${buckets[it] ?: 0}" }, Type.Aside, color = Ink.Grey)

        // The song playing now, with its tags.
        val now = playing.queue.getOrNull(playing.index)
        val nowFeel = now?.let { n -> m.firstOrNull { it.track.id.value == n.id.value } }
        Spacer(Modifier.height(18.dp))
        Txt("Like the song playing", Type.Aside)
        if (now == null) Txt("Nothing is playing.", Type.Aside, color = Ink.Grey)
        else if (nowFeel == null) Txt("${now.title} has not been measured yet. Let it play with Smart fade on.", Type.Aside, color = Ink.Grey)
        else {
            Txt("${now.title}: ${nowFeel.bpm.toInt()} bpm, ${tempoWord(nowFeel.bpm)}, ${energyWord(nowFeel.rank)}, ${voiceWord(nowFeel.vocal)}.", Type.Aside, color = Ink.Grey)
            val similar = m.filter { it.track.id.value != nowFeel.track.id.value }.sortedBy { f ->
                tempoGap(f.bpm, nowFeel.bpm) * 3.0 + abs(f.rank - nowFeel.rank) * 1.5 + abs(f.vocal - nowFeel.vocal) * .8 -
                    (if (f.key.isNotBlank() && f.key == nowFeel.key) .3 else 0.0)
            }.take(25).map { it.track }
            MixActions("Similar to ${now.title}", similar, saved, { saved = saved + it }, actions, container, scope)
        }

        // Mixes by tags.
        val mixes = listOf(
            Mix("Chill mix", "Slow or mid-tempo and calm", m.filter { it.bpm < 110 && it.rank < .4 }.sortedBy { it.rank }.map { it.track }),
            Mix("Hype mix", "Upbeat or fast and intense", m.filter { it.bpm >= 120 && it.rank >= .6 }.sortedByDescending { it.rank }.map { it.track }),
            Mix("Focus mix", "Mostly instrumental and not too intense", m.filter { it.vocal <= .35 && it.rank < .7 }.sortedBy { it.rank }.map { it.track }),
            Mix("Slow to fast", "Everything measured, from the slowest song to the fastest", m.sortedBy { it.bpm }.map { it.track }.let { all -> if (all.size <= 40) all else List(40) { i -> all[i * (all.size - 1) / 39] } }),
        )
        mixes.forEach { x ->
            Spacer(Modifier.height(18.dp))
            Txt(x.name, Type.Aside)
            if (x.tracks.isEmpty()) Txt("${x.about}. Nothing fits yet.", Type.Aside, color = Ink.Grey)
            else {
                Txt("${x.about}. ${x.tracks.size} songs.", Type.Aside, color = Ink.Grey)
                MixActions(x.name, x.tracks, saved, { saved = saved + it }, actions, container, scope)
            }
        }
    }
}

@Composable
private fun MixActions(
    name: String,
    tracks: List<Track>,
    saved: Set<String>,
    onSaved: (String) -> Unit,
    actions: app.aether.ui.Actions,
    container: app.aether.Container,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (tracks.isEmpty()) return
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Txt("Play", Type.Aside, modifier = Modifier.clickable { actions.play(tracks, 0) }.padding(vertical = 10.dp))
        Txt(
            if (name in saved) "Kept as a playlist" else "Keep as a playlist",
            Type.Aside,
            modifier = Modifier.clickable {
                if (name !in saved) {
                    onSaved(name)
                    scope.launch { container.library.createPlaylist(name, tracks = tracks) }
                }
            }.padding(vertical = 10.dp),
        )
    }
}
