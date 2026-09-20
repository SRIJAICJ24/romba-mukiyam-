package com.rombamukiyam.vault.feature.credential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.crypto.GeneratorMode
import com.rombamukiyam.vault.core.crypto.GeneratorOptions
import com.rombamukiyam.vault.domain.usecase.GeneratePasswordUseCase
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor(private val generate: GeneratePasswordUseCase) : ViewModel() {
    fun generate(o: GeneratorOptions): String = generate.invoke(o)
    fun bits(o: GeneratorOptions): Double = generate.estimateBits(o)
}

/** Button that opens the generator sheet; on Use the generated value is handed back and cleared here. */
@Composable
fun PasswordGeneratorEntry(onGenerated: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LinkButton(stringResource(R.string.form_generate), onClick = { open = true })
    if (open) {
        PasswordGeneratorSheet(onUse = { onGenerated(it); open = false }, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorSheet(onUse: (String) -> Unit, onDismiss: () -> Unit, viewModel: PasswordGeneratorViewModel = hiltViewModel()) {
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var value by remember { mutableStateOf("") }
    LaunchedEffect(options) { value = viewModel.generate(options) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.gen_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GeneratorMode.entries.forEach { m ->
                    FilterChip(selected = options.mode == m, onClick = { options = options.copy(mode = m) }, label = { Text(modeLabel(m)) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                    IconButton(onClick = { value = viewModel.generate(options) }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.gen_regenerate)) }
                }
            }
            Text(stringResource(R.string.gen_bits, viewModel.bits(options).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))

            when (options.mode) {
                GeneratorMode.RANDOM -> {
                    Text(stringResource(R.string.gen_length, options.length), style = MaterialTheme.typography.labelLarge)
                    Slider(value = options.length.toFloat(), onValueChange = { options = options.copy(length = it.toInt()) }, valueRange = 8f..40f, steps = 31)
                    OptionCheck(stringResource(R.string.gen_uppercase), options.uppercase) { options = options.copy(uppercase = it) }
                    OptionCheck(stringResource(R.string.gen_lowercase), options.lowercase) { options = options.copy(lowercase = it) }
                    OptionCheck(stringResource(R.string.gen_digits), options.digits) { options = options.copy(digits = it) }
                    OptionCheck(stringResource(R.string.gen_symbols), options.symbols) { options = options.copy(symbols = it) }
                    OptionCheck(stringResource(R.string.gen_avoid_ambiguous), options.avoidAmbiguous) { options = options.copy(avoidAmbiguous = it) }
                    if (options.symbols) {
                        OutlinedTextField(
                            value = options.customSymbols ?: "",
                            onValueChange = { options = options.copy(customSymbols = it.take(30)) },
                            label = { Text(stringResource(R.string.gen_custom_symbols)) },
                            placeholder = { Text(GeneratorOptions.DEFAULT_SYMBOLS) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                GeneratorMode.PASSPHRASE -> {
                    Text(stringResource(R.string.gen_words, options.wordCount), style = MaterialTheme.typography.labelLarge)
                    Slider(value = options.wordCount.toFloat(), onValueChange = { options = options.copy(wordCount = it.toInt()) }, valueRange = 3f..8f, steps = 4)
                    OptionCheck(stringResource(R.string.gen_capitalise), options.capitaliseWords) { options = options.copy(capitaliseWords = it) }
                    OptionCheck(stringResource(R.string.gen_append_digits), options.appendDigit) { options = options.copy(appendDigit = it) }
                }
                GeneratorMode.MEMORABLE -> {
                    OptionCheck(stringResource(R.string.gen_symbols), options.symbols) { options = options.copy(symbols = it) }
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.gen_use), onClick = { onUse(value); value = "" }, enabled = value.isNotEmpty())
        }
    }
}

@Composable
private fun OptionCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun modeLabel(m: GeneratorMode): String = stringResource(
    when (m) {
        GeneratorMode.RANDOM -> R.string.gen_mode_random
        GeneratorMode.MEMORABLE -> R.string.gen_mode_memorable
        GeneratorMode.PASSPHRASE -> R.string.gen_mode_passphrase
    },
)
