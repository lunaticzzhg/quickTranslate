package com.lunatic.quicktranslate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lunatic.quicktranslate.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUrl = intent.extractSharedHttpUrl()

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavHost(sharedUrl = sharedUrl)
                }
            }
        }
    }
}

private fun Intent?.extractSharedHttpUrl(): String? {
    if (this == null) {
        return null
    }
    if (action != Intent.ACTION_SEND || type != "text/plain") {
        return null
    }
    val raw = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
    if (raw.isBlank()) {
        return null
    }
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
        return null
    }
    return raw
}
