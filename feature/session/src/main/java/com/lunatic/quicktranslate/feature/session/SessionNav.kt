package com.lunatic.quicktranslate.feature.session

import android.net.Uri

object SessionNav {
    const val route = "session"
    const val uriArg = "uri"
    const val nameArg = "name"
    const val mimeArg = "mime"
    const val durationArg = "duration"

    const val routePattern =
        "$route?$uriArg={$uriArg}&$nameArg={$nameArg}&$mimeArg={$mimeArg}&$durationArg={$durationArg}"

    fun createRoute(media: ImportedSessionMedia): String {
        return buildString {
            append(route)
            append("?")
            append(uriArg)
            append("=")
            append(Uri.encode(media.uri))
            append("&")
            append(nameArg)
            append("=")
            append(Uri.encode(media.displayName))
            append("&")
            append(mimeArg)
            append("=")
            append(Uri.encode(media.mimeType))
            append("&")
            append(durationArg)
            append("=")
            append(media.durationMs)
        }
    }
}
