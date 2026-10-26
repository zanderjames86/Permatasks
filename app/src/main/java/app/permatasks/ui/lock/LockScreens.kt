package app.permatasks.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.permatasks.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun biometricAvailable(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

/** Fingerprint or face (both are "strong" biometrics on the Pixel 8 Pro). */
fun promptBiometric(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("Fingerprint or face")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info)
}

/** First run: choose a PIN, then optionally enable biometrics. */
@Composable
fun SetupPinScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val activity = context as FragmentActivity
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    PinPad(
        title = if (first == null) "Choose a PIN for Permatasks" else "Enter the PIN again",
        subtitle = error ?: "At least 4 digits. It protects the app on every start.",
        isError = error != null,
        onSubmit = { pin ->
            when {
                pin.length < 4 -> error = "Use at least 4 digits"
                first == null -> { first = pin; error = null }
                first != pin -> { first = null; error = "PINs didn't match, start again" }
                else -> scope.launch {
                    withContext(Dispatchers.Default) { c.pins.set(pin) }
                    if (biometricAvailable(activity)) {
                        promptBiometric(activity, "Enable fingerprint / face unlock") {
                            c.settings.biometricEnabled = true
                        }
                    }
                    c.lock.unlock()
                    onDone()
                }
            }
        },
    )
}

@Composable
fun LockScreen() {
    val context = LocalContext.current
    val c = context.container
    val activity = context as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canBio = c.settings.biometricEnabled && biometricAvailable(activity)
    val unlock = { c.lock.unlock() }

    LaunchedEffect(Unit) {
        if (canBio) promptBiometric(activity, "Unlock Permatasks", unlock)
    }

    PinPad(
        title = "Permatasks is locked",
        subtitle = error ?: "Enter your PIN",
        isError = error != null,
        busy = checking,
        extra = if (canBio) ({ IconButton(onClick = { promptBiometric(activity, "Unlock Permatasks", unlock) }) {
            Icon(Icons.Outlined.Fingerprint, "Use fingerprint or face", Modifier.size(40.dp))
        } }) else null,
        onSubmit = { pin ->
            checking = true
            scope.launch {
                val ok = withContext(Dispatchers.Default) { c.pins.verify(pin) }
                checking = false
                if (ok) {
                    unlock()
                } else {
                    val wait = c.pins.lockoutRemaining()
                    error = if (wait > 0) "Too many attempts. Try again in ${wait / 1000 + 1}s" else "Wrong PIN"
                }
            }
        },
    )
}

@Composable
fun PinPad(
    title: String,
    subtitle: String,
    isError: Boolean,
    busy: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(if (pin.isEmpty()) " " else "●".repeat(pin.length), fontSize = 28.sp, letterSpacing = 6.sp)
            Spacer(Modifier.height(24.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { key ->
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = {
                                when (key) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> { onSubmit(pin); pin = "" }
                                    else -> if (pin.length < 12) pin += key
                                }
                            },
                            modifier = Modifier.size(76.dp),
                        ) {
                            if (key == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete") else Text(key, fontSize = 22.sp)
                        }
                    }
                }
            }
            extra?.let { Spacer(Modifier.height(16.dp)); it() }
        }
    }
}
