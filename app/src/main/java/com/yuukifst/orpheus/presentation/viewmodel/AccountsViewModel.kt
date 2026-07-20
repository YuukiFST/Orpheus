package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountsUiState(
    val connectedAccountCount: Int = 0
)

@HiltViewModel
class AccountsViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<AccountsUiState> = MutableStateFlow(AccountsUiState()).asStateFlow()
}
