package com.rombamukiyam.vault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.crypto.StrengthHint
import com.rombamukiyam.vault.core.crypto.StrengthLevel
import com.rombamukiyam.vault.core.crypto.StrengthReport
import com.rombamukiyam.vault.ui.theme.RombaColors

/**
 * Password / passphrase entry. Visibility state is a plain `remember` (never saveable) and the value itself
 * is owned by the ViewModel so it is not written to instance state either.
 */
@Composable
fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    strength: StrengthReport? = null,
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (visible) R.string.action_hide else R.string.action_reveal),
                    )
                }
            },
            supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        )
        if (strength != null && value.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            StrengthMeter(strength)
        }
    }
}

@Composable
fun StrengthMeter(report: StrengthReport, modifier: Modifier = Modifier) {
    val s = RombaColors.semantic
    val (fraction, color, labelRes) = when (report.level) {
        StrengthLevel.VERY_WEAK -> Triple(0.15f, s.critical, R.string.strength_very_weak)
        StrengthLevel.WEAK -> Triple(0.35f, s.attention, R.string.strength_weak)
        StrengthLevel.FAIR -> Triple(0.6f, s.attention, R.string.strength_fair)
        StrengthLevel.STRONG -> Triple(0.8f, s.safe, R.string.strength_strong)
        StrengthLevel.VERY_STRONG -> Triple(1f, s.safe, R.string.strength_very_strong)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, color = color)
        }
        report.hints.take(2).forEach { hint ->
            Text(
                stringResource(hint.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun StrengthHint.labelRes(): Int = when (this) {
    StrengthHint.TOO_SHORT -> R.string.hint_too_short
    StrengthHint.ADD_WORDS -> R.string.hint_add_words
    StrengthHint.REPEATED_CHARS -> R.string.hint_repeated
    StrengthHint.SEQUENCE -> R.string.hint_sequence
    StrengthHint.COMMON_WORD -> R.string.hint_common_word
    StrengthHint.ONLY_ONE_CHARSET -> R.string.hint_one_charset
    StrengthHint.KEYBOARD_PATTERN -> R.string.hint_keyboard
}
