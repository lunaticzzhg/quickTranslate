package com.lunatic.quicktranslate.feature.home

data class HomeState(
    val title: String = "QuickTranslate",
    val message: String = "MVI app skeleton is ready for the first feature task.",
    val primaryActionLabel: String = "Import Audio / Video"
)

sealed interface HomeIntent {
    data object PrimaryActionClicked : HomeIntent
}

sealed interface HomeEffect {
    data object ShowImportPlaceholder : HomeEffect
}
