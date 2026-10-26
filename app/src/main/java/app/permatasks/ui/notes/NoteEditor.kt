package app.permatasks.ui.notes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.permatasks.container
import app.permatasks.data.NoteEntity
import app.permatasks.data.TaskEntity
import app.permatasks.data.TaskStatus
import app.permatasks.domain.Block
import app.permatasks.domain.BlockType
import app.permatasks.domain.NoteBlocks
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.components.Pill
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.format.Formats
import app.permatasks.ui.tasks.dateColor
import app.permatasks.ui.theme.MonoStyle
import app.permatasks.voice.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LIST_TYPES = setOf(BlockType.BULLET, BlockType.NUMBERED, BlockType.CHECK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: String, startDictation: Boolean, onBack: () -> Unit, onOpenNote: (String) -> Unit, onOpenTask: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val repo = c.notes
    val scope = rememberCoroutineScope()

    var note by remember { mutableStateOf<NoteEntity?>(null) }
    val blocks = remember { mutableStateListOf<Block>() }
    var title by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var focusRequestId by remember { mutableStateOf<String?>(null) }
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reminderFor by remember { mutableStateOf<Block?>(null) }

    LaunchedEffect(noteId) {
        val n = repo.notes.get(noteId) ?: return@LaunchedEffect onBack()
        note = n
        title = n.title
        blocks.clear(); blocks.addAll(NoteBlocks.decode(n.blocks))
        reading = n.plainText.isNotBlank() && !startDictation
    }
    val current = note ?: return
    val linkedTasks by remember(noteId) { c.repo.tasks.observeForNote(noteId) }.collectAsState(initial = emptyList())
    val taskById = linkedTasks.associateBy { it.id }
    val backlinks by remember(title) { repo.notes.observeBacklinks(title.trim()) }.collectAsState(initial = emptyList())

    // Autosave.
    LaunchedEffect(version) {
        if (version == 0) return@LaunchedEffect
        delay(600)
        repo.save(current.copy(title = title.trim()), blocks.toList())
    }
    DisposableEffect(Unit) {
        onDispose {
            val n = note ?: return@onDispose
            val snapshot = blocks.toList()
            val t = title.trim()
            if (t.isEmpty() && NoteBlocks.plainText(snapshot).isEmpty() && snapshot.none { it.image != null }) {
                c.scope.launch { repo.delete(n) }
            } else {
                c.scope.launch { repo.save(n.copy(title = t), snapshot) }
            }
        }
    }
    fun changed() { version++ }

    fun focus(id: String) { focusRequestId = id }
    LaunchedEffect(focusRequestId) {
        val id = focusRequestId ?: return@LaunchedEffect
        delay(30)
        runCatching { requesters[id]?.requestFocus() }
        focusRequestId = null
    }

    fun updateBlock(i: Int, b: Block) { if (i in blocks.indices) { blocks[i] = b; changed() } }
    fun insertAfter(i: Int, b: Block) { blocks.add(i + 1, b); changed(); focus(b.id) }
    fun setType(type: BlockType) {
        val i = blocks.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: blocks.lastIndex
        if (type == BlockType.DIVIDER) {
            insertAfter(i, Block(type = BlockType.DIVIDER))
            insertAfter(i + 1, Block())
            return
        }
        val b = blocks[i]
        updateBlock(i, b.copy(type = if (b.type == type) BlockType.PARAGRAPH else type))
        focus(b.id)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { c.vault.importImage(uri) } ?: return@launch
            val i = blocks.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: blocks.lastIndex
            insertAfter(i, Block(type = BlockType.IMAGE, image = name))
            insertAfter(i + 1, Block())
        }
    }

    var listening by remember { mutableStateOf(false) }
    var dictationTarget by remember { mutableStateOf<String?>(null) }
    val voice = remember {
        VoiceInput(
            context,
            onPartial = {},
            onResult = { text ->
                val i = blocks.indexOfFirst { it.id == dictationTarget }
                if (i >= 0 && blocks[i].text.isBlank()) updateBlock(i, blocks[i].copy(text = text.replaceFirstChar(Char::uppercase)))
                else insertAfter(if (i >= 0) i else blocks.lastIndex, Block(text = text.replaceFirstChar(Char::uppercase)))
            },
            onError = {},
            onListening = { listening = it },
        )
    }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) voice.start() }
    fun dictate() {
        dictationTarget = focusedId ?: blocks.lastOrNull()?.id
        reading = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start()
        else mic.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { if (startDictation) { delay(300); dictate() } }

    fun openLink(target: String) = scope.launch {
        repo.save(current.copy(title = title.trim()), blocks.toList())
        onOpenNote(repo.openOrCreate(target).id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconToggleButton(current.pinned, { p -> note = current.copy(pinned = p); scope.launch { repo.save(current.copy(pinned = p, title = title), blocks.toList()) } }) {
                        Icon(if (current.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin")
                    }
                    IconButton(onClick = { reading = !reading }) { Icon(if (reading) Icons.Outlined.Edit else Icons.Outlined.Visibility, if (reading) "Edit" else "Read") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Share as Markdown") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                menu = false
                                val md = NoteBlocks.toMarkdown(title, blocks)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, md), "Share note"))
                            })
                            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (!reading) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.imePadding()) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolButton(Icons.Outlined.TextFields, "Text") { setType(BlockType.PARAGRAPH) }
                        ToolButton(Icons.Outlined.Title, "Heading") { setType(BlockType.H1) }
                        ToolButton(Icons.Outlined.CheckBox, "Checklist") { setType(BlockType.CHECK) }
                        ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullets") { setType(BlockType.BULLET) }
                        ToolButton(Icons.Filled.FormatListNumbered, "Numbered") { setType(BlockType.NUMBERED) }
                        ToolButton(Icons.Outlined.FormatQuote, "Quote") { setType(BlockType.QUOTE) }
                        ToolButton(Icons.Outlined.Lightbulb, "Callout") { setType(BlockType.CALLOUT) }
                        ToolButton(Icons.Outlined.Code, "Code") { setType(BlockType.CODE) }
                        ToolButton(Icons.Outlined.HorizontalRule, "Divider") { setType(BlockType.DIVIDER) }
                        ToolButton(Icons.Outlined.Image, "Image") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        ToolButton(Icons.Outlined.Link, "Link to note") {
                            val i = blocks.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: blocks.lastIndex
                            updateBlock(i, blocks[i].copy(text = blocks[i].text + "[[]]"))
                            focus(blocks[i].id)
                        }
                        ToolButton(Icons.Outlined.Mic, if (listening) "Listening" else "Dictate", active = listening) { if (listening) voice.stop() else dictate() }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                if (reading) {
                    Text(
                        title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                } else {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            title, { title = it.replace("\n", ""); changed() },
                            textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(
                    "Edited " + Formats.date(current.updatedAt, true),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            itemsIndexed(blocks, key = { _, b -> b.id }) { i, b ->
                val numberIndex = if (b.type == BlockType.NUMBERED) blocks.subList(0, i + 1).takeLastWhile { it.type == BlockType.NUMBERED }.size else 0
                val task = b.taskId?.let { taskById[it] }
                val checked = b.checked || (task != null && task.status != TaskStatus.OPEN)
                if (reading) {
                    ReadBlock(b, numberIndex, checked, task, onLink = ::openLink, onToggle = { v ->
                        updateBlock(i, b.copy(checked = v)); scope.launch { repo.setChecked(b, v) }
                    }, onOpenTask = onOpenTask)
                } else {
                    val requester = requesters.getOrPut(b.id) { FocusRequester() }
                    EditBlock(
                        block = b, numberIndex = numberIndex, checked = checked, task = task, requester = requester,
                        onFocus = { focusedId = b.id },
                        onText = { raw ->
                            val shortcut = if (b.type == BlockType.PARAGRAPH) NoteBlocks.shortcut(raw) else null
                            when {
                                shortcut != null && shortcut.first == BlockType.DIVIDER -> {
                                    updateBlock(i, b.copy(type = BlockType.DIVIDER, text = ""))
                                    insertAfter(i, Block())
                                }
                                shortcut != null -> updateBlock(i, b.copy(type = shortcut.first, text = shortcut.second))
                                raw.contains('\n') && b.type != BlockType.CODE -> {
                                    val before = raw.substringBefore('\n')
                                    val after = raw.substringAfter('\n')
                                    if (before.isEmpty() && after.isEmpty() && b.type in LIST_TYPES) {
                                        updateBlock(i, b.copy(type = BlockType.PARAGRAPH, text = ""))
                                    } else {
                                        updateBlock(i, b.copy(text = before))
                                        insertAfter(i, Block(type = if (b.type in LIST_TYPES) b.type else BlockType.PARAGRAPH, text = after))
                                    }
                                }
                                else -> updateBlock(i, b.copy(text = raw))
                            }
                        },
                        onBackspaceEmpty = {
                            if (b.type != BlockType.PARAGRAPH) {
                                updateBlock(i, b.copy(type = BlockType.PARAGRAPH))
                            } else if (blocks.size > 1) {
                                blocks.removeAt(i); changed()
                                blocks.getOrNull(i - 1)?.let { focus(it.id) }
                            }
                        },
                        onToggle = { v -> updateBlock(i, b.copy(checked = v)); scope.launch { repo.setChecked(b, v) } },
                        onRemind = { reminderFor = b },
                        onOpenTask = onOpenTask,
                        onRemove = {
                            b.image?.let(c.vault::delete)
                            blocks.removeAt(i); if (blocks.isEmpty()) blocks.add(Block()); changed()
                        },
                    )
                }
            }
            item {
                if (!reading) {
                    Box(Modifier.fillMaxWidth().height(80.dp).clickable {
                        val last = blocks.lastOrNull()
                        if (last == null || last.text.isNotEmpty() || last.type != BlockType.PARAGRAPH) insertAfter(blocks.lastIndex, Block()) else focus(last.id)
                    })
                }
            }
            if (backlinks.any { it.id != noteId } || linkedTasks.isNotEmpty()) {
                item { HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            }
            val others = backlinks.filter { it.id != noteId }
            if (others.isNotEmpty()) {
                item { SectionHeader("Linked from", others.size) }
                items(others, key = { "bl_" + it.id }) { n ->
                    ListRow(n.title.ifBlank { "Untitled" }, n.plainText.take(80), Icons.AutoMirrored.Outlined.Notes, onClick = { onOpenNote(n.id) }, showChevron = true)
                }
            }
            if (linkedTasks.isNotEmpty()) {
                item { SectionHeader("Reminders from this note", linkedTasks.count { it.status == TaskStatus.OPEN }) }
                items(linkedTasks, key = { "t_" + it.id }) { t ->
                    ListRow(
                        t.title, t.dueAt?.let { Formats.date(it, t.hasTime) } ?: "No date", Icons.Outlined.TaskAlt,
                        if (t.status == TaskStatus.OPEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onOpenTask(t.id) }, showChevron = true,
                    )
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    reminderFor?.let { b ->
        var whenText by remember { mutableStateOf("tomorrow 9am") }
        AlertDialog(
            onDismissRequest = { reminderFor = null },
            title = { Text("Remind me") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\"${b.text.ifBlank { "Checklist item" }}\" becomes a task that nags until ticked.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(whenText, { whenText = it }, label = { Text("When") }, singleLine = true, supportingText = { Text("e.g. today 6pm, fri, every mon 8am") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = b
                    reminderFor = null
                    scope.launch {
                        val id = repo.remindChecklistItem(noteId, "${target.text} $whenText".trim()) ?: return@launch
                        val i = blocks.indexOfFirst { it.id == target.id }
                        if (i >= 0) updateBlock(i, blocks[i].copy(taskId = id))
                    }
                }) { Text("Create reminder") }
            },
            dismissButton = { TextButton(onClick = { reminderFor = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("The note, its images and its checklist reminders are deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val n = current
                    note = null
                    scope.launch { repo.delete(n); onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun blockStyle(type: BlockType): TextStyle {
    val t = MaterialTheme.typography
    val base = when (type) {
        BlockType.H1 -> t.headlineSmall
        BlockType.H2 -> t.titleLarge
        BlockType.H3 -> t.titleMedium
        BlockType.CODE -> MonoStyle
        BlockType.QUOTE -> t.bodyLarge.copy(fontStyle = FontStyle.Italic)
        else -> t.bodyLarge
    }
    return base.copy(color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun BlockFrame(block: Block, numberIndex: Int, checked: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    val leading: (@Composable () -> Unit)? = when (block.type) {
        BlockType.BULLET -> ({ Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(20.dp)) })
        BlockType.NUMBERED -> ({ Text("$numberIndex.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 20.dp)) })
        BlockType.CHECK -> ({ Checkbox(checked, onToggle, Modifier.size(32.dp)) })
        else -> null
    }
    val bg = when (block.type) {
        BlockType.CALLOUT -> MaterialTheme.colorScheme.secondaryContainer
        BlockType.CODE -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> null
    }
    val vPad = when (block.type) { BlockType.H1 -> 10.dp; BlockType.H2, BlockType.H3 -> 6.dp; else -> 3.dp }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = vPad)
            .then(if (bg != null) Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp) else Modifier.padding(horizontal = 8.dp)),
        verticalAlignment = if (block.type == BlockType.CHECK) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (block.type == BlockType.QUOTE) {
            Box(Modifier.width(3.dp).height(24.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
        }
        if (block.type == BlockType.CALLOUT) { Text("💡", modifier = Modifier.padding(end = 8.dp)) }
        leading?.let { it(); Spacer(Modifier.width(6.dp)) }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun EditBlock(
    block: Block,
    numberIndex: Int,
    checked: Boolean,
    task: TaskEntity?,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onText: (String) -> Unit,
    onBackspaceEmpty: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemind: () -> Unit,
    onOpenTask: (String) -> Unit,
    onRemove: () -> Unit,
) {
    when (block.type) {
        BlockType.DIVIDER -> {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f).padding(vertical = 12.dp))
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove", Modifier.size(18.dp)) }
            }
            return
        }
        BlockType.IMAGE -> {
            ImageBlock(block.image, onRemove)
            return
        }
        else -> {}
    }
    BlockFrame(block, numberIndex, checked, onToggle) {
        Column {
            Box {
                if (block.text.isEmpty()) {
                    Text(
                        when (block.type) {
                            BlockType.H1, BlockType.H2, BlockType.H3 -> "Heading"
                            BlockType.CHECK -> "To-do"
                            BlockType.PARAGRAPH -> "Write, or type # - [] > for blocks"
                            else -> ""
                        },
                        style = blockStyle(block.type).copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                    )
                }
                BasicTextField(
                    block.text, onText,
                    textStyle = blockStyle(block.type).copy(
                        textDecoration = if (block.type == BlockType.CHECK && checked) TextDecoration.LineThrough else null,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) onFocus() }
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && block.text.isEmpty()) { onBackspaceEmpty(); true } else false
                        },
                )
            }
            if (block.type == BlockType.CHECK) CheckMeta(task, onRemind, onOpenTask)
        }
    }
}

@Composable
private fun CheckMeta(task: TaskEntity?, onRemind: () -> Unit, onOpenTask: (String) -> Unit) {
    if (task != null) {
        Row(Modifier.clickable { onOpenTask(task.id) }.padding(vertical = 2.dp)) {
            val due = task.dueAt
            Pill(
                due?.let { Formats.date(it, task.hasTime) } ?: "Reminder",
                if (due != null) dateColor(due, task.hasTime, task.status != TaskStatus.OPEN) else MaterialTheme.colorScheme.primary,
                Icons.Outlined.AddAlarm, filled = true,
            )
        }
    } else {
        Text(
            "+ remind me", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRemind).padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun ImageBlock(name: String?, onRemove: (() -> Unit)?) {
    val vault = LocalContext.current.container.vault
    var bmp by remember(name) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) { bmp = name?.let { withContext(Dispatchers.IO) { vault.bitmap(it) } } }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        bmp?.let {
            Image(
                it.asImageBitmap(), null,
                Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth,
            )
        } ?: Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            ) { Icon(Icons.Outlined.Close, "Remove image", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun ReadBlock(
    block: Block,
    numberIndex: Int,
    checked: Boolean,
    task: TaskEntity?,
    onLink: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    when (block.type) {
        BlockType.DIVIDER -> { HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)); return }
        BlockType.IMAGE -> { ImageBlock(block.image, null); return }
        else -> {}
    }
    if (block.text.isBlank() && block.type == BlockType.PARAGRAPH) { Spacer(Modifier.height(8.dp)); return }
    val linkColor = MaterialTheme.colorScheme.primary
    val styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
    val annotated = remember(block.text, linkColor) {
        buildAnnotatedString {
            var pos = 0
            val text = block.text
            val matches = (NoteBlocks.linkRe.findAll(text).map { it to true } + NoteBlocks.urlRe.findAll(text).map { it to false })
                .sortedBy { it.first.range.first }
            for ((m, isWiki) in matches) {
                if (m.range.first < pos) continue
                append(text.substring(pos, m.range.first))
                if (isWiki) {
                    val target = m.groupValues[1].trim()
                    val label = m.groupValues[2].ifBlank { target }
                    withLink(LinkAnnotation.Clickable(target, styles) { onLink(target) }) { append(label) }
                } else {
                    withLink(LinkAnnotation.Url(m.value, styles)) { append(m.value) }
                }
                pos = m.range.last + 1
            }
            append(text.substring(pos))
            if (block.type == BlockType.CHECK && checked) addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), 0, length)
        }
    }
    BlockFrame(block, numberIndex, checked, onToggle) {
        Column {
            Text(annotated, style = blockStyle(block.type))
            if (block.type == BlockType.CHECK && task != null) CheckMeta(task, {}, onOpenTask)
        }
    }
}
