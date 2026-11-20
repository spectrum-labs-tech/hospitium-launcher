// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher.ui

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
import tv.hospitium.launcher.R
import tv.hospitium.launcher.domain.LauncherState
import tv.hospitium.launcher.domain.Problem
import java.time.Duration
import java.time.Instant

@Composable
fun SetupScreen(state: LauncherState.NeedsServer, onSubmit: (String) -> Unit) {
    var text by rememberSaveable(state.suggested) { mutableStateOf(state.suggested) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(420.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .border(BorderStroke(2.dp, if (focused) Accent else Color.White.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.setup_hint), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.4f))
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
                    modifier = Modifier.focusRequester(field).onFocusChanged { focused = it.isFocused },
                )
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = { onSubmit(text) }) { Text(stringResource(R.string.setup_connect)) }
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFFB4AB))
        }
    }
}

@Composable
fun PairingScreen(state: LauncherState.Pairing, now: Instant, onChangeServer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pairing_body), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(28.dp))
        val code = state.code
        if (code == null) {
            Text(stringResource(R.string.pairing_requesting), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        } else {
            // Spaced, monospaced, large: read from across a room. The alphabet has no 0/O/1/I.
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                for (half in code.chunked(3)) {
                    Text(half, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 72.sp, letterSpacing = 6.sp, color = Accent)
                }
            }
            state.expiresAt?.let { exp ->
                val minutes = Duration.between(now, exp).toMinutes().coerceAtLeast(0) + 1
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pairing_expires, minutes), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
        state.problem?.let {
            Spacer(Modifier.height(12.dp))
            Text(problemText(it), style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFFB4AB))
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.pairing_server, state.serverUrl), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
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
