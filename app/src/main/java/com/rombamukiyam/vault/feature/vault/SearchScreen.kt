package com.rombamukiyam.vault.feature.vault

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.usecase.SearchVaultUseCase
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.VaultCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class SearchUiState(val query: String = "", val results: List<VaultItemSummary> = emptyList(), val searched: Boolean = false, val nowMillis: Long = 0L)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val search: SearchVaultUseCase, private val clock: Clock) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState(nowMillis = clock.nowMillis()))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val queries = MutableStateFlow("")

    init {
        queries.debounce(200).distinctUntilChanged().onEach { q ->
            if (q.isBlank()) {
                _state.value = _state.value.copy(results = emptyList(), searched = false)
            } else {
                val results = search(q).getOrNull() ?: emptyList()
                _state.value = _state.value.copy(results = results, searched = true, nowMillis = clock.nowMillis())
            }
        }.launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        queries.value = q
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.vault_search_title), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.searched && state.results.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.vault_search_empty, state.query),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.results, key = { it.id }) { item ->
                VaultCard(item, state.nowMillis, onClick = { onOpenItem(item.id) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
