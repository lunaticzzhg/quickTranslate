package com.lunatic.quicktranslate.feature.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.ShowImportPlaceholder -> {
                    Toast.makeText(
                        context,
                        "Import flow will be implemented in the next task.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent
    )
}
