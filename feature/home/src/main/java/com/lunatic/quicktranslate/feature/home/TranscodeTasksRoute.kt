package com.lunatic.quicktranslate.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun TranscodeTasksRoute(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (Long, ImportedMedia) -> Unit,
    viewModel: TranscodeTasksViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                TranscodeTasksEffect.NavigateBack -> onNavigateBack()
                is TranscodeTasksEffect.NavigateToSession -> {
                    onNavigateToSession(effect.projectId, effect.media)
                }
            }
        }
    }

    TranscodeTasksScreen(
        state = state,
        onIntent = viewModel::onIntent
    )
}
