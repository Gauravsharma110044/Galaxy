@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.aether.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aether.core.designsystem.component.Txt
import app.aether.core.designsystem.glass.LocalReduceMotion
import app.aether.core.designsystem.theme.Type
import app.aether.ui.DockHeight
import app.aether.ui.LocalPageSettled
import app.aether.ui.LocalRoomsHidden
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class GalaxyPoint(val x: Float, val y: Float, val radius: Float, val light: Float)

/** Galaxy's original, offline artwork. A bounded Canvas, with no bitmap loading or shaders. */
@Composable
internal fun GalaxyMasthead(modifier: Modifier = Modifier, onScreen: () -> Boolean, scrolling: () -> Boolean) {
    val reduce = LocalReduceMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val hidden = LocalRoomsHidden.current
    val settled = LocalPageSettled.current
    val active = state.isAtLeast(Lifecycle.State.RESUMED) && onScreen() && !hidden() && settled() && !scrolling() && !reduce
    var seconds by remember { mutableFloatStateOf(0f) }
    // Thirty redraws/second only while visible and still. Draw-state reads avoid recomposition.
    LaunchedEffect(active) {
        if (active) while (true) { delay(33); seconds += 0.033f }
    }
    val stars = remember {
        val r = Random(1729)
        List(135) { GalaxyPoint(r.nextFloat(), r.nextFloat(), 0.55f + r.nextFloat() * 1.1f, r.nextFloat()) }
    }
    val dust = remember {
        val r = Random(42)
        List(920) { i ->
            val d = sqrt(r.nextFloat())
            val angle = d * 10f + (i % 3) * (2f * PI.toFloat() / 3f) + (r.nextFloat() - .5f) * (.16f + .5f * d)
            GalaxyPoint(cos(angle) * d, sin(angle) * d, .35f + r.nextFloat() * 1.25f, .25f + r.nextFloat() * .65f)
        }
    }
    val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val foot = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() + DockHeight + 58.dp + 12.dp
    Box(modifier.fillMaxWidth().height((window - foot).coerceIn(480.dp, 760.dp)).background(Color.Black)) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Slowly rotating spiral galaxy and twinkling stars on a true black sky" }) {
            val t = seconds
            val unit = size.width / 390f
            stars.forEachIndexed { i, s ->
                val p = Offset(s.x * size.width, s.y * size.height * .89f)
                val light = (.50f + .38f * sin(t * (.55f + s.light) + i)).coerceIn(.1f, .95f)
                drawCircle(Color.White.copy(alpha = light), s.radius * unit, p)
                if (i % 23 == 0) {
                    val a = Color.White.copy(alpha = light * .55f)
                    drawLine(a, p - Offset(5f * unit, 0f), p + Offset(5f * unit, 0f), .55f * unit)
                    drawLine(a, p - Offset(0f, 5f * unit), p + Offset(0f, 5f * unit), .55f * unit)
                }
            }
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .76f to Color.Transparent, 1f to Color.Black))
        }
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(app.aether.R.drawable.galaxy_realistic),
            contentDescription = "Depth-rendered spiral galaxy, an artistic visualization",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(bottom = 90.dp).androidxGalaxyRotation { seconds * .55f - 18f },
        )
        Txt("Listen", Type.Monument.copy(fontSize = Type.Monument.fontSize * 1.35f, lineHeight = Type.Monument.lineHeight * 1.35f), align = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp))
    }
}


/** A quiet, true-black sky on the first-launch welcome screen. */
@Composable
internal fun GalaxyWelcomeSky(modifier: Modifier = Modifier) {
    val points = remember {
        val r = Random(2026)
        List(120) { GalaxyPoint(r.nextFloat(), r.nextFloat(), .55f + r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.background(Color.Black)) {
        points.forEach { p ->
            drawCircle(Color.White.copy(alpha = .22f + p.light * .5f), p.radius * size.width / 390f, Offset(p.x * size.width, p.y * size.height))
        }
    }
}

private fun Modifier.androidxGalaxyRotation(angle: () -> Float): Modifier = this.then(
    Modifier.graphicsLayer { rotationZ = angle() }
)
