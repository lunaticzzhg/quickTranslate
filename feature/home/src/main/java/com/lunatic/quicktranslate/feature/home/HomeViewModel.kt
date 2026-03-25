package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<HomeEffect>()
    val effect: SharedFlow<HomeEffect> = mutableEffect.asSharedFlow()

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.PrimaryActionClicked -> emitEffect(HomeEffect.LaunchFilePicker)
            is HomeIntent.MediaImported -> emitEffect(HomeEffect.NavigateToSession(intent.media))
            is HomeIntent.MediaImportFailed -> emitEffect(HomeEffect.ShowError(intent.message))
        }
    }

    private fun emitEffect(effect: HomeEffect) {
        viewModelScope.launch {
            mutableEffect.emit(effect)
        }
    }
}
