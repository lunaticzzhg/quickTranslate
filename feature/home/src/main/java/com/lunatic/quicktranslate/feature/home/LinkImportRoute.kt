package com.lunatic.quicktranslate.feature.home

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun LinkImportRoute(
    initialUrl: String,
    onNavigateBack: () -> Unit,
    onNavigateToSession: (Long, ImportedMedia) -> Unit,
    viewModel: LinkImportViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LinkImportEffect.NavigateToSession -> onNavigateToSession(
                    effect.projectId,
                    effect.media
                )
                is LinkImportEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun submit() {
        val normalized = inputUrl.trim()
        if (!isValidHttpUrl(normalized)) {
            errorMessage = "Please input a valid http/https link."
            return
        }
        errorMessage = null
        viewModel.submitUrl(normalized)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Import Media Link",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Paste an audio/video URL, then continue to import directly.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = inputUrl,
            onValueChange = {
                inputUrl = it
                if (errorMessage != null) {
                    errorMessage = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "http/https URL") },
            isError = errorMessage != null,
            singleLine = true
        )
        val currentErrorMessage = errorMessage
        if (currentErrorMessage != null) {
            Text(
                text = currentErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = {
                val clipboardText = clipboardManager.getText()?.text?.trim().orEmpty()
                if (isValidHttpUrl(clipboardText)) {
                    inputUrl = clipboardText
                    errorMessage = null
                } else {
                    errorMessage = "Clipboard does not contain a valid http/https link."
                }
            }
        ) {
            Text(text = "Paste from Clipboard")
        }
        Button(onClick = { submit() }) {
            Text(text = "Start Import")
        }
        Button(onClick = onNavigateBack) {
            Text(text = "Back")
        }
    }
}

private fun isValidHttpUrl(value: String): Boolean {
    if (value.isBlank()) {
        return false
    }
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
