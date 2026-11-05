package com.shinjikai.dictionary.ui

import android.net.Uri

enum class Screen {
    Search,
    Browse,
    History,
    Detail,
    Bookmarks,
    Settings,
    LocalDictionary,
    AnkiExporterSettings,
    Category
}

enum class DetailSource(val value: String) {
    Online("online"),
    Bookmark("bookmark")
}

enum class AppRoute(val route: String, val screen: Screen) {
    Search("search", Screen.Search),
    Browse("browse", Screen.Browse),
    History("history", Screen.History),
    Detail("detail", Screen.Detail),
    Bookmarks("bookmarks", Screen.Bookmarks),
    Settings("settings", Screen.Settings),
    LocalDictionary("local-dictionary", Screen.LocalDictionary),
    AnkiExporterSettings("anki-exporter-settings", Screen.AnkiExporterSettings),
    Category("category", Screen.Category);

    companion object {
        const val SEARCH_QUERY_ARG = "query"
        const val DETAIL_WORD_ID_ARG = "wordId"
        const val DETAIL_SOURCE_ARG = "source"
    }
}

fun String?.toScreen(): Screen {
    return when {
        this == null -> Screen.Search
        this.startsWith(AppRoute.Detail.route) -> Screen.Detail
        this.startsWith(AppRoute.Search.route) -> Screen.Search
        this.startsWith(AppRoute.Browse.route) -> Screen.Browse
        else -> AppRoute.entries.firstOrNull { it.route == this }?.screen ?: Screen.Search
    }
}

fun buildSearchRoute(query: String? = null): String {
    val normalized = query?.trim().orEmpty()
    return if (normalized.isBlank()) {
        AppRoute.Search.route
    } else {
        "${AppRoute.Search.route}?${AppRoute.SEARCH_QUERY_ARG}=${Uri.encode(normalized)}"
    }
}

fun buildDetailRoute(wordId: Int, source: DetailSource = DetailSource.Online): String {
    return "${AppRoute.Detail.route}/$wordId?${AppRoute.DETAIL_SOURCE_ARG}=${source.value}"
}

enum class ResultMode {
    None,
    Search,
    Category
}
