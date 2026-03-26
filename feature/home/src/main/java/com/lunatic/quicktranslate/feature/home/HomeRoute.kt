package com.lunatic.quicktranslate.feature.home

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    initialImportUrl: String? = null,
    onNavigateToSession: (Long, ImportedMedia) -> Unit,
    onNavigateToTranscodeTasks: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    linkImportViewModel: LinkImportViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLinkImportDialogOpen by rememberSaveable { mutableStateOf(false) }
    var linkImportInput by rememberSaveable { mutableStateOf("") }
    var linkImportError by rememberSaveable { mutableStateOf<String?>(null) }
    var clipboardPromptUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var consumedInitialImportUrl by rememberSaveable { mutableStateOf(false) }
    var lastHandledClipboardUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var resumeClipboardCheckTick by rememberSaveable { mutableStateOf(0) }

    fun detectClipboardLinkIfNeeded() {
        if (isLinkImportDialogOpen || clipboardPromptUrl != null) {
            return
        }
        val clipboardUrl = clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
            .extractFirstHttpUrl()
        if (isValidHttpUrl(clipboardUrl) && clipboardUrl != lastHandledClipboardUrl) {
            lastHandledClipboardUrl = clipboardUrl
            clipboardPromptUrl = clipboardUrl
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val result = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                require(descriptor.length != 0L) { "Selected file is empty." }
            } ?: error("Unable to access selected file.")

            val displayName = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            } ?: "Imported media"

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val durationMs = MediaMetadataRetriever().let { retriever ->
                try {
                    retriever.setDataSource(context, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: -1L
                } finally {
                    retriever.release()
                }
            }

            ImportedMedia(
                uri = uri.toString(),
                displayName = displayName,
                mimeType = mimeType,
                durationMs = durationMs
            )
        }

        result.onSuccess { media ->
            viewModel.onIntent(HomeIntent.MediaImported(media))
        }.onFailure {
            viewModel.onIntent(
                HomeIntent.MediaImportFailed(
                    message = "Unable to import this file. Please try another media file."
                )
            )
        }
    }

    LaunchedEffect(linkImportViewModel) {
        linkImportViewModel.effect.collect { effect ->
            when (effect) {
                is LinkImportEffect.NavigateToSession -> {
                    isLinkImportDialogOpen = false
                    linkImportError = null
                    onNavigateToSession(effect.projectId, effect.media)
                }
                is LinkImportEffect.ShowError -> {
                    linkImportError = effect.message
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.LaunchFilePicker -> filePickerLauncher.launch(
                    arrayOf("audio/*", "video/*")
                )
                HomeEffect.NavigateToLinkImport -> {
                    isLinkImportDialogOpen = true
                }
                HomeEffect.NavigateToTranscodeTasks -> onNavigateToTranscodeTasks()
                is HomeEffect.NavigateToSession -> onNavigateToSession(
                    effect.projectId,
                    effect.media
                )
                is HomeEffect.ShowError -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(initialImportUrl) {
        val sharedUrl = initialImportUrl?.trim().orEmpty()
        if (!consumedInitialImportUrl && isValidHttpUrl(sharedUrl)) {
            consumedInitialImportUrl = true
            linkImportInput = sharedUrl
            isLinkImportDialogOpen = true
            return@LaunchedEffect
        }
        detectClipboardLinkIfNeeded()
    }

    LaunchedEffect(resumeClipboardCheckTick) {
        if (resumeClipboardCheckTick <= 0) {
            return@LaunchedEffect
        }
        detectClipboardLinkIfNeeded()
        delay(350)
        detectClipboardLinkIfNeeded()
    }

    DisposableEffect(lifecycleOwner, clipboardManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                resumeClipboardCheckTick += 1
            }
        }
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            detectClipboardLinkIfNeeded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        }
    }

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent
    )

    val pendingClipboardUrl = clipboardPromptUrl
    if (pendingClipboardUrl != null) {
        AlertDialog(
            onDismissRequest = { clipboardPromptUrl = null },
            title = { Text(text = "Found link in clipboard") },
            text = { Text(text = "Import this link?\n$pendingClipboardUrl") },
            confirmButton = {
                Button(onClick = {
                    clipboardPromptUrl = null
                    linkImportInput = pendingClipboardUrl
                    linkImportError = null
                    isLinkImportDialogOpen = true
                }) {
                    Text(text = "Import")
                }
            },
            dismissButton = {
                Button(onClick = { clipboardPromptUrl = null }) {
                    Text(text = "Ignore")
                }
            }
        )
    }

    if (isLinkImportDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                isLinkImportDialogOpen = false
                linkImportError = null
            },
            title = { Text(text = "Import Media Link") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = linkImportInput,
                        onValueChange = { value ->
                            linkImportInput = value
                            if (linkImportError != null) {
                                linkImportError = null
                            }
                        },
                        label = { Text(text = "http/https URL") },
                        isError = linkImportError != null,
                        singleLine = true
                    )
                    val message = linkImportError
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val normalized = linkImportInput.trim()
                    if (!isValidHttpUrl(normalized)) {
                        linkImportError = "Please input a valid http/https link."
                        return@Button
                    }
                    linkImportError = null
                    linkImportViewModel.submitUrl(normalized)
                }) {
                    Text(text = "Start Import")
                }
            },
            dismissButton = {
                Button(onClick = {
                    isLinkImportDialogOpen = false
                    linkImportError = null
                }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

private fun String.extractFirstHttpUrl(): String {
    if (isBlank()) {
        return ""
    }
    val regex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    return regex.find(this)?.value?.trim().orEmpty()
}

private fun isValidHttpUrl(value: String): Boolean {
    if (value.isBlank()) {
        return false
    }
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
