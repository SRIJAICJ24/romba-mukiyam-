package com.rombamukiyam.vault.feature.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.domain.model.CategoryTemplates
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.FieldDraft
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItemDraft
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.usecase.CreateVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.GetVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.UpdateVaultItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable field row. [key] is a stable UI key; [id] is the persisted id (null for new fields). */
data class FieldEditor(
    val key: String = UUID.randomUUID().toString(),
    val id: String? = null,
    val type: FieldType,
    val label: String,
    /** Template label key when the label has not been customised (resolved in the UI). */
    val templateKey: String? = null,
    val value: String = "",
    val isSecret: Boolean = type.secretByDefault,
    val isSensitive: Boolean = type.sensitiveByDefault,
)

data class FormUiState(
    val isEdit: Boolean = false,
    val itemId: String? = null,
    val profiles: List<FamilyProfile> = emptyList(),
    val ownerId: String? = null,
    val category: VaultCategory = VaultCategory.BANK,
    val title: String = "",
    val institution: String = "",
    val website: String = "",
    val highSecurity: Boolean = true,
    val favorite: Boolean = false,
    val twoFactor: Boolean? = null,
    val lastChangedAt: Long? = null,
    val reviewDays: Int? = 180,
    val notes: String = "",
    val fields: List<FieldEditor> = emptyList(),
    val tags: String = "",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
    val error: VaultError? = null,
    val showDiscardDialog: Boolean = false,
    val showAddField: Boolean = false,
)

sealed class FormEffect {
    data class Created(val id: String) : FormEffect()
    data object Saved : FormEffect()
    data object Dismissed : FormEffect()
}

