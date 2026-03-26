package com.lunatic.quicktranslate.feature.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun TranscodeTasksScreen(
    state: TranscodeTasksState,
    onIntent: (TranscodeTasksIntent) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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
                        Text(
                            text = "Stage: ${task.stageLabel}",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!task.progressLabel.isNullOrBlank()) {
                            Text(
                                text = "Progress: ${task.progressLabel}",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!task.errorMessage.isNullOrBlank()) {
                            val needsYoutubeLogin = task.errorMessage
                                .contains("Sign in to confirm you’re not a bot", ignoreCase = true) ||
                                task.errorMessage.contains("cookies", ignoreCase = true)
                            SelectionContainer {
                                Text(
                                    text = "Reason: ${task.errorMessage}",
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(
                                        AnnotatedString(task.errorMessage.orEmpty())
                                    )
                                    Toast.makeText(
                                        context,
                                        "Error copied",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(text = "Copy Error")
                            }
                            if (needsYoutubeLogin) {
                                Button(
                                    onClick = {
                                        val intent = Intent(YOUTUBE_LOGIN_ACTION).apply {
                                            setPackage(context.packageName)
                                        }
                                        runCatching { context.startActivity(intent) }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    "Unable to open YouTube login",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    },
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    Text(text = "Login YouTube")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val YOUTUBE_LOGIN_ACTION = "com.lunatic.quicktranslate.action.YOUTUBE_LOGIN"
