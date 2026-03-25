package com.lunatic.quicktranslate.feature.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun SessionRoute(
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SessionEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    SessionScreen(
        state = state,
        player = viewModel.player,
        onIntent = viewModel::onIntent
    )
}
