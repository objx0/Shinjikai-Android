package com.shinjikai.dictionary.ui

import com.shinjikai.dictionary.data.AppSettings
import com.shinjikai.dictionary.data.BookmarkItem
import com.shinjikai.dictionary.data.RecentSearchEntry
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.data.WordDetailsResponse

data class InstalledDictionaryUiItem(
    val id: String,
    val name: String,
    val source: String,
    val fileName: String? = null,
    val sourceKey: String? = null,
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true
)

data class SearchUiState(
    val term: String = "",
    val activeCategoryId: Int? = null,
    val activeCategoryName: String? = null,
    val resultMode: ResultMode = ResultMode.None,
    val recentSearches: List<RecentSearchEntry> = emptyList(),
    val offlinePreviewItems: List<SearchItem> = emptyList(),
    val wordOfTheDay: SearchItem? = null,
    val isWordOfTheDayBookmarked: Boolean = false,
    val isImportingOfflineData: Boolean = false,
    val offlineImportError: Boolean = false,
    val offlineImportStatus: String? = null,
    val offlineImportPhase: String? = null,
    val offlineTermCount: Int = 0,
    val isOfflineDictionaryStateLoaded: Boolean = false
)

data class DetailUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val details: WordDetailsResponse? = null,
    val selectedItem: SearchItem? = null,
    val isBookmarked: Boolean = false,
    val categoryNameById: Map<Int, String> = emptyMap(),
    val isImportingOfflineData: Boolean = false,
    val offlineImportPhase: String? = null
)

data class BookmarksUiState(
    val items: List<BookmarkItem> = emptyList(),
    val isEditMode: Boolean = false,
    val selectedIds: Set<Int> = emptySet(),
    val pendingDeletionIds: Set<Int>? = null,
    val useOfflineMode: Boolean = false,
    val activeCategoryId: Int? = null
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val showIntroduction: Boolean = true,
    val isImportingOfflineData: Boolean = false,
    val offlineImportError: Boolean = false,
    val offlineImportProgress: Float = 0f,
    val offlineImportPhase: String? = null,
    val offlineImportStatus: String? = null,
    val offlineLastImportEpochMs: Long? = null,
    val offlineTermCount: Int = 0,
    val isOfflineDictionaryStateLoaded: Boolean = false,
    val offlineLastImportSource: String? = null,
    val installedDictionaries: List<InstalledDictionaryUiItem> = emptyList()
)
