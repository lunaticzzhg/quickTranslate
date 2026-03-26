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
                return@withContext PlatformLinkResolveResult.Failure(
                    PlatformLinkResolveFailure(
                        type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                        message = "Douyin resolver is not implemented yet."
                    )
                )
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
