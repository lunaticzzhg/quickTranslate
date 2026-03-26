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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LinkImportRoute(
    initialUrl: String,
    onNavigateBack: () -> Unit,
    onSubmitUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val normalized = inputUrl.trim()
        if (!isValidHttpUrl(normalized)) {
            errorMessage = "Please input a valid http/https link."
            return
        }
        errorMessage = null
        onSubmitUrl(normalized)
        Toast.makeText(
            context,
            "Link accepted. Import execution will be connected in next task.",
            Toast.LENGTH_SHORT
        ).show()
        onNavigateBack()
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
            text = "Paste an audio/video URL, then continue to import.",
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
