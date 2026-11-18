package com.example.mediaplayer.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaplayer.domain.error.DomainResult
import com.example.mediaplayer.domain.usecase.SearchTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(val titles: List<String> = emptyList())

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchTracks: SearchTracksUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val result = searchTracks("")
            if (result is DomainResult.Success) {
                _state.value = HomeUiState(result.value.map { "${it.artist} — ${it.title}" })
            }
        }
    }
}
