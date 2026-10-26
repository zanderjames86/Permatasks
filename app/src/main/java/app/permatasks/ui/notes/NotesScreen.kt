package app.permatasks.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.NoteEntity
import app.permatasks.domain.BlockType
import app.permatasks.domain.NoteBlocks
import app.permatasks.ui.components.EmptyState
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.format.Formats

@Composable
fun NotesContent(onOpen: (String) -> Unit) {
    val c = LocalContext.current.container
    val notes by remember { c.notes.notes.observeAll() }.collectAsState(initial = null)
    @Suppress("UNUSED_VARIABLE") val tick by c.settings.changes.collectAsState()
    val list = notes ?: return
    if (list.isEmpty()) {
        EmptyState(
            Icons.AutoMirrored.Outlined.Notes, "Your notes live here",
            "Pages with headings, images and checklists. Link pages with [[Title]]. Checklist items can nag you like tasks.",
        )
        return
    }
    val pinned = list.filter { it.pinned }
    val others = list.filter { !it.pinned }
    val columns = if (c.settings.notesGrid) 2 else 1
    LazyVerticalStaggeredGrid(
        StaggeredGridCells.Fixed(columns),
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 112.dp),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pinned.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) { SectionHeader("Pinned", pinned.size) }
            items(pinned, key = { it.id }) { NoteCard(it) { onOpen(it.id) } }
            item(span = StaggeredGridItemSpan.FullLine) { SectionHeader("Others", others.size) }
        }
        items(others, key = { it.id }) { NoteCard(it) { onOpen(it.id) } }
    }
}

@Composable
fun NoteCard(note: NoteEntity, onClick: () -> Unit) {
    val blocks = remember(note.blocks) { NoteBlocks.decode(note.blocks) }
    val checks = blocks.filter { it.type == BlockType.CHECK }
    val images = blocks.count { it.type == BlockType.IMAGE }
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (note.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            val preview = blocks.filter { it.type != BlockType.CHECK && it.type != BlockType.IMAGE && it.text.isNotBlank() }
                .take(6).joinToString("\n") { b -> NoteBlocks.linkRe.replace(b.text) { m -> m.groupValues[2].ifBlank { m.groupValues[1] } } }
            if (preview.isNotBlank()) {
                Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            checks.take(4).forEach { b ->
                Text(
                    (if (b.checked) "☑ " else "☐ ") + b.text, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Formats.date(note.updatedAt, false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (checks.isNotEmpty()) {
                    Icon(Icons.Outlined.CheckBox, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${checks.count { it.checked }}/${checks.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (images > 0) Icon(Icons.Outlined.Image, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
