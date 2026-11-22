package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.AppError
import com.example.core.network.BackendErrorParser
import com.example.data.model.ProfileVisitorItem
import com.example.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitorsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val visitors: List<ProfileVisitorItem> = emptyList(),
    val max: Int = 50,
    val error: AppError? = null,
)

@HiltViewModel
class VisitorsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisitorsUiState())
    val uiState: StateFlow<VisitorsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun load(isRefresh: Boolean = false) {
        _uiState.update { cur ->
            if (isRefresh && cur.visitors.isNotEmpty()) {
                cur.copy(isRefreshing = true, error = null)
            } else {
                cur.copy(isLoading = true, isRefreshing = false, error = null)
            }
        }
        viewModelScope.launch {
            accountRepository.listMyVisitors()
                .onSuccess { res ->
                    _uiState.value = VisitorsUiState(
                        isLoading = false,
                        isRefreshing = false,
                        visitors = res.visitors,
                        max = res.max,
                        error = null,
                    )
                }
                .onFailure { err ->
                    val appError = err as? AppError ?: BackendErrorParser.fromThrowable(err)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = appError,
                        )
                    }
                }
        }
    }
}
