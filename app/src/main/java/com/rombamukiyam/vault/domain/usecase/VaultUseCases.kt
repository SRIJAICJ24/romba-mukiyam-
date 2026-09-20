package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.domain.model.CategoryTemplates
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.domain.model.VaultItemDraft
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Shared validation for create/update. Returns user-facing message keys (resolved by the UI). */
object DraftValidator {
    const val ERR_TITLE_REQUIRED = "title_required"
    const val ERR_OWNER_REQUIRED = "owner_required"
    const val ERR_DISALLOWED_FIELD = "disallowed_field"
    const val ERR_FIELD_LABEL_REQUIRED = "field_label_required"
    const val MAX_TITLE = 80
    const val MAX_FIELD_VALUE = 4000
    const val MAX_NOTES = 4000

    fun validate(draft: VaultItemDraft): List<String> {
        val errors = mutableListOf<String>()
        if (draft.title.isBlank() || draft.title.length > MAX_TITLE) errors += ERR_TITLE_REQUIRED
        if (draft.ownerId.isBlank()) errors += ERR_OWNER_REQUIRED
        draft.fields.forEach { f ->
            if (f.label.isBlank()) errors += ERR_FIELD_LABEL_REQUIRED
            if (CategoryTemplates.isDisallowedLabel(f.label)) errors += ERR_DISALLOWED_FIELD
        }
        return errors.distinct()
    }

    /** Drops empty optional fields and trims values so the vault never fills with blanks. */
    fun normalise(draft: VaultItemDraft): VaultItemDraft = draft.copy(
        title = draft.title.trim(),
        fields = draft.fields
            .map { it.copy(label = it.label.trim(), value = it.value.trim().take(MAX_FIELD_VALUE)) }
            .filter { it.value.isNotEmpty() },
        notes = draft.notes?.trim()?.take(MAX_NOTES)?.ifBlank { null },
    )
}

class CreateVaultItemUseCase @Inject constructor(
    private val repo: VaultRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(draft: VaultItemDraft): Outcome<String> = withContext(dispatchers.io) {
        val errors = DraftValidator.validate(draft)
        if (errors.isNotEmpty()) return@withContext Outcome.Failure(VaultError.ValidationFailed(errors))
        safeOutcome { repo.create(DraftValidator.normalise(draft)) }
    }
}

class UpdateVaultItemUseCase @Inject constructor(
    private val repo: VaultRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(draft: VaultItemDraft): Outcome<Unit> = withContext(dispatchers.io) {
        val errors = DraftValidator.validate(draft)
        if (errors.isNotEmpty()) return@withContext Outcome.Failure(VaultError.ValidationFailed(errors))
        safeOutcome { repo.update(DraftValidator.normalise(draft)) }
    }
}

class GetVaultItemUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(id: String): Outcome<VaultItem?> = withContext(dispatchers.io) { safeOutcome { repo.getItem(id) } }
}

class DeleteVaultItemUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    /** Soft delete: moves to Trash. */
    suspend operator fun invoke(id: String): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { repo.moveToTrash(id) } }
}

class RestoreVaultItemUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(id: String): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { repo.restore(id) } }
}

class PermanentlyDeleteVaultItemUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(id: String): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { repo.deletePermanently(id) } }
}

class EmptyTrashUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(): Outcome<Int> = withContext(dispatchers.io) { safeOutcome { repo.purgeTrashOlderThan(0) } }
}

class ToggleFavoriteUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(id: String, favorite: Boolean): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { repo.setFavorite(id, favorite) } }
}

class SearchVaultUseCase @Inject constructor(private val repo: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(query: String): Outcome<List<VaultItemSummary>> = withContext(dispatchers.io) { safeOutcome { repo.search(query) } }
}

class MarkReviewedUseCase @Inject constructor(
    private val repo: VaultRepository,
    private val dispatchers: DispatcherProvider,
    private val clock: com.rombamukiyam.vault.core.common.Clock,
) {
    suspend operator fun invoke(id: String): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { repo.markReviewed(id, clock.nowMillis()) } }
}
