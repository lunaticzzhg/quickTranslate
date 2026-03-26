package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveFailure
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveFailureType
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolvedItem
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolvedMedia
import com.lunatic.quicktranslate.domain.project.repository.PlatformLinkResolverRepository
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DefaultPlatformLinkResolverRepository : PlatformLinkResolverRepository {
    override suspend fun resolve(sourceUrl: String): PlatformLinkResolveResult {
        return withContext(Dispatchers.IO) {
            val normalized = sourceUrl.trim()
            val parsedUri = runCatching { URI(normalized) }.getOrNull()
            val host = parsedUri?.host?.lowercase().orEmpty()

            if (host.isBlank()) {
                return@withContext PlatformLinkResolveResult.Failure(
                    PlatformLinkResolveFailure(
                        type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                        message = "Invalid link host."
                    )
                )
            }

            if (isLikelyDirectMediaLink(normalized)) {
                val item = PlatformLinkResolvedItem(
                    id = "default",
                    label = "Default",
                    resolvedMediaUrl = normalized,
                    mimeType = guessMimeType(normalized),
                    estimatedBytes = null
                )
                return@withContext PlatformLinkResolveResult.Success(
                    PlatformLinkResolvedMedia(
                        requestUrl = normalized,
                        suggestedProjectName = normalized.defaultDisplayName(),
                        items = listOf(item),
                        sourceHost = host,
                        isDirectMedia = true
                    )
                )
            }

            if (host in BILIBILI_HOSTS) {
                return@withContext resolveBilibili(normalized)
            }

            if (host in DOUYIN_HOSTS) {
                val item = PlatformLinkResolvedItem(
                    id = "douyin_ytdlp",
                    label = "Douyin (yt-dlp)",
                    resolvedMediaUrl = normalized,
                    mimeType = null,
                    estimatedBytes = null
                )
                return@withContext PlatformLinkResolveResult.Success(
                    PlatformLinkResolvedMedia(
                        requestUrl = normalized,
                        suggestedProjectName = normalized.defaultDisplayName(),
                        items = listOf(item),
                        sourceHost = host,
                        isDirectMedia = false
                    )
                )
            }

            if (host in YOUTUBE_HOSTS) {
                return@withContext resolveYouTube(normalized)
            }

            return@withContext PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.UNSUPPORTED_SITE,
                    message = "Unsupported site: $host"
                )
            )
        }
    }

    private fun isLikelyDirectMediaLink(url: String): Boolean {
        val sanitized = url.substringBefore('#').substringBefore('?').lowercase()
        return DIRECT_MEDIA_SUFFIXES.any { suffix -> sanitized.endsWith(suffix) }
    }

    private fun guessMimeType(url: String): String? {
        val sanitized = url.substringBefore('#').substringBefore('?').lowercase()
        return when {
            sanitized.endsWith(".mp3") -> "audio/mpeg"
            sanitized.endsWith(".m4a") -> "audio/mp4"
            sanitized.endsWith(".aac") -> "audio/aac"
            sanitized.endsWith(".wav") -> "audio/wav"
            sanitized.endsWith(".flac") -> "audio/flac"
            sanitized.endsWith(".ogg") -> "audio/ogg"
            sanitized.endsWith(".opus") -> "audio/opus"
            sanitized.endsWith(".mp4") -> "video/mp4"
            sanitized.endsWith(".m4v") -> "video/x-m4v"
            sanitized.endsWith(".mov") -> "video/quicktime"
            sanitized.endsWith(".mkv") -> "video/x-matroska"
            sanitized.endsWith(".webm") -> "video/webm"
            sanitized.endsWith(".3gp") -> "video/3gpp"
            else -> null
        }
    }

    private fun resolveBilibili(sourceUrl: String): PlatformLinkResolveResult {
        val expandedUrl = resolveFinalUrl(sourceUrl)
        val expandedHost = runCatching { URI(expandedUrl).host?.lowercase().orEmpty() }
            .getOrDefault("")
        if (expandedHost !in BILIBILI_HOSTS) {
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.UNSUPPORTED_SITE,
                    message = "Short link does not resolve to a supported bilibili host."
                )
            )
        }
        val html = runCatching {
            httpGetText(
                expandedUrl,
                headers = mapOf(
                    "Referer" to "https://www.bilibili.com/",
                    "Origin" to "https://www.bilibili.com",
                    "User-Agent" to DEFAULT_USER_AGENT,
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        }.getOrElse { error ->
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = error.message ?: "Failed to fetch bilibili page."
                )
            )
        }

        val title = extractPageTitle(html)
            ?: expandedUrl.defaultDisplayName()
        val playInfoJson = extractPlayInfoJson(html)
            ?: return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "Bilibili play info was not found on page."
                )
            )
        val items = parseBilibiliPlayInfo(playInfoJson)
        if (items.isEmpty()) {
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "No downloadable media candidates were extracted from bilibili page."
                )
            )
        }
        return PlatformLinkResolveResult.Success(
            PlatformLinkResolvedMedia(
                requestUrl = sourceUrl,
                suggestedProjectName = title,
                items = items,
                sourceHost = expandedHost,
                isDirectMedia = false
            )
        )
    }

    private fun resolveYouTube(sourceUrl: String): PlatformLinkResolveResult {
        val expandedUrl = resolveFinalUrl(sourceUrl)
        val expandedHost = runCatching { URI(expandedUrl).host?.lowercase().orEmpty() }
            .getOrDefault("")
        if (expandedHost !in YOUTUBE_HOSTS) {
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.UNSUPPORTED_SITE,
                    message = "Short link does not resolve to a supported youtube host."
                )
            )
        }
        val videoId = extractYouTubeVideoId(expandedUrl)
            ?: return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "Unable to extract YouTube video id from link."
                )
            )
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val html = runCatching {
            httpGetText(
                watchUrl,
                headers = mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com",
                    "User-Agent" to DEFAULT_USER_AGENT,
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        }.getOrElse { error ->
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = error.message ?: "Failed to fetch youtube page."
                )
            )
        }
        val playerJson = extractYouTubePlayerResponseJson(html)
            ?: return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "YouTube player response was not found on page."
                )
            )
        val playerResponse = runCatching { JSONObject(playerJson) }.getOrNull()
            ?: return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "YouTube player response is invalid JSON."
                )
            )
        val playabilityStatus = playerResponse.optJSONObject("playabilityStatus")
        val status = playabilityStatus?.optString("status").orEmpty()
        if (status.isNotBlank() && status != "OK") {
            val reason = playabilityStatus?.optString("reason").orEmpty()
            val lowerReason = reason.lowercase()
            val failureType = when {
                lowerReason.contains("sign in") ||
                    lowerReason.contains("confirm your age") -> PlatformLinkResolveFailureType.LOGIN_REQUIRED
                lowerReason.contains("not available in your country") ||
                    lowerReason.contains("not available in your region") -> PlatformLinkResolveFailureType.REGION_RESTRICTED
                lowerReason.contains("copyright") ||
                    lowerReason.contains("drm") -> PlatformLinkResolveFailureType.DRM_PROTECTED
                else -> PlatformLinkResolveFailureType.EXTRACT_FAILED
            }
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = failureType,
                    message = reason.ifBlank { "YouTube playability status: $status" }
                )
            )
        }
        val items = parseYouTubeStreamingItems(playerResponse)
        if (items.isEmpty()) {
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "No downloadable media candidates were extracted from youtube page."
                )
            )
        }
        val title = playerResponse.optJSONObject("videoDetails")
            ?.optString("title")
            ?.trim()
            ?.ifBlank { null }
            ?: extractPageTitle(html)
                ?.removeSuffix("- YouTube")
                ?.trim()
                ?.ifBlank { null }
            ?: watchUrl.defaultDisplayName()
        return PlatformLinkResolveResult.Success(
            PlatformLinkResolvedMedia(
                requestUrl = sourceUrl,
                suggestedProjectName = title,
                items = items,
                sourceHost = expandedHost,
                isDirectMedia = false
            )
        )
    }

    private fun resolveFinalUrl(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
        }
        return connection.useAndDisconnect {
            connection.inputStream.use { /* force redirects and final URL */ }
            connection.url.toString()
        }
    }

    private fun httpGetText(url: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            setRequestProperty("Accept-Encoding", "gzip")
        }
        return connection.useAndDisconnect {
            val code = responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code while loading $url")
            }
            val rawStream = BufferedInputStream(inputStream)
            val bodyStream = if (contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }
            InputStreamReader(bodyStream, Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }
    }

    private fun extractPageTitle(html: String): String? {
        val title = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
            ?: return null
        return title.removeSuffix("_哔哩哔哩_bilibili").trim().ifBlank { null }
    }

    private fun extractPlayInfoJson(html: String): String? {
        val playInfoMatch = PLAY_INFO_REGEX.find(html) ?: return null
        return playInfoMatch.groupValues.getOrNull(1)?.trim()
    }

    private fun parseBilibiliPlayInfo(playInfoJson: String): List<PlatformLinkResolvedItem> {
        val root = runCatching { JSONObject(playInfoJson) }.getOrNull() ?: return emptyList()
        val data = root.optJSONObject("data") ?: return emptyList()
        val dash = data.optJSONObject("dash")
        val items = mutableListOf<PlatformLinkResolvedItem>()

        parseDurlItems(data.optJSONArray("durl"), items)
        if (dash != null) {
            parseDashAudioItems(dash.optJSONArray("audio"), items)
            parseDashVideoItems(dash.optJSONArray("video"), items)
        }
        return items
    }

    private fun parseDashAudioItems(
        audioArray: JSONArray?,
        collector: MutableList<PlatformLinkResolvedItem>
    ) {
        if (audioArray == null) return
        val count = minOf(audioArray.length(), 3)
        repeat(count) { index ->
            val item = audioArray.optJSONObject(index) ?: return@repeat
            val url = item.optString("baseUrl")
                .ifBlank { item.optString("base_url") }
                .ifBlank { firstBackupUrl(item) }
            if (url.isBlank()) return@repeat
            val bandwidth = item.optLong("bandwidth", -1L)
            val kbps = if (bandwidth > 0) (bandwidth / 1000.0).roundToInt() else null
            collector += PlatformLinkResolvedItem(
                id = "audio_${item.optLong("id", index.toLong())}",
                label = if (kbps != null) "Audio ${kbps}kbps" else "Audio ${index + 1}",
                resolvedMediaUrl = url,
                mimeType = item.optString("mimeType").ifBlank { "audio/mp4" },
                estimatedBytes = null
            )
        }
    }

    private fun parseDashVideoItems(
        videoArray: JSONArray?,
        collector: MutableList<PlatformLinkResolvedItem>
    ) {
        if (videoArray == null) return
        val count = minOf(videoArray.length(), 2)
        repeat(count) { index ->
            val item = videoArray.optJSONObject(index) ?: return@repeat
            val url = item.optString("baseUrl")
                .ifBlank { item.optString("base_url") }
                .ifBlank { firstBackupUrl(item) }
            if (url.isBlank()) return@repeat
            val height = item.optInt("height", -1)
            collector += PlatformLinkResolvedItem(
                id = "video_${item.optLong("id", index.toLong())}",
                label = if (height > 0) "Video ${height}p" else "Video ${index + 1}",
                resolvedMediaUrl = url,
                mimeType = item.optString("mimeType").ifBlank { "video/mp4" },
                estimatedBytes = null
            )
        }
    }

    private fun firstBackupUrl(item: JSONObject): String {
        val backupArray = item.optJSONArray("backupUrl")
            ?: item.optJSONArray("backup_url")
            ?: return ""
        repeat(backupArray.length()) { index ->
            val url = backupArray.optString(index).trim()
            if (url.isNotBlank()) {
                return url
            }
        }
        return ""
    }

    private fun parseDurlItems(
        durlArray: JSONArray?,
        collector: MutableList<PlatformLinkResolvedItem>
    ) {
        if (durlArray == null) return
        val count = minOf(durlArray.length(), 2)
        repeat(count) { index ->
            val item = durlArray.optJSONObject(index) ?: return@repeat
            val url = item.optString("url")
            if (url.isBlank()) return@repeat
            val size = item.optLong("size", -1L).takeIf { it > 0L }
            collector += PlatformLinkResolvedItem(
                id = "durl_$index",
                label = "Default ${index + 1}",
                resolvedMediaUrl = url,
                mimeType = guessMimeType(url),
                estimatedBytes = size
            )
        }
    }

    private fun parseYouTubeStreamingItems(playerResponse: JSONObject): List<PlatformLinkResolvedItem> {
        val streamingData = playerResponse.optJSONObject("streamingData") ?: return emptyList()
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        val formats = streamingData.optJSONArray("formats")
        val collector = mutableListOf<PlatformLinkResolvedItem>()
        val addedUrls = linkedSetOf<String>()
        parseYouTubeFormatArray(
            array = adaptiveFormats,
            collector = collector,
            addedUrls = addedUrls,
            audioLimit = 6,
            videoLimit = 2
        )
        parseYouTubeFormatArray(
            array = formats,
            collector = collector,
            addedUrls = addedUrls,
            audioLimit = 2,
            videoLimit = 1
        )
        return collector
    }

    private fun parseYouTubeFormatArray(
        array: JSONArray?,
        collector: MutableList<PlatformLinkResolvedItem>,
        addedUrls: MutableSet<String>,
        audioLimit: Int,
        videoLimit: Int
    ) {
        if (array == null) return
        var audioCount = 0
        var videoCount = 0
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            val resolvedUrl = resolveYouTubeFormatUrl(item)
            if (resolvedUrl.isBlank() || !addedUrls.add(resolvedUrl)) {
                return@repeat
            }
            val mimeType = item.optString("mimeType")
                .substringBefore(';')
                .trim()
                .ifBlank { guessMimeType(resolvedUrl) }
            val bandwidth = item.optLong("bitrate", -1L)
            val estimatedBytes = item.optString("contentLength")
                .toLongOrNull()
                ?: estimateBytesFromBitrate(
                    bitrate = bandwidth,
                    durationMs = item.optString("approxDurationMs").toLongOrNull()
                )
            if (mimeType?.startsWith("audio/") == true) {
                if (audioCount >= audioLimit) return@repeat
                audioCount += 1
                val kbps = if (bandwidth > 0) (bandwidth / 1000.0).roundToInt() else null
                collector += PlatformLinkResolvedItem(
                    id = "yt_audio_${item.optInt("itag", index)}",
                    label = if (kbps != null) "Audio ${kbps}kbps" else "Audio ${audioCount}",
                    resolvedMediaUrl = resolvedUrl,
                    mimeType = mimeType,
                    estimatedBytes = estimatedBytes
                )
            } else if (mimeType?.startsWith("video/") == true) {
                if (videoCount >= videoLimit) return@repeat
                videoCount += 1
                val quality = item.optString("qualityLabel").ifBlank { "Video ${videoCount}" }
                collector += PlatformLinkResolvedItem(
                    id = "yt_video_${item.optInt("itag", index)}",
                    label = quality,
                    resolvedMediaUrl = resolvedUrl,
                    mimeType = mimeType,
                    estimatedBytes = estimatedBytes
                )
            }
        }
    }

    private fun resolveYouTubeFormatUrl(item: JSONObject): String {
        val directUrl = item.optString("url").trim()
        if (directUrl.isNotBlank()) {
            return directUrl
        }
        val cipher = item.optString("signatureCipher")
            .ifBlank { item.optString("cipher") }
            .trim()
        if (cipher.isBlank()) {
            return ""
        }
        val params = parseFormEncodedParams(cipher)
        val url = params["url"].orEmpty()
        if (url.isBlank()) {
            return ""
        }
        if (!params["s"].isNullOrBlank()) {
            return ""
        }
        val signature = params["sig"] ?: params["signature"]
        val sp = params["sp"].orEmpty()
        if (signature.isNullOrBlank() || sp.isBlank()) {
            return url
        }
        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator$sp=$signature"
    }

    private fun parseFormEncodedParams(raw: String): Map<String, String> {
        return raw.split('&')
            .mapNotNull { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = token.substring(0, idx)
                val value = token.substring(idx + 1)
                key to URLDecoder.decode(value, Charsets.UTF_8.name())
            }
            .toMap()
    }

    private fun estimateBytesFromBitrate(bitrate: Long, durationMs: Long?): Long? {
        if (bitrate <= 0 || durationMs == null || durationMs <= 0L) {
            return null
        }
        return (bitrate * durationMs) / 8_000L
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase().orEmpty()
        if (host == "youtu.be") {
            val pathId = parsed.path.orEmpty().trim('/').substringBefore('/')
            return pathId.ifBlank { null }
        }
        if (host.endsWith("youtube.com")) {
            val path = parsed.path.orEmpty()
            if (path == "/watch") {
                val queryParams = parseUrlQueryParams(parsed.rawQuery.orEmpty())
                return queryParams["v"]?.trim()?.ifBlank { null }
            }
            if (path.startsWith("/shorts/")) {
                return path.removePrefix("/shorts/").substringBefore('/').ifBlank { null }
            }
            if (path.startsWith("/live/")) {
                return path.removePrefix("/live/").substringBefore('/').ifBlank { null }
            }
            if (path.startsWith("/embed/")) {
                return path.removePrefix("/embed/").substringBefore('/').ifBlank { null }
            }
        }
        return null
    }

    private fun parseUrlQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) {
            return emptyMap()
        }
        return rawQuery.split('&')
            .mapNotNull { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = URLDecoder.decode(token.substring(0, idx), Charsets.UTF_8.name())
                val value = URLDecoder.decode(token.substring(idx + 1), Charsets.UTF_8.name())
                key to value
            }
            .toMap()
    }

    private fun extractYouTubePlayerResponseJson(html: String): String? {
        return extractBalancedJsonAfterMarker(html, "ytInitialPlayerResponse = ")
            ?: extractBalancedJsonAfterMarker(html, "var ytInitialPlayerResponse = ")
    }

    private fun extractBalancedJsonAfterMarker(html: String, marker: String): String? {
        val startIndex = html.indexOf(marker)
        if (startIndex < 0) return null
        val jsonStart = html.indexOf('{', startIndex + marker.length)
        if (jsonStart < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (index in jsonStart until html.length) {
            val ch = html[index]
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\') {
                escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (inString) {
                continue
            }
            if (ch == '{') {
                depth += 1
            } else if (ch == '}') {
                depth -= 1
                if (depth == 0) {
                    return html.substring(jsonStart, index + 1)
                }
            }
        }
        return null
    }

    companion object {
        private val DIRECT_MEDIA_SUFFIXES = listOf(
            ".mp3",
            ".m4a",
            ".aac",
            ".wav",
            ".flac",
            ".ogg",
            ".opus",
            ".mp4",
            ".m4v",
            ".mov",
            ".mkv",
            ".webm",
            ".3gp"
        )

        private val BILIBILI_HOSTS = setOf(
            "www.bilibili.com",
            "m.bilibili.com",
            "b23.tv"
        )

        private val DOUYIN_HOSTS = setOf(
            "www.douyin.com",
            "v.douyin.com",
            "www.iesdouyin.com"
        )

        private val YOUTUBE_HOSTS = setOf(
            "www.youtube.com",
            "m.youtube.com",
            "youtube.com",
            "music.youtube.com",
            "youtu.be"
        )

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        private val TITLE_REGEX = Regex("(?is)<title[^>]*>(.*?)</title>")
        private val PLAY_INFO_REGEX = Regex(
            "(?s)window\\.__playinfo__\\s*=\\s*(\\{.*?\\})\\s*</script>"
        )
    }
}

private inline fun <T> HttpURLConnection.useAndDisconnect(
    block: HttpURLConnection.() -> T
): T {
    return try {
        block(this)
    } finally {
        disconnect()
    }
}

private fun String.defaultDisplayName(): String {
    val uri = runCatching { URI(this) }.getOrNull()
    val host = uri?.host.orEmpty()
    val path = uri?.path.orEmpty()
    val tail = path.substringAfterLast('/').ifBlank { "media-link" }
    return if (host.isNotBlank()) {
        "$host/$tail"
    } else {
        "Imported media link"
    }
}
