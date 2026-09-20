package com.rombamukiyam.vault.feature.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.ArchiveFamilyProfileUseCase
import com.rombamukiyam.vault.domain.usecase.CreateFamilyProfileUseCase
import com.rombamukiyam.vault.domain.usecase.UpdateFamilyProfileUseCase
import com.rombamukiyam.vault.feature.onboarding.ColorPicker
import com.rombamukiyam.vault.feature.onboarding.RelationshipChips
import com.rombamukiyam.vault.feature.onboarding.relationshipLabel
import com.rombamukiyam.vault.ui.components.ConfirmationDialog
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.ProfileAvatar
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.message
import com.rombamukiyam.vault.ui.theme.ProfileColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditor(
    val id: String? = null,
    val name: String = "",
    val relationship: Relationship = Relationship.DAD,
    val color: Long = ProfileColors.first(),
    val busy: Boolean = false,
    val error: VaultError? = null,
)

data class FamilyUiState(
    val active: List<FamilyProfile> = emptyList(),
    val archived: List<FamilyProfile> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val editor: ProfileEditor? = null,
    val archiveCandidate: FamilyProfile? = null,
)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    family: FamilyRepository,
    vault: VaultRepository,
    private val create: CreateFamilyProfileUseCase,
    private val update: UpdateFamilyProfileUseCase,
    private val archive: ArchiveFamilyProfileUseCase,
) : ViewModel() {

    private val ui = MutableStateFlow(FamilyUiState())

    val state: StateFlow<FamilyUiState> = combine(family.observeAll(), vault.observeActive(), ui) { profiles, items, local ->
        local.copy(
            active = profiles.filterNot { it.archived },
            archived = profiles.filter { it.archived },
            counts = items.groupingBy { it.ownerId }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyUiState())

    fun startAdd() = ui.update { it.copy(editor = ProfileEditor()) }
    fun startEdit(p: FamilyProfile) = ui.update { it.copy(editor = ProfileEditor(p.id, p.displayName, p.relationship, p.avatarColor)) }
    fun closeEditor() = ui.update { it.copy(editor = null) }
    fun editName(v: String) = ui.update { it.copy(editor = it.editor?.copy(name = v.take(40), error = null)) }
    fun editRelationship(r: Relationship) = ui.update { it.copy(editor = it.editor?.copy(relationship = r)) }
    fun editColor(c: Long) = ui.update { it.copy(editor = it.editor?.copy(color = c)) }

    fun save() {
        val e = ui.value.editor ?: return
        if (e.busy) return
        ui.update { it.copy(editor = e.copy(busy = true)) }
        viewModelScope.launch {
            val result = if (e.id == null) {
                create(e.name, e.relationship, e.color).map { }
            } else {
                val existing = state.value.active.plus(state.value.archived).firstOrNull { it.id == e.id }
                if (existing == null) Outcome.Failure(VaultError.UnknownSafeFailure()) else update(existing.copy(displayName = e.name.trim(), relationship = e.relationship, avatarColor = e.color))
            }
            when (result) {
                is Outcome.Success -> ui.update { it.copy(editor = null) }
                is Outcome.Failure -> ui.update { it.copy(editor = e.copy(busy = false, error = result.error)) }
            }
        }
    }

    fun askArchive(p: FamilyProfile) = ui.update { it.copy(archiveCandidate = p) }
    fun cancelArchive() = ui.update { it.copy(archiveCandidate = null) }
    fun confirmArchive() {
        val p = ui.value.archiveCandidate ?: return
        ui.update { it.copy(archiveCandidate = null) }
        viewModelScope.launch { archive(p.id, true) }
    }
    fun restore(p: FamilyProfile) = viewModelScope.launch { archive(p.id, false) }
}

@Composable
fun FamilyScreen(safeMode: Boolean, onOpenOwner: (String) -> Unit, viewModel: FamilyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RombaTopBar(stringResource(R.string.family_title)) },
        floatingActionButton = {
            if (!safeMode) {
                ExtendedFloatingActionButton(onClick = viewModel::startAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.family_add)) })
            }
        },
    ) { padding ->
        if (state.active.isEmpty() && state.archived.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.family_empty_title),
                body = stringResource(R.string.family_empty_body),
                icon = Icons.Filled.People,
                actionText = if (safeMode) null else stringResource(R.string.family_add),
                onAction = viewModel::startAdd,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
                items(state.active, key = { it.id }) { p ->
                    ProfileRow(p, state.counts[p.id] ?: 0, safeMode, onOpen = { onOpenOwner(p.id) }, onEdit = { viewModel.startEdit(p) }, onArchive = { viewModel.askArchive(p) }, onRestore = null)
                }
                if (state.archived.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.family_archived_section)) }
                    items(state.archived, key = { it.id }) { p ->
                        ProfileRow(p, state.counts[p.id] ?: 0, safeMode, onOpen = { onOpenOwner(p.id) }, onEdit = { viewModel.startEdit(p) }, onArchive = null, onRestore = { viewModel.restore(p) })
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        ProfileEditorDialog(editor, viewModel)
    }
    state.archiveCandidate?.let {
        ConfirmationDialog(
            title = stringResource(R.string.family_archive_title),
            text = stringResource(R.string.family_archive_body),
            confirmText = stringResource(R.string.family_archive),
            onConfirm = viewModel::confirmArchive,
            onDismiss = viewModel::cancelArchive,
        )
    }
}

@Composable
private fun ProfileRow(
    p: FamilyProfile,
    count: Int,
    safeMode: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (() -> Unit)?,
    onRestore: (() -> Unit)?,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(p, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("${relationshipLabel(p.relationship)} · ${stringResource(R.string.family_owns_items, count)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!safeMode) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    LinkButton(stringResource(R.string.action_edit), onClick = onEdit)
                    if (onArchive != null) LinkButton(stringResource(R.string.family_archive), onClick = onArchive)
                    if (onRestore != null) LinkButton(stringResource(R.string.family_restore), onClick = onRestore)
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(editor: ProfileEditor, vm: FamilyViewModel) {
    AlertDialog(
        onDismissRequest = vm::closeEditor,
        title = { Text(stringResource(if (editor.id == null) R.string.family_add else R.string.family_edit), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = vm::editName,
                    label = { Text(stringResource(R.string.label_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    isError = editor.error != null,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.label_relationship), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                RelationshipChips(editor.relationship, vm::editRelationship)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                ColorPicker(editor.color, vm::editColor)
                editor.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(if (it is VaultError.ValidationFailed) stringResource(R.string.form_err_name_required) else it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::save, enabled = editor.name.isNotBlank() && !editor.busy) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = vm::closeEditor) { Text(stringResource(R.string.action_cancel)) } },
    )
}
