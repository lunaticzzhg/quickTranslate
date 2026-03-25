package com.lunatic.quicktranslate.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lunatic.quicktranslate.feature.home.HomeRoute
import com.lunatic.quicktranslate.feature.session.ImportedSessionMedia
import com.lunatic.quicktranslate.feature.session.SessionNav
import com.lunatic.quicktranslate.feature.session.SessionRoute

private const val HOME_ROUTE = "home"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE
    ) {
        composable(route = HOME_ROUTE) {
            HomeRoute(
                onNavigateToSession = { media ->
                    navController.navigate(
                        SessionNav.createRoute(
                            ImportedSessionMedia(
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
