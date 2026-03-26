package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolvedItem
import com.lunatic.quicktranslate.domain.project.usecase.ResolvePlatformLinkUseCase
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class DownloadedTranscriptionMedia(
    val localPath: String,
    val mimeType: String?,
    val downloadedFromRemote: Boolean
)

class SessionRemoteMediaDownloadStage(
    private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val resolvePlatformLinkUseCase: ResolvePlatformLinkUseCase,
    private val ytDlpPath: String,
    private val ytDlpCookiesPath: String,
    private val ytDlpExtractorArgs: String
) {
    private val internalCookiesFile: File by lazy {
        File(appContext.filesDir, INTERNAL_YOUTUBE_COOKIES_RELATIVE_PATH)
    }
    suspend fun ensureLocalMedia(
        projectId: Long,
        mediaUri: String,
        onProgress: ((Int) -> Unit)? = null
    ): DownloadedTranscriptionMedia {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse(mediaUri)
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme != "http" && scheme != "https") {
                return@withContext DownloadedTranscriptionMedia(
                    localPath = mediaUri,
                    mimeType = null,
                    downloadedFromRemote = false
                )
            }
            val host = uri.host?.lowercase().orEmpty()
            if (isYtDlpPreferredHost(host)) {
                return@withContext downloadByYtDlpPreferredPlatform(
                    projectId = projectId,
                    sourceUrl = mediaUri,
                    host = host,
                    onProgress = onProgress
                )
            }

            val resolvedDownloadUrl = resolveDownloadUrl(mediaUri)
            val downloadUri = Uri.parse(resolvedDownloadUrl)
            val request = Request.Builder()
                .url(resolvedDownloadUrl)
                .get()
                .applyRequestHeadersForHost(downloadUri.host?.lowercase().orEmpty())
                .build()

            val firstResponse = okHttpClient.newCall(request).execute()
            if (firstResponse.code == 403) {
                firstResponse.close()
                val retryRequest = Request.Builder()
                    .url(resolvedDownloadUrl)
                    .get()
                    .applyRequestHeadersForHost(downloadUri.host?.lowercase().orEmpty())
                    .header("Range", "bytes=0-")
                    .build()
                return@withContext executeDownloadRequest(
                    response = okHttpClient.newCall(retryRequest).execute(),
                    projectId = projectId,
                    uri = downloadUri,
                    onProgress = onProgress
                )
            }
            return@withContext executeDownloadRequest(
                response = firstResponse,
                projectId = projectId,
                uri = downloadUri,
                onProgress = onProgress
            )
        }
    }

    private suspend fun resolveDownloadUrl(mediaUri: String): String {
        val uri = Uri.parse(mediaUri)
        val host = uri.host?.lowercase().orEmpty()
        if (!isPlatformPageHost(host)) {
            return mediaUri
        }
        return when (val result = resolvePlatformLinkUseCase(mediaUri)) {
            is PlatformLinkResolveResult.Success -> {
                selectPreferredCandidate(result.media.items)?.resolvedMediaUrl ?: mediaUri
            }
            is PlatformLinkResolveResult.Failure -> mediaUri
        }
    }

    private fun selectPreferredCandidate(
        items: List<PlatformLinkResolvedItem>
    ): PlatformLinkResolvedItem? {
        if (items.isEmpty()) {
            return null
        }
        return items.firstOrNull { it.id.startsWith("durl_") }
            ?: items.firstOrNull { it.mimeType?.startsWith("audio/") == true }
            ?: items.first()
    }

    private fun isPlatformPageHost(host: String): Boolean {
        if (host.isBlank()) return false
        return host.contains("bilibili.com") ||
            host == "b23.tv" ||
            host.contains("youtube.com") ||
            host == "youtu.be" ||
            host.contains("douyin.com")
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host.contains("youtube.com") || host == "youtu.be"
    }

    private fun isBilibiliHost(host: String): Boolean {
        return host.contains("bilibili.com") || host == "b23.tv"
    }

    private fun isDouyinHost(host: String): Boolean {
        return host.contains("douyin.com") || host.contains("iesdouyin.com")
    }

    private fun isYtDlpPreferredHost(host: String): Boolean {
        return isYouTubeHost(host) || isBilibiliHost(host) || isDouyinHost(host)
    }

    private fun downloadByYtDlpPreferredPlatform(
        projectId: Long,
        sourceUrl: String,
        host: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        return if (isYouTubeHost(host)) {
            downloadYouTubeByYtDlp(
                projectId = projectId,
                sourceUrl = sourceUrl,
                onProgress = onProgress
            )
        } else {
            downloadGenericByEmbeddedYtDlp(
                projectId = projectId,
                sourceUrl = sourceUrl,
                platformLabel = when {
                    isBilibiliHost(host) -> "bilibili"
                    isDouyinHost(host) -> "douyin"
                    else -> host
                },
                onProgress = onProgress
            )
        }
    }

    private fun downloadGenericByEmbeddedYtDlp(
        projectId: Long,
        sourceUrl: String,
        platformLabel: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        val outputDirectory = prepareOutputDirectory(projectId)
        val outputTemplate = File(outputDirectory, "source.%(ext)s").absolutePath
        Log.i(TAG, "$platformLabel download via embedded youtubedl-android.")
        return downloadByEmbeddedYtDlpGeneric(
            sourceUrl = sourceUrl,
            outputDirectory = outputDirectory,
            outputTemplate = outputTemplate,
            onProgress = onProgress
        )
    }

    private fun downloadYouTubeByYtDlp(
        projectId: Long,
        sourceUrl: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        val outputDirectory = prepareOutputDirectory(projectId)
        val outputTemplate = File(outputDirectory, "source.%(ext)s").absolutePath
        Log.i(TAG, "YouTube download via embedded youtubedl-android.")
        return downloadByEmbeddedYtDlp(
            sourceUrl = sourceUrl,
            outputDirectory = outputDirectory,
            outputTemplate = outputTemplate,
            onProgress = onProgress
        )
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

    private fun downloadByEmbeddedYtDlp(
        sourceUrl: String,
        outputDirectory: File,
        outputTemplate: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
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
        val request = YoutubeDLRequest(sourceUrl)
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        request.addOption("--extractor-args", resolveExtractorArgs())
        request.addOption("--user-agent", DEFAULT_USER_AGENT)
        request.addOption("--add-header", "Referer:https://www.youtube.com/")
        request.addOption("--add-header", "Origin:https://www.youtube.com")
        request.addOption("--downloader", "native")
        request.addOption("-f", "93/92/91/best")
        request.addOption("--print", "after_move:filepath")
        request.addOption("-o", outputTemplate)
        applyCookiesOption(
            addOption = { key, value -> request.addOption(key, value) },
            onInvalidPath = { path ->
                throw IOException("yt-dlp cookies file does not exist: $path")
            }
        )
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
            val listFormats = buildEmbeddedListFormatsDiagnostics(sourceUrl)
            throw IOException(
                "Embedded yt-dlp failed. Cause: ${error.message}" + listFormats,
                error
            )
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
            val listFormats = buildEmbeddedListFormatsDiagnostics(sourceUrl)
            throw IOException(
                "yt-dlp failed with code ${response.exitCode}. Output: ${logs.take(400)}" +
                    listFormats
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

    private fun downloadByExternalYtDlp(
        sourceUrl: String,
        outputDirectory: File,
        outputTemplate: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        val executable = ytDlpPath.ifBlank { "yt-dlp" }
        val executableFile = File(executable)
        if (executableFile.exists() && !executableFile.canExecute()) {
            executableFile.setExecutable(true, true)
        }
        val command = listOf(
            executable,
            "--no-playlist",
            "--no-warnings",
            "--extractor-args",
            resolveExtractorArgs(),
            "--user-agent",
            DEFAULT_USER_AGENT,
            "--add-header",
            "Referer:https://www.youtube.com/",
            "--add-header",
            "Origin:https://www.youtube.com",
            "--downloader",
            "native",
            "-f",
            "93/92/91/best",
            "--print",
            "after_move:filepath",
            "-o",
            outputTemplate,
            sourceUrl
        )
        val commandWithCookies = command.toMutableList().apply {
            appendCookiesOption(this)
        }
        return runExternalYtDlpCommand(
            command = commandWithCookies,
            outputDirectory = outputDirectory,
            onProgress = onProgress
        )
    }

    private fun downloadByEmbeddedYtDlpGeneric(
        sourceUrl: String,
        outputDirectory: File,
        outputTemplate: String,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        try {
            FFmpeg.getInstance().init(appContext)
            YoutubeDL.init(appContext)
        } catch (error: Exception) {
            throw IOException("Failed to initialize embedded yt-dlp runtime. Cause: ${error.message}", error)
        }
        val request = YoutubeDLRequest(sourceUrl)
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        request.addOption("-f", "bestaudio/best")
        request.addOption("--print", "after_move:filepath")
        request.addOption("-o", outputTemplate)
        applyCookiesOption(
            addOption = { key, value -> request.addOption(key, value) },
            onInvalidPath = { path ->
                throw IOException("yt-dlp cookies file does not exist: $path")
            }
        )
        val outputLines = mutableListOf<String>()
        val response = try {
            YoutubeDL.execute(request) { progress, _, line ->
                if (line.isNotBlank()) {
                    outputLines += line
                }
                onProgress?.invoke(progress.toInt().coerceIn(0, 99))
            }
        } catch (error: Exception) {
            if (isDouyinFreshCookiesError(error.message.orEmpty())) {
                throw IOException(
                    "Douyin requires fresh cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a fresh cookies.txt file. " +
                        "Cause: ${error.message}",
                    error
                )
            }
            throw IOException("Embedded yt-dlp failed. Cause: ${error.message}", error)
        }
        if (response.exitCode != 0) {
            val logs = buildString {
                append(response.out)
                if (response.err.isNotBlank()) {
                    appendLine()
                    append(response.err)
                }
            }
            if (isDouyinFreshCookiesError(logs)) {
                throw IOException(
                    "Douyin requires fresh cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a fresh cookies.txt file. " +
                        "Output: ${logs.take(400)}"
                )
            }
            throw IOException("yt-dlp failed with code ${response.exitCode}. Output: ${logs.take(400)}")
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

    private fun runExternalYtDlpCommand(
        command: List<String>,
        outputDirectory: File,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            val baseMessage = error.message.orEmpty()
            val compatibilityHint = if (
                command.firstOrNull().orEmpty().contains("libytdlp.so") &&
                baseMessage.contains("error=2")
            ) {
                " Hint: this binary is likely built for GNU/Linux (ld-linux/ld-musl) " +
                    "and not Android bionic. Please rebuild yt-dlp for Android arm64."
            } else {
                ""
            }
            throw IOException(
                "yt-dlp is unavailable at '${command.firstOrNull().orEmpty()}'. " +
                    "Please provide an Android-compatible executable via quicktranslate.ytdlp.path. " +
                    "Cause: ${error.message}.$compatibilityHint",
                error
            )
        }
        val logs = StringBuilder()
        val outputLines = mutableListOf<String>()
        process.inputStream.bufferedReader().use { reader ->
            readYtDlpOutput(reader, logs, outputLines, onProgress)
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            if (isBotCheckError(logs.toString())) {
                throw IOException(
                    "yt-dlp requires YouTube cookies. " +
                        "Please set quicktranslate.ytdlp.cookies.path to a valid cookies.txt file. " +
                        "Output: ${logs.take(400)}"
                )
            }
            val listFormats = buildExternalListFormatsDiagnostics(command.lastOrNull().orEmpty())
            throw IOException(
                "yt-dlp failed with code $exitCode. Output: ${logs.take(400)}" +
                    listFormats
            )
        }
        val localPath = resolveYtDlpOutputPath(outputLines, outputDirectory)
            ?: throw IOException("yt-dlp finished but output file was not found.")
        onProgress?.invoke(100)
        return DownloadedTranscriptionMedia(
            localPath = localPath,
            mimeType = guessMimeType(localPath),
            downloadedFromRemote = true
        )
    }

    private fun is403Error(message: String): Boolean {
        val text = message.lowercase()
        return text.contains("http error 403") || text.contains("forbidden")
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
                onInvalidPath = { }
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

    private fun buildExternalListFormatsDiagnostics(sourceUrl: String): String {
        if (sourceUrl.isBlank()) return ""
        val executable = ytDlpPath.ifBlank { "yt-dlp" }
        val command = mutableListOf(
            executable,
            "--no-playlist",
            "--extractor-args",
            resolveExtractorArgs(),
            "--user-agent",
            DEFAULT_USER_AGENT,
            "--add-header",
            "Referer:https://www.youtube.com/",
            "--add-header",
            "Origin:https://www.youtube.com",
            "--list-formats",
            sourceUrl
        )
        return runCatching {
            appendCookiesOption(command)
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val logs = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    logs.appendLine(line)
                }
            }
            process.waitFor()
            val payload = logs.toString().trim()
            if (payload.isBlank()) {
                ""
            } else {
                "\nList-formats:\n${payload.take(1200)}"
            }
        }.getOrElse {
            "\nList-formats failed: ${it.message}"
        }
    }

    private fun appendCookiesOption(command: MutableList<String>) {
        resolveCookiesFileOrNull()?.let { cookiesFile ->
            command.add(1, cookiesFile.absolutePath)
            command.add(1, "--cookies")
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

    private fun readYtDlpOutput(
        reader: BufferedReader,
        logs: StringBuilder,
        outputLines: MutableList<String>,
        onProgress: ((Int) -> Unit)?
    ) {
        val progressRegex = Regex("""(\d{1,3}(?:\.\d+)?)%""")
        while (true) {
            val line = reader.readLine() ?: break
            outputLines += line
            logs.appendLine(line)
            val progress = progressRegex.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?.toInt()
                ?.coerceIn(0, 99)
            if (progress != null) {
                onProgress?.invoke(progress)
            }
        }
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

    private fun executeDownloadRequest(
        response: Response,
        projectId: Long,
        uri: Uri,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        response.use { networkResponse ->
            if (!networkResponse.isSuccessful) {
                throw IOException("Download failed with code ${networkResponse.code}.")
            }
            val body = networkResponse.body ?: throw IOException("Download body is empty.")
            val contentType = body.contentType()?.toString()
            val totalBytes = body.contentLength().takeIf { it > 0L }
            val outputDirectory = File(
                appContext.filesDir,
                "projects/$projectId/media"
            )
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs()
            }
            val extension = resolveExtension(uri = uri, contentType = contentType)
            val targetFile = File(outputDirectory, "source$extension")
            val tempFile = File(outputDirectory, "source.download")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes != null && onProgress != null) {
                            val percent = ((bytesCopied * 100L) / totalBytes)
                                .toInt()
                                .coerceIn(0, 100)
                            onProgress(percent)
                        }
                    }
                }
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Unable to move downloaded file into project directory.")
            }
            return DownloadedTranscriptionMedia(
                localPath = targetFile.absolutePath,
                mimeType = contentType,
                downloadedFromRemote = true
            )
        }
    }

    private fun resolveExtension(uri: Uri, contentType: String?): String {
        val path = uri.path.orEmpty()
        val suffix = path.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotBlank() && it.length <= 8 }
        if (suffix != null) {
            return ".$suffix"
        }
        return when {
            contentType?.startsWith("audio/") == true -> ".m4a"
            contentType?.startsWith("video/") == true -> ".mp4"
            else -> ".media"
        }
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

    private fun Request.Builder.applyRequestHeadersForHost(host: String): Request.Builder {
        header("User-Agent", DEFAULT_USER_AGENT)
        header("Accept", "*/*")
        if (host.contains("bilivideo.com") || host.contains("bilibili.com")) {
            header("Referer", "https://www.bilibili.com/")
            header("Origin", "https://www.bilibili.com")
        } else if (
            host.contains("googlevideo.com") ||
            host.contains("youtube.com") ||
            host == "youtu.be"
        ) {
            header("Referer", "https://www.youtube.com/")
            header("Origin", "https://www.youtube.com")
        } else if (host.contains("douyin.com") || host.contains("bytecdn.cn")) {
            header("Referer", "https://www.douyin.com/")
            header("Origin", "https://www.douyin.com")
        }
        return this
    }

    companion object {
        private const val TAG = "SessionRemoteDownload"
        private const val INTERNAL_YOUTUBE_COOKIES_RELATIVE_PATH = "yt/youtube_cookies.txt"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
