package com.rombamukiyam.vault.feature.credential

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.domain.model.CategoryTemplates
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.usecase.DraftValidator
import com.rombamukiyam.vault.ui.components.ConfirmationDialog
import com.rombamukiyam.vault.ui.components.Dates
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.PassphraseField
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.categoryLabel
import com.rombamukiyam.vault.ui.components.fieldTypeLabel
import com.rombamukiyam.vault.ui.components.message
import com.rombamukiyam.vault.ui.components.templateLabel

private val reviewOptions = listOf(null, 30, 60, 90, 180, 365)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialFormScreen(
    itemId: String?,
    presetCategory: VaultCategory?,
    presetOwner: String?,
    onDone: () -> Unit,
    onCreated: (String) -> Unit = { onDone() },
    viewModel: CredentialFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.init(itemId, presetCategory, presetOwner) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { viewModel.requestClose() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { e ->
            when (e) {
                is FormEffect.Created -> onCreated(e.id)
                FormEffect.Saved -> onDone()
                FormEffect.Dismissed -> onDone()
            }
        }
    }

    // Default labels for template fields, resolved here (UI layer) and handed to the ViewModel on save.
    val resolvedLabels = remember(state.fields) { mutableMapOf<String, String>() }
    state.fields.forEach { f ->
        f.templateKey?.let { resolvedLabels[it] = templateLabel(it) }
        resolvedLabels["type:${f.type.name}"] = fieldTypeLabel(f.type)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RombaTopBar(stringResource(if (state.isEdit) R.string.form_edit_title else R.string.form_add_title), onBack = viewModel::requestClose) },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OwnerPicker(state, viewModel) }
            item { CategoryPicker(state, viewModel) }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.form_name)) },
                    placeholder = { Text(stringResource(R.string.form_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    isError = state.error is VaultError.ValidationFailed && state.title.isBlank(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.institution,
                    onValueChange = viewModel::setInstitution,
                    label = { Text(stringResource(R.string.form_institution)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
            }

            item { SectionHeader(stringResource(R.string.form_fields_section)) }
            items(state.fields, key = { it.key }) { f -> FieldEditorRow(f, viewModel) }
            item { SecondaryButton(stringResource(R.string.action_add_field), onClick = { viewModel.showAddField(true) }) }

            item {
                OutlinedTextField(
                    value = state.website,
                    onValueChange = viewModel::setWebsite,
                    label = { Text(stringResource(R.string.form_website)) },
                    placeholder = { Text(stringResource(R.string.form_website_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                )
            }

            if (CategoryTemplates.hasPrimaryPassword(state.category)) {
                item { LastChangedPicker(state, viewModel) }
                item { ReviewPolicyPicker(state.reviewDays, viewModel::setReviewDays) }
                item { TwoFactorPicker(state, viewModel) }
            }

            item { ToggleRow(stringResource(R.string.form_high_security), state.highSecurity, viewModel::setHighSecurity) }
            item { ToggleRow(stringResource(R.string.form_favourite), state.favorite, viewModel::setFavorite) }

            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    label = { Text(stringResource(R.string.form_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }

            item {
                state.error?.let { err ->
                    Text(formErrorMessage(err), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    stringResource(R.string.action_save_securely),
                    onClick = { viewModel.save(resolvedLabels.toMap()) },
                    loading = state.saving,
                    enabled = state.title.isNotBlank() && state.ownerId != null,
                )
            }
        }
    }

    if (state.showDiscardDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.form_discard_title),
            text = stringResource(R.string.form_discard_body),
            confirmText = stringResource(R.string.action_discard),
            dismissText = stringResource(R.string.action_keep_editing),
            onConfirm = viewModel::discard,
            onDismiss = viewModel::keepEditing,
            destructive = true,
        )
    }
    if (state.showAddField) AddFieldDialog(onPick = viewModel::addField, onDismiss = { viewModel.showAddField(false) })
}

@Composable
private fun formErrorMessage(err: VaultError): String = when (err) {
    is VaultError.ValidationFailed -> when (err.messages.firstOrNull()) {
        DraftValidator.ERR_TITLE_REQUIRED -> stringResource(R.string.form_err_title_required)
        DraftValidator.ERR_OWNER_REQUIRED -> stringResource(R.string.form_err_owner_required)
        DraftValidator.ERR_DISALLOWED_FIELD -> stringResource(R.string.form_err_disallowed)
        DraftValidator.ERR_FIELD_LABEL_REQUIRED -> stringResource(R.string.form_err_field_label)
        else -> stringResource(R.string.err_validation)
    }
    else -> err.message()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerPicker(state: FormUiState, vm: CredentialFormViewModel) {
    Text(stringResource(R.string.form_owner), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.profiles.forEach { p ->
            FilterChip(selected = state.ownerId == p.id, onClick = { vm.setOwner(p.id) }, label = { Text(p.displayName) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(state: FormUiState, vm: CredentialFormViewModel) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!state.isEdit) expanded = it }) {
        OutlinedTextField(
            value = categoryLabel(state.category),
            onValueChange = {},
            readOnly = true,
            enabled = !state.isEdit,
            label = { Text(stringResource(R.string.form_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VaultCategory.entries.forEach { c ->
                DropdownMenuItem(text = { Text(categoryLabel(c)) }, onClick = { vm.setCategory(c); expanded = false })
            }
        }
    }
}

@Composable
private fun FieldEditorRow(f: FieldEditor, vm: CredentialFormViewModel) {
    val defaultLabel = f.templateKey?.let { templateLabel(it) } ?: fieldTypeLabel(f.type)
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = f.label,
                    onValueChange = { vm.setFieldLabel(f.key, it) },
                    placeholder = { Text(defaultLabel) },
                    label = { Text(stringResource(R.string.form_field_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = CategoryTemplates.isDisallowedLabel(f.label),
                )
                IconButton(onClick = { vm.moveField(f.key, up = true) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.ArrowUpward, contentDescription = null) }
                IconButton(onClick = { vm.moveField(f.key, up = false) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.ArrowDownward, contentDescription = null) }
                IconButton(onClick = { vm.removeField(f.key) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove)) }
            }
            Spacer(Modifier.height(8.dp))
            if (f.isSecret) {
                PassphraseField(
                    value = f.value,
                    onValueChange = { vm.setFieldValue(f.key, it) },
                    label = stringResource(R.string.form_field_value),
                    imeAction = ImeAction.Next,
                )
                if (f.type in CategoryTemplates.primaryPasswordTypes || f.type == FieldType.PROFILE_PASSWORD || f.type == FieldType.TRANSACTION_PASSWORD || f.type == FieldType.CUSTOM_SECRET) {
                    GeneratePasswordButton(onGenerated = { vm.applyGenerated(f.key, it) })
                }
            } else {
                OutlinedTextField(
                    value = f.value,
                    onValueChange = { vm.setFieldValue(f.key, it) },
                    label = { Text(stringResource(R.string.form_field_value)) },
                    singleLine = f.type != FieldType.RECOVERY_NOTES && f.type != FieldType.SECURE_NOTE,
                    minLines = if (f.type == FieldType.RECOVERY_NOTES || f.type == FieldType.SECURE_NOTE) 3 else 1,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (f.type) {
                            FieldType.PHONE, FieldType.RECOVERY_PHONE -> KeyboardType.Phone
                            FieldType.EMAIL, FieldType.RECOVERY_EMAIL -> KeyboardType.Email
                            FieldType.CARD_LAST4 -> KeyboardType.Number
                            FieldType.URL -> KeyboardType.Uri
                            else -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.setFieldSecret(f.key, !f.isSecret) }) {
                Checkbox(checked = f.isSecret, onCheckedChange = { vm.setFieldSecret(f.key, it) })
                Text(stringResource(R.string.form_field_secret), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Replaced with the real generator sheet in Phase 3; kept as a seam so the form does not change. */
@Composable
fun GeneratePasswordButton(onGenerated: (String) -> Unit) {
    PasswordGeneratorEntry(onGenerated = onGenerated)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LastChangedPicker(state: FormUiState, vm: CredentialFormViewModel) {
    var show by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(stringResource(R.string.form_last_changed), style = MaterialTheme.typography.labelLarge)
            Text(Dates.format(state.lastChangedAt) ?: stringResource(R.string.form_not_set), style = MaterialTheme.typography.bodyLarge)
        }
        Row {
            LinkButton(stringResource(R.string.form_pick_date), onClick = { show = true })
            if (state.lastChangedAt != null) LinkButton(stringResource(R.string.form_clear_date), onClick = { vm.setLastChanged(null) })
        }
    }
    if (show) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.lastChangedAt)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { vm.setLastChanged(pickerState.selectedDateMillis); show = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPolicyPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    Text(stringResource(R.string.form_review_policy), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reviewOptions.forEach { d ->
            FilterChip(
                selected = selected == d,
                onClick = { onSelect(d) },
                label = { Text(if (d == null) stringResource(R.string.review_none) else stringResource(R.string.review_days, d)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoFactorPicker(state: FormUiState, vm: CredentialFormViewModel) {
    Text(stringResource(R.string.detail_two_factor), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.twoFactor == true, onClick = { vm.setTwoFactor(true) }, label = { Text(stringResource(R.string.detail_two_factor_on)) })
        FilterChip(selected = state.twoFactor == false, onClick = { vm.setTwoFactor(false) }, label = { Text(stringResource(R.string.detail_two_factor_off)) })
        FilterChip(selected = state.twoFactor == null, onClick = { vm.setTwoFactor(null) }, label = { Text(stringResource(R.string.detail_two_factor_unknown)) })
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AddFieldDialog(onPick: (FieldType) -> Unit, onDismiss: () -> Unit) {
    val choices = listOf(
        FieldType.USERNAME, FieldType.PASSWORD, FieldType.EMAIL, FieldType.PHONE, FieldType.CUSTOMER_ID, FieldType.CIF, FieldType.CRN,
        FieldType.ACCOUNT_REFERENCE, FieldType.MEMBERSHIP_ID, FieldType.SECURITY_QUESTION, FieldType.SECURITY_ANSWER,
        FieldType.RECOVERY_EMAIL, FieldType.RECOVERY_PHONE, FieldType.RECOVERY_CODE, FieldType.RECOVERY_NOTES,
        FieldType.EXPIRY_DATE, FieldType.ISSUE_DATE, FieldType.REFERENCE_NUMBER, FieldType.URL, FieldType.TEXT, FieldType.CUSTOM, FieldType.CUSTOM_SECRET,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.form_add_field_type), style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(choices) { t ->
                    Text(
                        fieldTypeLabel(t),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
