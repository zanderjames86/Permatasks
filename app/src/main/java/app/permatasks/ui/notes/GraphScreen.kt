package app.permatasks.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.permatasks.container
import app.permatasks.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

private class Node(val id: String, val title: String, var x: Float, var y: Float, var degree: Int = 0)

/** Obsidian-style graph of notes and their [[links]]. Pinch to zoom, drag to pan, tap to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalContext.current.container
    val notes by remember { c.notes.notes.observeAll() }.collectAsState(initial = emptyList())
    val links by remember { c.notes.notes.observeLinks() }.collectAsState(initial = emptyList())
    var layout by remember { mutableStateOf<Pair<List<Node>, List<Pair<Int, Int>>>?>(null) }

    LaunchedEffect(notes, links) {
        layout = withContext(Dispatchers.Default) {
            val rnd = Random(42)
            val nodes = notes.map { Node(it.id, it.title.ifBlank { "Untitled" }, rnd.nextFloat() * 600 - 300, rnd.nextFloat() * 600 - 300) }
            val byTitle = notes.withIndex().associate { (i, n) -> n.title.trim().lowercase() to i }
            val byId = notes.withIndex().associate { (i, n) -> n.id to i }
            val edges = links.mapNotNull { l -> val a = byId[l.noteId]; val b = byTitle[l.target]; if (a != null && b != null && a != b) a to b else null }.distinct()
            edges.forEach { (a, b) -> nodes[a].degree++; nodes[b].degree++ }
            val k = 140f
            repeat(300) { step ->
                val temp = 40f * (1 - step / 300f) + 1f
                val dx = FloatArray(nodes.size); val dy = FloatArray(nodes.size)
                for (i in nodes.indices) for (j in i + 1 until nodes.size) {
                    val ex = nodes[i].x - nodes[j].x; val ey = nodes[i].y - nodes[j].y
                    val d = max(sqrt(ex * ex + ey * ey), 0.01f)
                    val f = k * k / d
                    dx[i] += ex / d * f; dy[i] += ey / d * f; dx[j] -= ex / d * f; dy[j] -= ey / d * f
                }
                for ((a, b) in edges) {
                    val ex = nodes[a].x - nodes[b].x; val ey = nodes[a].y - nodes[b].y
                    val d = max(sqrt(ex * ex + ey * ey), 0.01f)
                    // Spring towards a natural length so linked notes don't collapse onto each other.
                    val f = (d - k) * 0.6f
                    dx[a] -= ex / d * f; dy[a] -= ey / d * f; dx[b] += ex / d * f; dy[b] += ey / d * f
                }
                for (i in nodes.indices) {
                    val g = if (nodes[i].degree == 0) 0.25f else 0.08f
                    dx[i] -= nodes[i].x * g; dy[i] -= nodes[i].y * g
                    val d = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), 0.01f)
                    nodes[i].x += dx[i] / d * minOf(d, temp); nodes[i].y += dy[i] / d * minOf(d, temp)
                }
            }
            // Centre the layout on the origin so it can be fitted to the screen.
            if (nodes.isNotEmpty()) {
                val mx = (nodes.minOf { it.x } + nodes.maxOf { it.x }) / 2; val my = (nodes.minOf { it.y } + nodes.maxOf { it.y }) / 2
                nodes.forEach { it.x -= mx; it.y -= my }
            }
            nodes to edges
        }
    }
    /** Scale that fits the whole graph into ~75% of the canvas. */
    fun fit(nodes: List<Node>, w: Float, h: Float): Float {
        val spanX = (nodes.maxOfOrNull { kotlin.math.abs(it.x) } ?: 1f).coerceAtLeast(60f) * 2
        val spanY = (nodes.maxOfOrNull { kotlin.math.abs(it.y) } ?: 1f).coerceAtLeast(60f) * 2
        return minOf(w * 0.7f / spanX, h * 0.7f / spanY, 2.5f)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(0.3f, 4f); offset += pan }
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurface

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Graph") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        val l = layout
        if (notes.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState(Icons.Outlined.Hub, "Nothing to connect yet", "Create notes and link them with [[Note title]].") }
            return@Scaffold
        }
        Canvas(
            Modifier.padding(padding).fillMaxSize().transformable(transform).pointerInput(l, scale, offset) {
                detectTapGestures { tap ->
                    val nodes = l?.first ?: return@detectTapGestures
                    val sc = scale * fit(nodes, size.width.toFloat(), size.height.toFloat())
                    val cx = size.width / 2f + offset.x; val cy = size.height / 2f + offset.y
                    nodes.minByOrNull { n ->
                        val px = cx + n.x * sc; val py = cy + n.y * sc
                        (px - tap.x) * (px - tap.x) + (py - tap.y) * (py - tap.y)
                    }?.let { n ->
                        val px = cx + n.x * sc; val py = cy + n.y * sc
                        if ((px - tap.x) * (px - tap.x) + (py - tap.y) * (py - tap.y) < 48f * 48f) onOpen(n.id)
                    }
                }
            },
        ) {
            val (nodes, edges) = l ?: return@Canvas
            val sc = scale * fit(nodes, size.width, size.height)
            val cx = size.width / 2f + offset.x; val cy = size.height / 2f + offset.y
            fun p(n: Node) = Offset(cx + n.x * sc, cy + n.y * sc)
            edges.forEach { (a, b) -> drawLine(edgeColor, p(nodes[a]), p(nodes[b]), strokeWidth = 4f) }
            nodes.forEach { n ->
                val r = (14f + n.degree * 5f).coerceAtMost(40f) * scale.coerceIn(0.6f, 1.6f)
                drawCircle(primary.copy(alpha = 0.18f), r * 1.6f, p(n))
                drawCircle(primary, r, p(n))
                if (scale > 0.55f) {
                    val text = measurer.measure(n.title.take(28), TextStyle(fontSize = 13.sp, color = labelColor))
                    drawText(text, topLeft = p(n) + Offset(-text.size.width / 2f, r + 4f))
                }
            }
        }
    }
}
