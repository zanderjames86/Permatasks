package app.permatasks.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.Backup
import app.permatasks.data.BackupData
import app.permatasks.ui.components.GroupCard
import app.permatasks.ui.components.ListRow
import app.permatasks.ui.components.SectionHeader
import app.permatasks.ui.format.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Encrypted backups through Android's file picker: choose a file in Google Drive, OneDrive or
 * on the phone, and the provider app uploads it. Permatasks itself never touches the network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val s = c.settings
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val tick by s.changes.collectAsState()
    var passDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var hasKey by remember { mutableStateOf(c.backup.hasKey) }

    fun runBackup(uri: Uri) = scope.launch {
        status = "Backing up…"
        status = runCatching { withContext(Dispatchers.IO) { c.backup.writeTo(uri) }; "Backup saved" }
            .getOrElse { s.lastBackupError = it.message; "Backup failed: ${it.message}" }
    }

    val chooseAuto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            s.backupUri = uri.toString()
            s.autoBackup = true
            runBackup(uri)
        }
    }
    val exportOnce = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) runBackup(uri)
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Encrypted backup") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                "Backups are encrypted on this phone with AES-256 using a passphrase only you know, then saved wherever you choose — Google Drive, OneDrive, or a folder. The cloud only ever sees unreadable data. Without the passphrase, nobody (including you) can open a backup.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SectionHeader("1 · Passphrase")
            GroupCard {
                ListRow(
                    if (hasKey) "Passphrase set" else "Set a backup passphrase",
                    if (hasKey) "Tap to change. New backups will use the new one." else "Use something long you won't forget — it can't be recovered.",
                    Icons.Outlined.Key, onClick = { passDialog = true }, showChevron = true,
                )
            }
            SectionHeader("2 · Automatic daily backup")
            GroupCard {
                ListRow(
                    if (s.backupUri != null) "Backing up daily" else "Choose backup file",
                    if (s.backupUri != null) "Overwrites the same file every day" + (if (s.lastBackupAt > 0) " · last: ${Formats.date(s.lastBackupAt, true)}" else "")
                    else "Pick Google Drive or OneDrive in the file picker",
                    Icons.Outlined.Schedule,
                    onClick = { if (hasKey) chooseAuto.launch("permatasks-backup.ptbk") else passDialog = true },
                    showChevron = true,
                )
                if (s.backupUri != null) {
                    SwitchRow("Daily backup", null, Icons.Outlined.CloudUpload, s.autoBackup) { s.autoBackup = it }
                    ListRow("Back up now", null, Icons.Outlined.Save, onClick = { runBackup(Uri.parse(s.backupUri)) })
                }
                s.lastBackupError?.let { ListRow("Last backup failed", it, Icons.Outlined.CloudUpload, MaterialTheme.colorScheme.error) }
            }
            SectionHeader("Other")
            GroupCard {
                ListRow("Export a one-off backup", "Saves a new dated file", Icons.Outlined.Save, onClick = {
                    if (hasKey) exportOnce.launch("permatasks-${LocalDate.now()}.ptbk") else passDialog = true
                })
                ListRow("Restore from backup", "Replaces everything on this phone", Icons.Outlined.Restore, MaterialTheme.colorScheme.error, onClick = {
                    pickRestore.launch(arrayOf("*/*"))
                })
            }
            status?.let { Text(it, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.primary) }
        }
    }

    if (passDialog) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passDialog = false },
            title = { Text("Backup passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p1, { p1 = it }, label = { Text("Passphrase (min 8)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(p2, { p2 = it }, label = { Text("Repeat") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), isError = p2.isNotEmpty() && p1 != p2, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(enabled = p1.length >= 8 && p1 == p2, onClick = {
                    val pass = p1
                    passDialog = false
                    scope.launch {
                        status = "Securing passphrase…"
                        withContext(Dispatchers.Default) { c.backup.setPassphrase(pass) }
                        hasKey = true
                        status = "Passphrase set"
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { passDialog = false }) { Text("Cancel") } },
        )
    }

    restoreUri?.let { uri ->
        var pass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Unlock backup") },
            text = {
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Backup passphrase") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), isError = error != null, supportingText = { error?.let { Text(it) } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { c.backup.read(uri, pass) } }
                        r.onSuccess { pendingRestore = it; restoreUri = null }
                            .onFailure { error = if (it is Backup.WrongPassphrase) "Wrong passphrase" else (it.message ?: "Can't read this file") }
                    }
                }) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }
    pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace everything?") },
            text = { Text("Backup from ${Formats.date(data.createdAt, true)}: ${data.tasks.size} tasks, ${data.notes.size} notes, ${data.projects.size} projects. Everything currently on this phone will be replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        status = "Restoring…"
                        withContext(Dispatchers.IO) { c.backup.restore(data) }
                        c.repo.syncGeofences()
                        c.repo.processDueNags()
                        status = "Restored ${data.tasks.size} tasks and ${data.notes.size} notes"
                    }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}
