package ir.andarz.app.ui.platform

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.safeDrawingPadding

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ir.andarz.app.platform.AppLock
import ir.andarz.app.platform.PinResult
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.settings.LockMethod
import ir.andarz.data.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The lock screen of 8.2: nothing of the journal is composed behind it. Biometric (strong) with the PIN as
 * fallback, or PIN only. The wait after five wrong PINs survives restarts (D-33).
 */
@Composable
fun LockScreen(lock: AppLock, s: Settings) {
    val u = ui()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var waitLeft by remember { mutableStateOf(0L) }
    LaunchedEffect(s.pinLockedUntil) {
        while (true) {
            waitLeft = (s.pinLockedUntil - System.currentTimeMillis()).coerceAtLeast(0)
            if (waitLeft == 0L) break
            delay(1_000)
        }
    }
    val activity = ctx as? FragmentActivity
    val canBio = activity != null && s.lockMethod == LockMethod.BIOMETRIC &&
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    fun prompt() {
        val a = activity ?: return
        val p = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { lock.unlockByBiometric() }
        })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(u.t("set.lock")).setNegativeButtonText(u.t("set.lock.pin"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
        )
    }
    LaunchedEffect(canBio) { if (canBio) prompt() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Icon(Icons.Filled.Lock, contentDescription = u.t("a11y.lockedState"), tint = LocalAndarzColors.current.info)
        Text(u.content.appName, style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = pin, onValueChange = { v -> if (v.length <= 6 && v.all(Char::isDigit)) pin = v },
            label = { Text(u.t("set.lock.pin")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), enabled = waitLeft == 0L,
            modifier = Modifier.fillMaxWidth(),
        )
        if (waitLeft > 0) Text(u.t("set.lock.pin.wait", "s" to u.int(((waitLeft + 999) / 1000).toInt())), color = LocalAndarzColors.current.caution)
        message?.let { Text(it, color = LocalAndarzColors.current.redline) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canBio) OutlinedButton(onClick = { prompt() }) { Text(u.t("set.lock.biometric")) }
            Button(onClick = {
                scope.launch {
                    when (val r = lock.tryPin(pin)) {
                        PinResult.Ok -> message = null
                        is PinResult.Wrong -> { pin = ""; message = u.t("set.lock.pin.wrong") + if (r.triesLeft > 0) " \u00B7 " + u.t("set.lock.pin.tries", "n" to u.int(r.triesLeft)) else "" }
                        is PinResult.Wait -> message = null
                    }
                }
            }, enabled = pin.length >= 4 && waitLeft == 0L) { Text(u.t("common.ok")) }
        }
    }
}