@HiltViewModel
class CredentialFormViewModel @Inject constructor(
    private val family: FamilyRepository,
    private val settings: SettingsRepository,
    private val getItem: GetVaultItemUseCase,
    private val createItem: CreateVaultItemUseCase,
    private val updateItem: UpdateVaultItemUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FormUiState())
    val state: StateFlow<FormUiState> = _state.asStateFlow()
    private val effects = Channel<FormEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()
    private var initialised = false

    fun init(itemId: String?, presetCategory: VaultCategory?, presetOwner: String?) {
        if (initialised) return
        initialised = true
        viewModelScope.launch {
            val profiles = family.observeActive().first()
            val defaults = settings.settings.first()
            if (itemId == null) {
                val category = presetCategory ?: VaultCategory.BANK
                _state.update {
                    it.copy(
                        profiles = profiles,
                        ownerId = presetOwner ?: profiles.firstOrNull()?.id,
                        category = category,
                        highSecurity = category.defaultHighSecurity,
                        reviewDays = if (CategoryTemplates.hasPrimaryPassword(category)) defaults.defaultReviewDays else null,
                        fields = templateFields(category),
                        loaded = true,
                    )
                }
            } else {
                val item = getItem(itemId).getOrNull()
                if (item == null) {
                    _state.update { it.copy(loaded = true, error = VaultError.UnknownSafeFailure()) }
                    return@launch
                }
                _state.update {
                    it.copy(
                        isEdit = true,
                        itemId = item.id,
                        profiles = profiles,
                        ownerId = item.ownerId,
                        category = item.category,
                        title = item.title,
                        institution = item.institution ?: "",
                        website = item.officialWebsite ?: "",
                        highSecurity = item.highSecurity,
                        favorite = item.favorite,
                        twoFactor = item.twoFactorEnabled,
                        lastChangedAt = item.passwordLastChangedAt,
                        reviewDays = item.reviewIntervalDays,
                        notes = item.notes ?: "",
                        fields = item.fields.map { f -> FieldEditor(id = f.id, type = f.type, label = f.label, value = f.value, isSecret = f.isSecret, isSensitive = f.isSensitive) },
                        tags = item.tags.joinToString(", "),
                        loaded = true,
                    )
                }
            }
        }
    }

    private fun templateFields(category: VaultCategory): List<FieldEditor> =
        CategoryTemplates.fieldsFor(category).map { FieldEditor(type = it.type, label = "", templateKey = it.labelKey) }

    private fun edit(block: (FormUiState) -> FormUiState) {
        _state.update { block(it).copy(dirty = true, error = null) }
        // Debug-only: lengths, never values.
        com.rombamukiyam.vault.core.common.SafeLog.d("Form", "edit titleLen=${_state.value.title.length} fields=${_state.value.fields.size}")
    }

    fun setOwner(id: String) = edit { it.copy(ownerId = id) }
    fun setCategory(c: VaultCategory) = edit {
        // Switching category on a new item re-applies the template but keeps any values already typed.
        val typed = it.fields.filter { f -> f.value.isNotBlank() }
        val template = templateFields(c).filter { t -> typed.none { f -> f.type == t.type } }
        it.copy(category = c, highSecurity = c.defaultHighSecurity, fields = if (it.isEdit) it.fields else typed + template)
    }
    fun setTitle(v: String) = edit { it.copy(title = v.take(80)) }
    fun setInstitution(v: String) = edit { it.copy(institution = v.take(80)) }
    fun setWebsite(v: String) = edit { it.copy(website = v.take(200)) }
    fun setHighSecurity(v: Boolean) = edit { it.copy(highSecurity = v) }
    fun setFavorite(v: Boolean) = edit { it.copy(favorite = v) }
    fun setTwoFactor(v: Boolean?) = edit { it.copy(twoFactor = v) }
    fun setLastChanged(millis: Long?) = edit { it.copy(lastChangedAt = millis) }
    fun setReviewDays(days: Int?) = edit { it.copy(reviewDays = days) }
    fun setNotes(v: String) = edit { it.copy(notes = v.take(4000)) }
    fun setTags(v: String) = edit { it.copy(tags = v.take(200)) }

    fun setFieldValue(key: String, v: String) = edit { s -> s.copy(fields = s.fields.map { if (it.key == key) it.copy(value = v.take(4000)) else it }) }
    fun setFieldLabel(key: String, v: String) = edit { s -> s.copy(fields = s.fields.map { if (it.key == key) it.copy(label = v.take(60), templateKey = null) else it }) }
    fun setFieldSecret(key: String, v: Boolean) = edit { s -> s.copy(fields = s.fields.map { if (it.key == key) it.copy(isSecret = v) else it }) }
    fun removeField(key: String) = edit { s -> s.copy(fields = s.fields.filterNot { it.key == key }) }
    fun moveField(key: String, up: Boolean) = edit { s ->
        val idx = s.fields.indexOfFirst { it.key == key }
        val target = if (up) idx - 1 else idx + 1
        if (idx < 0 || target !in s.fields.indices) s else s.copy(fields = s.fields.toMutableList().apply { add(target, removeAt(idx)) })
    }
    fun showAddField(show: Boolean) = _state.update { it.copy(showAddField = show) }
    fun addField(type: FieldType) = edit { it.copy(fields = it.fields + FieldEditor(type = type, label = ""), showAddField = false) }

    /** Called by the Generate button with a freshly generated password for the given field. */
    fun applyGenerated(key: String, password: String) = setFieldValue(key, password)

    fun requestClose() {
        if (_state.value.dirty) _state.update { it.copy(showDiscardDialog = true) } else viewModelScope.launch { effects.send(FormEffect.Dismissed) }
    }
    fun keepEditing() = _state.update { it.copy(showDiscardDialog = false) }
    fun discard() {
        _state.update { FormUiState() }
        viewModelScope.launch { effects.send(FormEffect.Dismissed) }
    }

    fun save(resolvedLabels: Map<String, String>) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        val draft = VaultItemDraft(
            id = s.itemId,
            ownerId = s.ownerId ?: "",
            category = s.category,
            title = s.title,
            institution = s.institution.ifBlank { null },
            officialWebsite = s.website.ifBlank { null },
            favorite = s.favorite,
            highSecurity = s.highSecurity,
            notes = s.notes.ifBlank { null },
            passwordLastChangedAt = s.lastChangedAt,
            reviewIntervalDays = s.reviewDays,
            twoFactorEnabled = s.twoFactor,
            fields = s.fields.map { f ->
                FieldDraft(
                    id = f.id,
                    type = f.type,
                    label = f.label.ifBlank { f.templateKey?.let { resolvedLabels[it] } ?: resolvedLabels["type:${f.type.name}"] ?: f.type.name },
                    value = f.value,
                    isSecret = f.isSecret,
                    isSensitive = f.isSensitive,
                )
            },
            tags = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
        viewModelScope.launch {
            if (s.isEdit) {
                when (val r = updateItem(draft)) {
                    is Outcome.Success -> { clearSensitive(); effects.send(FormEffect.Saved) }
                    is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
                }
            } else {
                when (val r = createItem(draft)) {
                    is Outcome.Success -> { clearSensitive(); effects.send(FormEffect.Created(r.value)) }
                    is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
                }
            }
        }
    }

    /** After a successful save nothing sensitive should remain in UI state. */
    private fun clearSensitive() = _state.update { it.copy(fields = emptyList(), notes = "", dirty = false, saving = false) }
}
