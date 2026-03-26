package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.os.Build
import java.io.File

class EmbeddedYtDlpProvider(
    private val context: Context
) {
    fun resolve(configuredPath: String): String {
        val nativeBinary = File(context.applicationInfo.nativeLibraryDir, "libytdlp.so")
        if (nativeBinary.exists() && nativeBinary.length() > 0L) {
            return nativeBinary.absolutePath
        }
        val embedded = resolveEmbeddedBinaryPath()
        if (embedded != null) {
            return embedded
        }
        return configuredPath.ifBlank { "yt-dlp" }
    }

    private fun resolveEmbeddedBinaryPath(): String? {
        val assetPath = Build.SUPPORTED_ABIS
            .asSequence()
            .map { abi -> "tools/yt-dlp/$abi/yt-dlp" }
            .firstOrNull { assetExists(it) }
            ?: return null
        val output = File(context.filesDir, "tools/yt-dlp/yt-dlp")
        if (!output.exists() || output.length() <= 0L) {
            output.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                output.outputStream().use { sink ->
                    input.copyTo(sink)
                }
            }
        }
        if (!output.canExecute()) {
            output.setExecutable(true, true)
        }
        return output.absolutePath.takeIf { output.exists() && output.length() > 0L }
    }

    private fun assetExists(path: String): Boolean {
        return runCatching {
            context.assets.open(path).close()
            true
        }.getOrDefault(false)
    }
}
