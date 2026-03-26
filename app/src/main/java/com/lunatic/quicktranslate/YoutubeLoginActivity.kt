package com.lunatic.quicktranslate

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.time.Instant

class YoutubeLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    YoutubeLoginScreen(
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeLoginScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "YouTube 登录",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "登录后点击“保存登录信息”，用于下载受限视频。",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                webViewState.value?.loadUrl("https://accounts.google.com/")
            }) {
                Text("Google 登录")
            }
            Button(onClick = {
                webViewState.value?.loadUrl("https://m.youtube.com/")
            }) {
                Text("YouTube 首页")
            }
            Button(onClick = {
                val saved = saveYoutubeCookiesFile(context.filesDir)
                Toast.makeText(
                    context,
                    if (saved != null) "已保存：$saved" else "未获取到登录 Cookie",
                    Toast.LENGTH_LONG
                ).show()
            }) {
                Text("保存登录信息")
            }
            Button(onClick = onClose) {
                Text("关闭")
            }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    webViewClient = WebViewClient()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl("https://m.youtube.com/")
                    webViewState.value = this
                }
            }
        )
    }
}

private fun saveYoutubeCookiesFile(filesDir: File): String? {
    val cookieManager = CookieManager.getInstance()
    val targets = listOf(
        "https://www.youtube.com" to ".youtube.com",
        "https://m.youtube.com" to ".youtube.com",
        "https://accounts.google.com" to ".google.com",
        "https://www.google.com" to ".google.com"
    )
    val expiration = Instant.now().epochSecond + 60L * 60L * 24L * 30L
    val cookieRows = mutableSetOf<String>()
    targets.forEach { (url, domain) ->
        val rawCookies = cookieManager.getCookie(url).orEmpty()
        if (rawCookies.isBlank()) return@forEach
        rawCookies.split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .forEach { kv ->
                val index = kv.indexOf('=')
                if (index <= 0 || index >= kv.length - 1) return@forEach
                val name = kv.substring(0, index).trim()
                val value = kv.substring(index + 1).trim()
                if (name.isBlank() || value.isBlank()) return@forEach
                cookieRows += listOf(
                    domain,
                    "TRUE",
                    "/",
                    "TRUE",
                    expiration.toString(),
                    name,
                    value
                ).joinToString("\t")
            }
    }
    if (cookieRows.isEmpty()) {
        return null
    }
    val target = File(filesDir, "yt/youtube_cookies.txt")
    target.parentFile?.mkdirs()
    val payload = buildString {
        appendLine("# Netscape HTTP Cookie File")
        cookieRows.forEach { row ->
            appendLine(row)
        }
    }
    target.writeText(payload)
    return target.absolutePath
}
