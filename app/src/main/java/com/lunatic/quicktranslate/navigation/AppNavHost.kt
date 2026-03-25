package com.lunatic.quicktranslate.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lunatic.quicktranslate.feature.home.HomeRoute
import com.lunatic.quicktranslate.feature.home.TranscodeTasksRoute
import com.lunatic.quicktranslate.feature.session.ImportedSessionMedia
import com.lunatic.quicktranslate.feature.session.SessionNav
import com.lunatic.quicktranslate.feature.session.SessionRoute

private const val HOME_ROUTE = "home"
private const val TRANSCODE_TASKS_ROUTE = "transcode_tasks"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE
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
                onNavigateToTranscodeTasks = {
                    navController.navigate(TRANSCODE_TASKS_ROUTE)
                }
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
