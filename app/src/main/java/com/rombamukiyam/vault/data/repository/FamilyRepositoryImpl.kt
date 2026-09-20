package com.rombamukiyam.vault.data.repository

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import com.rombamukiyam.vault.data.mapper.toDomain
import com.rombamukiyam.vault.data.mapper.toEntity
import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FamilyRepositoryImpl(
    private val session: VaultSession,
    private val clock: Clock,
) : FamilyRepository {

    private val dao get() = session.database().familyProfileDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<FamilyProfile>> = session.state.flatMapLatest {
        if (session.isUnlocked) dao.observeAll().map { l -> l.map { it.toDomain() } } else flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<List<FamilyProfile>> = session.state.flatMapLatest {
        if (session.isUnlocked) dao.observeActive().map { l -> l.map { it.toDomain() } } else flowOf(emptyList())
    }

    override suspend fun get(id: String): FamilyProfile? = dao.getById(id)?.toDomain()

    override suspend fun count(): Int = dao.count()

    override suspend fun create(displayName: String, relationship: Relationship, avatarType: AvatarType, avatarColor: Long): FamilyProfile {
        val now = clock.nowMillis()
        val entity = FamilyProfileEntity(
            id = UUID.randomUUID().toString(),
            displayName = displayName.trim(),
            relationship = relationship.name,
            avatarType = avatarType.name,
            avatarColor = avatarColor,
            createdAt = now,
            updatedAt = now,
            sortOrder = dao.count(),
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun update(profile: FamilyProfile) {
        dao.update(profile.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived, clock.nowMillis())

    override suspend fun ownedItemCount(id: String): Int = dao.countItemsOwnedBy(id)
}

class VaultEventLoggerImpl(private val session: VaultSession, private val clock: Clock) : VaultEventLogger {
    override suspend fun log(type: HistoryEventType, detail: String?) {
        if (!session.isUnlocked) return
        runCatching {
            session.database().vaultItemDao().insertHistory(
                CredentialHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    vaultItemId = null,
                    eventType = type.name,
                    detail = detail,
                    occurredAt = clock.nowMillis(),
                ),
            )
        }
    }
}
