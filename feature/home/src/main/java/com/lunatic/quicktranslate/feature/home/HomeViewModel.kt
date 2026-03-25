package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
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
            HomeIntent.PrimaryActionClicked -> emitEffect(HomeEffect.ShowImportPlaceholder)
        }
    }

    private fun emitEffect(effect: HomeEffect) {
        mutableEffect.tryEmit(effect)
    }
}
