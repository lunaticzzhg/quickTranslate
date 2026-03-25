package com.lunatic.quicktranslate.feature.session

import android.net.Uri

object SessionNav {
    const val route = "session"
    const val projectIdArg = "projectId"
    const val uriArg = "uri"
    const val nameArg = "name"
    const val mimeArg = "mime"
    const val durationArg = "duration"

    const val routePattern =
        "$route?$projectIdArg={$projectIdArg}&$uriArg={$uriArg}&$nameArg={$nameArg}&$mimeArg={$mimeArg}&$durationArg={$durationArg}"

    fun createRoute(media: ImportedSessionMedia): String {
        return buildString {
            append(route)
            append("?")
            append(projectIdArg)
            append("=")
            append(media.projectId)
            append("&")
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
