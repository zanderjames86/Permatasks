package app.permatasks.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App-wide snackbar so any screen can offer "Undo". */
val LocalSnackbar = compositionLocalOf { SnackbarHostState() }

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize)) }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (expanded == false) -90f else 0f, label = "chevron")
    Row(
        Modifier.fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text("$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
        if (expanded != null) {
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A flat, rounded group of rows, like the cards in Google's settings apps. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { Column(Modifier.padding(vertical = 6.dp).animateContentSize(), content = content) }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) IconBadge(icon, iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
        if (showChevron) Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small rounded label used for dates, projects, labels. */
@Composable
fun Pill(text: String, color: Color, icon: ImageVector? = null, filled: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (filled) color.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = if (filled) 6.dp else 0.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp = 56.dp, stroke: Dp = 6.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.size(size)) {
        val s = stroke.toPx()
        val arc = Size(this.size.width - s, this.size.height - s)
        val topLeft = Offset(s / 2, s / 2)
        drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(s))
        drawArc(color, -90f, 360f * animated, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

val ScreenPadding = PaddingValues(bottom = 112.dp)
