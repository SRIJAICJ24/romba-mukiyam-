package com.rombamukiyam.vault.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.FamilyChip
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.VaultCard
import com.rombamukiyam.vault.ui.components.categoryLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class VaultFilter(val category: VaultCategory? = null, val ownerId: String? = null)

data class VaultListUiState(
    val filter: VaultFilter = VaultFilter(),
    val items: List<VaultItemSummary> = emptyList(),
    val profiles: List<FamilyProfile> = emptyList(),
    val nowMillis: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultListViewModel @Inject constructor(
    vault: VaultRepository,
    family: FamilyRepository,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(VaultFilter())

    val state: StateFlow<VaultListUiState> = combine(vault.observeActive(), family.observeActive(), filter) { items, profiles, f ->
        VaultListUiState(
            filter = f,
            items = items.filter { (f.category == null || it.category == f.category) && (f.ownerId == null || it.ownerId == f.ownerId) },
            profiles = profiles,
            nowMillis = clock.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultListUiState())

    fun init(category: VaultCategory?, owner: String?) {
        if (filter.value == VaultFilter() && (category != null || owner != null)) filter.value = VaultFilter(category, owner)
    }

    fun setCategory(c: VaultCategory?) = filter.update { it.copy(category = c) }
    fun setOwner(id: String?) = filter.update { it.copy(ownerId = id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    initialCategory: VaultCategory?,
    initialOwner: String?,
    safeMode: Boolean,
    onOpenItem: (String) -> Unit,
    onAdd: (VaultCategory?) -> Unit,
    onOpenTrash: () -> Unit,
    onSearch: () -> Unit,
    viewModel: VaultListViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(initialCategory, initialOwner) { viewModel.init(initialCategory, initialOwner) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RombaTopBar(stringResource(R.string.vault_title)) {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.vault_search_title)) }
                IconButton(onClick = onOpenTrash) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.vault_trash)) }
            }
        },
        floatingActionButton = {
            if (!safeMode) {
                ExtendedFloatingActionButton(onClick = { onAdd(state.filter.category) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.home_add_credential)) })
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    item { FilterChip(selected = state.filter.category == null, onClick = { viewModel.setCategory(null) }, label = { Text(stringResource(R.string.vault_all)) }) }
                    items(VaultCategory.entries) { c ->
                        FilterChip(selected = state.filter.category == c, onClick = { viewModel.setCategory(if (state.filter.category == c) null else c) }, label = { Text(categoryLabel(c)) })
                    }
                }
            }
            if (state.profiles.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        items(state.profiles, key = { it.id }) { p ->
                            FamilyChip(p, selected = state.filter.ownerId == p.id, onClick = { viewModel.setOwner(if (state.filter.ownerId == p.id) null else p.id) })
                        }
                    }
                }
            }
            if (state.items.isEmpty()) {
                item {
                    val cat = state.filter.category
                    EmptyState(
                        title = if (cat == null) stringResource(R.string.vault_empty_title) else stringResource(R.string.vault_empty_category_title, categoryLabel(cat)),
                        body = if (cat == null) stringResource(R.string.vault_empty_body) else stringResource(R.string.vault_empty_category_body),
                        icon = Icons.Filled.Lock,
                        actionText = if (safeMode) null else stringResource(R.string.home_add_credential),
                        onAction = { onAdd(cat) },
                    )
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    VaultCard(item, state.nowMillis, onClick = { onOpenItem(item.id) }, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
