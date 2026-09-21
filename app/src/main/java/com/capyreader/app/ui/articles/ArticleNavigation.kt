package com.capyreader.app.ui.articles

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.capyreader.app.ui.Route
import com.capyreader.app.ui.summaries.SummariesScreen

fun NavGraphBuilder.articleGraph(
    navController: NavController,
    pendingArticleID: String? = null,
    onPendingArticleSelected: () -> Unit = {},
    onNavigateToSummaries: () -> Unit = {},
    onOpenArticle: (String) -> Unit = {},
) {
    composable<Route.Articles> {
        ArticleScreen(
            pendingArticleID = pendingArticleID,
            onPendingArticleSelected = onPendingArticleSelected,
            onNavigateToSettings = {
                navController.navigate(Route.Settings) {
                    launchSingleTop = true
                }
            },
            onNavigateToSummaries = onNavigateToSummaries,
        )
    }
    composable<Route.Summaries> {
        SummariesScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onOpenArticle = { articleID ->
                navController.popBackStack(Route.Articles, inclusive = false)
                onOpenArticle(articleID)
            },
        )
    }
}
