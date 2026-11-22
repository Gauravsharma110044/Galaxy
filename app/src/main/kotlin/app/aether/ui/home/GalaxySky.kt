package app.aether.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The colours Galaxy's sky can take, by name. The first is the plain white it always had. */
val GalaxyAccents: List<Pair<String, Long>> = listOf(
    "White" to 0xFFFFFFFF,
    "Sky" to 0xFF7CC4FF,
    "Violet" to 0xFFA98BFF,
    "Rose" to 0xFFFF86B5,
    "Amber" to 0xFFFFC15E,
    "Mint" to 0xFF6FE3B0,
)

fun galaxyAccent(index: Int): Color = Color(GalaxyAccents[index.coerceIn(0, GalaxyAccents.lastIndex)].second)

/** A star in the 3D galaxy: a place in space, how far from the centre (0 to 1), its size and its light. */
private class Star3(val x: Float, val y: Float, val z: Float, val r: Float, val size: Float, val light: Float)

private fun buildGalaxy(): List<Star3> {
    val rnd = Random(2718)
    val out = ArrayList<Star3>(2100)
    // The arms: three of them winding out from the middle, thin at the rim and thicker near the core.
    repeat(1500) { i ->
        val d = sqrt(rnd.nextFloat())
        val angle = d * 9f + (i % 3) * (2f * PI.toFloat() / 3f) + (rnd.nextFloat() - .5f) * (.18f + .55f * d)
        val lift = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * (.015f + .09f * (1f - d))
        out.add(Star3(cos(angle) * d, lift, sin(angle) * d, d, .5f + rnd.nextFloat() * 1.3f, .35f + rnd.nextFloat() * .65f))
    }
    // The bulge: a small warm ball of stars at the heart.
    repeat(500) {
        val d = rnd.nextFloat() * rnd.nextFloat() * .28f
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val lift = (rnd.nextFloat() - .5f) * .22f * (1f - d * 2f)
        out.add(Star3(cos(a) * d, lift, sin(a) * d, d, .6f + rnd.nextFloat() * 1.2f, .5f + rnd.nextFloat() * .5f))
    }
    return out
}

/**
 * A galaxy drawn in space: about two thousand stars with a position in three dimensions, turned and
 * seen in perspective, nearer ones larger and brighter. It turns slowly by itself; drag sideways to
 * turn it by hand. Entirely offline: points on a Canvas, no pictures or shaders.
 */
@Composable
fun Galaxy3D(accent: Color, seconds: () -> Float, modifier: Modifier = Modifier) {
    val stars = remember { buildGalaxy() }
    var spin by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier
            .semantics { contentDescription = "A spiral galaxy in three dimensions, slowly turning. Drag sideways to turn it." }
            .pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> spin += dx * .008f } },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) * .47f
        val unit = size.width / 390f
        val yaw = seconds() * .16f + spin
        val tilt = 1.12f
        val cosY = cos(yaw); val sinY = sin(yaw)
        val cosT = cos(tilt); val sinT = sin(tilt)
        val warm = Color(0xFFFFEBC2)
        val rim = lerp(Color.White, accent, .75f)
        stars.forEach { s ->
            val x1 = s.x * cosY - s.z * sinY
            val z1 = s.x * sinY + s.z * cosY
            val y2 = s.y * cosT - z1 * sinT
            val z2 = s.y * sinT + z1 * cosT
            val f = 1f / (1.7f - z2 * .6f)
            val near = ((z2 + 1f) / 2f).coerceIn(0f, 1f)
            val colour = lerp(warm, rim, (s.r * 1.6f).coerceIn(0f, 1f))
            val a = (s.light * (.35f + .65f * near)).coerceIn(.08f, .95f)
            drawCircle(colour.copy(alpha = a), s.size * unit * (.7f + .8f * f), Offset(cx + x1 * f * radius * 1.7f, cy + y2 * f * radius * 1.7f))
        }
    }
}
