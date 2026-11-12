// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import tech.spectrumlabs.hospitium.launcher.R
import tech.spectrumlabs.hospitium.launcher.domain.LauncherState
import tech.spectrumlabs.hospitium.launcher.domain.Problem
import java.time.Duration
import java.time.Instant

@Composable
fun SetupScreen(state: LauncherState.NeedsServer, onSubmit: (String) -> Unit) {
    var text by rememberSaveable(state.suggested) { mutableStateOf(state.suggested) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 72.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(640.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(2.dp, if (focused) Accent else Color.White.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.setup_hint), style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.4f))
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
                    modifier = Modifier.focusRequester(field).onFocusChanged { focused = it.isFocused },
                )
            }
            Spacer(Modifier.width(24.dp))
            Button(onClick = { onSubmit(text) }) { Text(stringResource(R.string.setup_connect)) }
        }
        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB4AB))
        }
    }
}

@Composable
fun PairingScreen(state: LauncherState.Pairing, now: Instant, onChangeServer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.pairing_body), style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(48.dp))
        val code = state.code
        if (code == null) {
            Text(stringResource(R.string.pairing_requesting), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        } else {
            // Spaced, monospaced, large: read from across a room. The alphabet has no 0/O/1/I.
            Text(
                code.chunked(3).joinToString("  "),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 120.sp,
                letterSpacing = 16.sp,
                color = Accent,
            )
            state.expiresAt?.let { exp ->
                val minutes = Duration.between(now, exp).toMinutes().coerceAtLeast(0) + 1
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.pairing_expires, minutes), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
        state.problem?.let {
            Spacer(Modifier.height(24.dp))
            Text(problemText(it), style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB4AB))
        }
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.pairing_server, state.serverUrl), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onChangeServer) { Text(stringResource(R.string.pairing_change_server)) }
    }
}

@Composable
fun problemText(p: Problem): String = stringResource(
    when (p) {
        Problem.OFFLINE -> R.string.problem_offline
        Problem.RATE_LIMITED -> R.string.problem_rate_limited
        Problem.SERVER -> R.string.problem_server
        Problem.TOKEN_ALREADY_TAKEN -> R.string.problem_token_taken
    },
)
