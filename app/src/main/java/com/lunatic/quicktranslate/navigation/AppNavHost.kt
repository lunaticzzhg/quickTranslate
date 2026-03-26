package com.lunatic.quicktranslate.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lunatic.quicktranslate.feature.home.HomeRoute
import com.lunatic.quicktranslate.feature.home.LinkImportRoute
import com.lunatic.quicktranslate.feature.home.TranscodeTasksRoute
import com.lunatic.quicktranslate.feature.session.ImportedSessionMedia
import com.lunatic.quicktranslate.feature.session.SessionNav
import com.lunatic.quicktranslate.feature.session.SessionRoute

private const val HOME_ROUTE = "home"
private const val LINK_IMPORT_ROUTE = "link_import"
private const val LINK_IMPORT_URL_ARG = "initialUrl"
private const val TRANSCODE_TASKS_ROUTE = "transcode_tasks"

@Composable
fun AppNavHost(sharedUrl: String? = null) {
    val navController = rememberNavController()
    val startDestination = if (sharedUrl.isNullOrBlank()) {
        HOME_ROUTE
    } else {
        buildLinkImportRoute(sharedUrl)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = HOME_ROUTE) {
            HomeRoute(
                onNavigateToSession = { projectId, media ->
                    navController.navigate(
                        SessionNav.createRoute(
                            ImportedSessionMedia(
                                projectId = projectId,
                                uri = media.uri,
                                displayName = media.displayName,
                                mimeType = media.mimeType,
                                durationMs = media.durationMs
                            )
                        )
                    )
                },
                onNavigateToLinkImport = {
                    navController.navigate(buildLinkImportRoute())
                },
                onNavigateToTranscodeTasks = {
                    navController.navigate(TRANSCODE_TASKS_ROUTE)
                }
            )
        }

        composable(
            route = "$LINK_IMPORT_ROUTE?$LINK_IMPORT_URL_ARG={$LINK_IMPORT_URL_ARG}",
            arguments = listOf(
                navArgument(LINK_IMPORT_URL_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val initialUrl = entry.arguments?.getString(LINK_IMPORT_URL_ARG).orEmpty()
            LinkImportRoute(
                initialUrl = initialUrl,
                onNavigateBack = { navController.popBackStack() },
                onSubmitUrl = {}
            )
        }

        composable(route = TRANSCODE_TASKS_ROUTE) {
            TranscodeTasksRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { projectId, media ->
                    navController.navigate(
                        SessionNav.createRoute(
                            ImportedSessionMedia(
                                projectId = projectId,
                                uri = media.uri,
                                displayName = media.displayName,
                                mimeType = media.mimeType,
                                durationMs = media.durationMs
                            )
                        )
                    )
                }
            )
        }

        composable(
            route = SessionNav.routePattern,
            arguments = listOf(
                navArgument(SessionNav.projectIdArg) { type = NavType.LongType },
                navArgument(SessionNav.uriArg) { type = NavType.StringType },
                navArgument(SessionNav.nameArg) { type = NavType.StringType },
                navArgument(SessionNav.mimeArg) { type = NavType.StringType },
                navArgument(SessionNav.durationArg) { type = NavType.LongType }
            )
        ) {
            SessionRoute(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private fun buildLinkImportRoute(initialUrl: String? = null): String {
    if (initialUrl.isNullOrBlank()) {
        return LINK_IMPORT_ROUTE
    }
    return "$LINK_IMPORT_ROUTE?$LINK_IMPORT_URL_ARG=${Uri.encode(initialUrl)}"
}
