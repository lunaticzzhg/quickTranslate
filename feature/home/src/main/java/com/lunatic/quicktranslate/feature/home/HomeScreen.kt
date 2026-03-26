package com.lunatic.quicktranslate.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
        Button(
            onClick = { onIntent(HomeIntent.ImportLinkClicked) },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = state.importLinkLabel)
        }
        Button(
            onClick = { onIntent(HomeIntent.TranscodeEntryClicked) },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = state.transcodeEntryLabel)
        }

        LazyColumn(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = state.recentProjects,
                key = { it.id }
            ) { project ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onIntent(HomeIntent.RecentProjectClicked(project.id)) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = project.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Type: ${project.mediaTypeLabel}",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Subtitle: ${project.subtitleStatusLabel}",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Last Learned: ${project.recentLearnedAtLabel}",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { onIntent(HomeIntent.DeleteProjectClicked(project.id)) },
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(text = "Delete")
                        }
                    }
                }
            }
        }
    }

    val pendingDeletionProject = state.pendingDeletionProject
    if (pendingDeletionProject != null) {
        AlertDialog(
            onDismissRequest = { onIntent(HomeIntent.DismissDeleteDialog) },
            title = { Text(text = "Delete Project") },
            text = { Text(text = "Delete '${pendingDeletionProject.displayName}'?") },
            confirmButton = {
                Button(onClick = { onIntent(HomeIntent.ConfirmDeleteProject) }) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                Button(onClick = { onIntent(HomeIntent.DismissDeleteDialog) }) {
                    Text(text = "Cancel")
                }
            }
            )
    }

}
