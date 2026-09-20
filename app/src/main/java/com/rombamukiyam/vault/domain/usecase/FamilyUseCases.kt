package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

class CreateFamilyProfileUseCase @Inject constructor(private val repo: FamilyRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(name: String, relationship: Relationship, color: Long): Outcome<FamilyProfile> = withContext(dispatchers.io) {
        if (name.isBlank()) return@withContext Outcome.Failure(VaultError.ValidationFailed(listOf("name_required")))
        safeOutcome { repo.create(name, relationship, AvatarType.INITIALS, color) }
    }
}

class UpdateFamilyProfileUseCase @Inject constructor(private val repo: FamilyRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(profile: FamilyProfile): Outcome<Unit> = withContext(dispatchers.io) {
        if (profile.displayName.isBlank()) return@withContext Outcome.Failure(VaultError.ValidationFailed(listOf("name_required")))
        safeOutcome { repo.update(profile) }
    }
}

/** Profiles are archived, never deleted: items they own stay exactly where they are. */
class ArchiveFamilyProfileUseCase @Inject constructor(private val repo: FamilyRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(id: String, archived: Boolean): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { repo.setArchived(id, archived) } }
}
