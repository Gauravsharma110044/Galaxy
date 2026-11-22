package app.aether.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aether.core.designsystem.component.Txt
import app.aether.core.designsystem.theme.Ink
import app.aether.core.designsystem.theme.Space
import app.aether.core.designsystem.theme.Type
import app.aether.ui.Route
import app.aether.ui.home.galaxyAccent
import kotlin.random.Random

/** A playlist as the sky shows it: where it opens, its name, and how many songs it holds. */
class ConstellationItem(val route: Route, val key: Long, val title: String, val count: Int)

/**
 * Each playlist as a constellation: one star for each of its songs (up to fourteen), always in the
 * same pattern for the same playlist, joined by thin lines in the accent colour. A bigger playlist is a
 * fuller constellation; an empty one is three dim stars. Tap one to open the playlist.
 */
fun LazyListScope.constellationItems(
    lists: List<ConstellationItem>,
    accentIndex: Int,
    open: (Route) -> Unit,
) {
    val accent = galaxyAccent(accentIndex)
    if (lists.isEmpty()) {
        item(key = "sky:none") {
            Txt("Make a playlist and it appears here as a constellation.", Type.Aside, color = Ink.Grey, modifier = Modifier.padding(horizontal = Space.edge, vertical = 24.dp))
        }
        return
    }
    items(lists.chunked(2), key = { row -> "sky:" + row.joinToString(",") { it.key.toString() } }) { pair ->
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { c -> Box(Modifier.weight(1f)) { Constellation(c, accent, open) } }
            if (pair.size == 1) Box(Modifier.weight(1f)) {}
        }
    }
}

@Composable
private fun Constellation(c: ConstellationItem, accent: Color, open: (Route) -> Unit) {
    val n = if (c.count <= 0) 3 else c.count.coerceIn(3, 14)
    val points = androidx.compose.runtime.remember(c.key, n) {
        val r = Random(c.key * 7919L + 13)
        List(n) { Offset(.1f + r.nextFloat() * .8f, .14f + r.nextFloat() * .72f) }.sortedBy { it.x }
    }
    val sizes = androidx.compose.runtime.remember(c.key, n) {
        val r = Random(c.key * 31L + 5)
        List(n) { 1.2f + r.nextFloat() * 1.9f }
    }
    Column(
        Modifier.fillMaxWidth().clickable { open(c.route) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(120.dp)
                .semantics { contentDescription = "${c.title}, a constellation of ${c.count} songs" },
        ) {
            val unit = size.width / 160f
            if (c.count > 0) {
                for (i in 0 until points.size - 1) {
                    drawLine(accent.copy(alpha = .38f), Offset(points[i].x * size.width, points[i].y * size.height), Offset(points[i + 1].x * size.width, points[i + 1].y * size.height), strokeWidth = .8f * unit)
                }
            }
            points.forEachIndexed { i, p ->
                val centre = Offset(p.x * size.width, p.y * size.height)
                val strength = if (c.count > 0) 1f else .35f
                drawCircle(accent.copy(alpha = .16f * strength), sizes[i] * 3.2f * unit, centre)
                drawCircle(Color.White.copy(alpha = .9f * strength), sizes[i] * unit, centre)
            }
        }
        Txt(c.title, Type.Aside, color = Ink.Grey, maxLines = 1)
        Txt(if (c.count <= 0) "Empty" else if (c.count == 1) "1 song" else "${c.count} songs", Type.Aside, color = Ink.Grey)
    }
}
