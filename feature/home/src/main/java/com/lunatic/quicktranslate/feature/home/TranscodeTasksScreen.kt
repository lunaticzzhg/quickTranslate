package com.lunatic.quicktranslate.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TranscodeTasksScreen(
    state: TranscodeTasksState,
    onIntent: (TranscodeTasksIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = { onIntent(TranscodeTasksIntent.BackClicked) }) {
            Text(text = "Back")
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.tasks, key = { it.taskId }) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onIntent(TranscodeTasksIntent.TaskClicked(task)) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = task.projectName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Status: ${task.statusLabel}",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!task.errorMessage.isNullOrBlank()) {
                            Text(
                                text = "Reason: ${task.errorMessage}",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
