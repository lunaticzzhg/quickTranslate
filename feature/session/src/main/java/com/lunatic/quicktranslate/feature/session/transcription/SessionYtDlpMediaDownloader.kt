package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException

class SessionYtDlpMediaDownloader(
    private val appContext: Context,
    private val ytDlpCookiesPath: String,
    private val ytDlpExtractorArgs: String
) {
    private val internalCookiesFile: File by lazy {
        File(appContext.filesDir, INTERNAL_YOUTUBE_COOKIES_RELATIVE_PATH)
    }

    fun downloadYouTube(
        projectId: Long,
        sourceUrl: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        val outputDirectory = prepareOutputDirectory(projectId)
        val outputTemplate = File(outputDirectory, "source.%(ext)s").absolutePath
        Log.i(TAG, "YouTube download via embedded youtubedl-android.")
        return downloadEmbedded(
            sourceUrl = sourceUrl,
            outputDirectory = outputDirectory,
            outputTemplate = outputTemplate,
            formatOption = "93/92/91/best",
            useYouTubeHeaders = true,
            onProgress = onProgress
        )
    }

    fun downloadGeneric(
        projectId: Long,
        sourceUrl: String,
        platformLabel: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        val outputDirectory = prepareOutputDirectory(projectId)
        val outputTemplate = File(outputDirectory, "source.%(ext)s").absolutePath
        Log.i(TAG, "$platformLabel download via embedded youtubedl-android.")
        return downloadEmbedded(
            sourceUrl = sourceUrl,
            outputDirectory = outputDirectory,
            outputTemplate = outputTemplate,
            formatOption = "bestaudio/best",
            useYouTubeHeaders = false,
            onProgress = onProgress
        )
    }

    private fun downloadEmbedded(
        sourceUrl: String,
        outputDirectory: File,
        outputTemplate: String,
        formatOption: String,
        useYouTubeHeaders: Boolean,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        initEmbeddedRuntime()
        val request = YoutubeDLRequest(sourceUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-f", formatOption)
            addOption("--print", "after_move:filepath")
            addOption("-o", outputTemplate)
            if (useYouTubeHeaders) {
                addOption("--extractor-args", resolveExtractorArgs())
                addOption("--user-agent", DEFAULT_USER_AGENT)
                addOption("--add-header", "Referer:https://www.youtube.com/")
                addOption("--add-header", "Origin:https://www.youtube.com")
                addOption("--downloader", "native")
            }
            applyCookiesOption(
                addOption = { key, value -> addOption(key, value) },
                onInvalidPath = { path ->
                    throw IOException("yt-dlp cookies file does not exist: $path")
                }
            )
        }
        val outputLines = mutableListOf<String>()
        val response = try {
            YoutubeDL.execute(request) { progress, _, line ->
                if (line.isNotBlank()) {
                    outputLines += line
                }
                onProgress?.invoke(progress.toInt().coerceIn(0, 99))
            }
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            if (isBotCheckError(message)) {
                throw IOException(
                    "Embedded yt-dlp requires YouTube cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a valid cookies.txt file. " +
                        "Cause: ${error.message}",
                    error
                )
            }
            if (isDouyinFreshCookiesError(message)) {
                throw IOException(
                    "Douyin requires fresh cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a fresh cookies.txt file. " +
                        "Cause: ${error.message}",
                    error
                )
            }
            val diagnostics = if (useYouTubeHeaders) {
                buildEmbeddedListFormatsDiagnostics(sourceUrl)
            } else {
                ""
            }
            throw IOException("Embedded yt-dlp failed. Cause: ${error.message}$diagnostics", error)
        }

        if (response.exitCode != 0) {
            val logs = buildString {
                append(response.out)
                if (response.err.isNotBlank()) {
                    appendLine()
                    append(response.err)
                }
            }
            if (isBotCheckError(logs)) {
                throw IOException(
                    "Embedded yt-dlp requires YouTube cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a valid cookies.txt file. " +
                        "Output: ${logs.take(400)}"
                )
            }
            if (isDouyinFreshCookiesError(logs)) {
                throw IOException(
                    "Douyin requires fresh cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a fresh cookies.txt file. " +
                        "Output: ${logs.take(400)}"
                )
            }
            val diagnostics = if (useYouTubeHeaders) {
                buildEmbeddedListFormatsDiagnostics(sourceUrl)
            } else {
                ""
            }
            throw IOException(
                "yt-dlp failed with code ${response.exitCode}. Output: ${logs.take(400)}$diagnostics"
            )
        }

        outputLines += response.out.lines()
        val localPath = resolveYtDlpOutputPath(outputLines, outputDirectory)
            ?: throw IOException("yt-dlp finished but output file was not found.")
        onProgress?.invoke(100)
        return DownloadedTranscriptionMedia(
            localPath = localPath,
            mimeType = guessMimeType(localPath),
            downloadedFromRemote = true
        )
    }

    private fun initEmbeddedRuntime() {
        try {
            FFmpeg.getInstance().init(appContext)
            YoutubeDL.init(appContext)
        } catch (error: YoutubeDLException) {
            throw IOException(
                "Failed to initialize embedded yt-dlp runtime. Cause: ${error.message}",
                error
            )
        } catch (error: Exception) {
            throw IOException(
                "Failed to initialize embedded ffmpeg runtime. Cause: ${error.message}",
                error
            )
        }
    }

    private fun buildEmbeddedListFormatsDiagnostics(sourceUrl: String): String {
        return runCatching {
            val request = YoutubeDLRequest(sourceUrl)
            request.addOption("--no-playlist")
            request.addOption("--list-formats")
            request.addOption("--extractor-args", resolveExtractorArgs())
            request.addOption("--user-agent", DEFAULT_USER_AGENT)
            request.addOption("--add-header", "Referer:https://www.youtube.com/")
            request.addOption("--add-header", "Origin:https://www.youtube.com")
            applyCookiesOption(
                addOption = { key, value -> request.addOption(key, value) },
                onInvalidPath = {}
            )
            val response = YoutubeDL.execute(request)
            val payload = buildString {
                append(response.out)
                if (response.err.isNotBlank()) {
                    appendLine()
                    append(response.err)
                }
            }.trim()
            if (payload.isBlank()) {
                ""
            } else {
                "\nList-formats:\n${payload.take(1200)}"
            }
        }.getOrElse {
            "\nList-formats failed: ${it.message}"
        }
    }

    private fun applyCookiesOption(
        addOption: (String, String) -> Unit,
        onInvalidPath: (String) -> Unit
    ) {
        resolveCookiesFileOrNull(onInvalidPath)?.let { cookiesFile ->
            addOption("--cookies", cookiesFile.absolutePath)
        }
    }

    private fun resolveExtractorArgs(): String {
        return ytDlpExtractorArgs.trim()
            .takeIf { it.isNotBlank() }
            ?: "youtube:player_client=tv,web_safari"
    }

    private fun resolveCookiesFileOrNull(
        onInvalidPath: ((String) -> Unit)? = null
    ): File? {
        val cookiesPath = ytDlpCookiesPath.trim()
        if (cookiesPath.isBlank()) {
            return internalCookiesFile.takeIf { it.exists() && it.isFile }
        }
        val cookiesFile = File(cookiesPath)
        if (!cookiesFile.exists() || !cookiesFile.isFile) {
            onInvalidPath?.invoke(cookiesPath)
            return null
        }
        return cookiesFile
    }

    private fun isBotCheckError(message: String): Boolean {
        val text = message.lowercase()
        return text.contains("sign in to confirm you're not a bot") ||
            text.contains("use --cookies-from-browser or --cookies")
    }

    private fun isDouyinFreshCookiesError(message: String): Boolean {
        val text = message.lowercase()
        return text.contains("fresh cookies") && text.contains("douyin")
    }

    private fun resolveYtDlpOutputPath(
        outputLines: List<String>,
        outputDirectory: File
    ): String? {
        outputLines.asReversed().forEach { line ->
            val candidate = line.trim()
            if (candidate.isBlank()) return@forEach
            val file = File(candidate)
            if (file.isFile && file.exists()) {
                return file.absolutePath
            }
        }
        val fallback = outputDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("source.") && !it.name.endsWith(".part") }
            ?.maxByOrNull { it.lastModified() }
        return fallback?.absolutePath
    }

    private fun prepareOutputDirectory(projectId: Long): File {
        val outputDirectory = File(appContext.filesDir, "projects/$projectId/media")
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        outputDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("source.") }
            ?.forEach { it.delete() }
        return outputDirectory
    }

    private fun guessMimeType(path: String): String? {
        val sanitized = path.substringBefore('#').substringBefore('?').lowercase()
        return when {
            sanitized.endsWith(".mp3") -> "audio/mpeg"
            sanitized.endsWith(".m4a") -> "audio/mp4"
            sanitized.endsWith(".aac") -> "audio/aac"
            sanitized.endsWith(".wav") -> "audio/wav"
            sanitized.endsWith(".flac") -> "audio/flac"
            sanitized.endsWith(".ogg") -> "audio/ogg"
            sanitized.endsWith(".opus") -> "audio/opus"
            sanitized.endsWith(".mp4") -> "video/mp4"
            sanitized.endsWith(".webm") -> "video/webm"
            else -> null
        }
    }

    companion object {
        private const val TAG = "SessionRemoteDownload"
        private const val INTERNAL_YOUTUBE_COOKIES_RELATIVE_PATH = "yt/youtube_cookies.txt"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
