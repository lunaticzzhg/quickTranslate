package com.lunatic.quicktranslate.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            if (state.recentProjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                    Button(
                        onClick = { onIntent(HomeIntent.PrimaryActionClicked) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                            Text(
                                text = state.primaryActionLabel,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    Button(
                        onClick = { onIntent(HomeIntent.ImportLinkClicked) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                            Text(
                                text = state.importLinkLabel,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onIntent(HomeIntent.PrimaryActionClicked) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = state.primaryActionLabel)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onIntent(HomeIntent.ImportLinkClicked) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = state.importLinkLabel)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .padding(top = 20.dp)
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
        }

        FloatingActionButton(
            onClick = { onIntent(HomeIntent.TranscodeEntryClicked) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(64.dp)
        ) {
            Text(text = "任务")
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
