package com.lunatic.quicktranslate.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = state.message,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Button(onClick = { onIntent(HomeIntent.PrimaryActionClicked) }) {
            Text(text = state.primaryActionLabel)
        }
    }
}
