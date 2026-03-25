package com.lunatic.quicktranslate.feature.home

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeRoute(
    onNavigateToSession: (ImportedMedia) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.LaunchFilePicker -> filePickerLauncher.launch(
                    arrayOf("audio/*", "video/*")
                )
                is HomeEffect.NavigateToSession -> onNavigateToSession(effect.media)
                is HomeEffect.ShowError -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent
    )
}
