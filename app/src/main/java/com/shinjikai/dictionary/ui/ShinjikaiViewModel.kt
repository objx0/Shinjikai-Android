package com.shinjikai.dictionary.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.shinjikai.dictionary.R
import com.shinjikai.dictionary.data.AppDatabase
import com.shinjikai.dictionary.data.AppSettings
import com.shinjikai.dictionary.data.AppThemeMode
import com.shinjikai.dictionary.data.BookmarkItem
import com.shinjikai.dictionary.data.BookmarkRepository
import com.shinjikai.dictionary.data.BrowsePagingSource
import com.shinjikai.dictionary.data.BundledDictionaryInstaller
import com.shinjikai.dictionary.data.DictionarySource
import com.shinjikai.dictionary.data.InstalledDictionaryRecord
import com.shinjikai.dictionary.data.InstalledDictionaryStore
import com.shinjikai.dictionary.data.LocalYomitanSource
import com.shinjikai.dictionary.data.OFFLINE_IMAGE_DIR_META_KEY
import com.shinjikai.dictionary.data.RecentSearchStore
import com.shinjikai.dictionary.data.RelatedWordItem
import com.shinjikai.dictionary.data.RawShinjikaiImporter
import com.shinjikai.dictionary.data.RecentSearchEntry
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.data.SearchPagingSource
import com.shinjikai.dictionary.data.SearchRequestSpec
import com.shinjikai.dictionary.data.SettingsStore
import com.shinjikai.dictionary.data.ShinjikaiRepository
import com.shinjikai.dictionary.data.WordDetailsResponse
import com.shinjikai.dictionary.data.Writing
import com.shinjikai.dictionary.data.YomitanMetaEntity
import com.shinjikai.dictionary.data.YomitanImporter
import com.shinjikai.dictionary.integration.BookmarkCsvExporter
import com.shinjikai.dictionary.data.detectOfflineArchiveKind
import com.shinjikai.dictionary.data.extractOfflineArchive
import com.shinjikai.dictionary.data.extractZipStream
import com.shinjikai.dictionary.data.findOfflineImportPayload
import com.shinjikai.dictionary.data.replaceStagedDirectoryAtomically
import com.shinjikai.dictionary.data.stageDirectoryCopy
import com.shinjikai.dictionary.data.stageDirectoryMergedCopy
import com.shinjikai.dictionary.data.toBrowseSearchItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ShinjikaiViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context
        get() = getApplication<Application>().applicationContext
    private val database = AppDatabase.getInstance(context)
    private val settingsStore = SettingsStore(context)
    private val installedDictionaryStore = InstalledDictionaryStore(context)
    private val recentSearchStore = RecentSearchStore(context)
    private val bookmarkRepository = BookmarkRepository(database.bookmarkDao(), database.yomitanDao())
    private val bundledDictionaryInstaller = BundledDictionaryInstaller(context, database)
    private val searchSpec = MutableStateFlow<SearchRequestSpec?>(null)
    private val searchRefreshNonce = MutableStateFlow(0)
    private var detailsLoadJob: Job? = null
    private var dictionaryInstallJob: Job? = null
    private var isOfflineDataOperationActive = false
    private val detailPrefetches = mutableMapOf<Int, Deferred<Result<WordDetailsResponse>>>()
    private var categoriesPreloadJob: Job? = null
    private val bookmarkMutationMutex = Mutex()
    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.readCached())

    private var dictionarySource: DictionarySource = createDictionarySource()
        set(value) {
            field = value
            repository = ShinjikaiRepository(source = value)
            categoriesPreloadJob?.cancel()
            categoriesPreloadJob = null
            categoryNameById = emptyMap()
            invalidateSearchAndBrowsePaging()
        }

    private var repository: ShinjikaiRepository = ShinjikaiRepository(source = dictionarySource)

    val searchResults: Flow<PagingData<SearchItem>> = combine(searchSpec, searchRefreshNonce) { spec, _ -> spec }
        .flatMapLatest { spec ->
            if (spec == null || spec.mode == ResultMode.None) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 30,
                        prefetchDistance = 15,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = { SearchPagingSource(repository = repository, spec = spec) }
                ).flow
            }
        }
        .cachedIn(viewModelScope)

    val bookmarkPagingFlow: Flow<PagingData<BookmarkItem>> =
        bookmarkRepository.pagedFlow().cachedIn(viewModelScope)

    private var bookmarkedIds by mutableStateOf<Set<Int>>(emptySet())

    val browsePagingFlow: Flow<PagingData<SearchItem>> = searchRefreshNonce
        .flatMapLatest {
            Pager(
                config = PagingConfig(
                    pageSize = 30,
                    initialLoadSize = 30,
                    prefetchDistance = 10,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { BrowsePagingSource(database.yomitanDao()) }
            ).flow
        }
        .cachedIn(viewModelScope)

    private val yomitanImporter = YomitanImporter(database)
    private val rawShinjikaiImporter = RawShinjikaiImporter(database)
    var searchFocusNonce by mutableStateOf(0)
        private set

    private data class OfflineImportResult(
        val importedCount: Int = 0,
        val installedImages: Boolean = false
    )

    var term by mutableStateOf("")
    var activeResultQuery by mutableStateOf("")
    var loadingDetails by mutableStateOf(false)
    var detailsError by mutableStateOf<String?>(null)
    var details by mutableStateOf<WordDetailsResponse?>(null)
    var selectedItem by mutableStateOf<SearchItem?>(null)
    private var detailsOpenedFromBookmarks by mutableStateOf(false)

    private var introDismissedThisSession by mutableStateOf(false)

    var activeCategoryId by mutableStateOf<Int?>(null)
    var activeCategoryName by mutableStateOf<String?>(null)
    var categoryNameById by mutableStateOf<Map<Int, String>>(emptyMap())

    val bookmarkedItems = mutableStateListOf<BookmarkItem>()

    var isBookmarkEditMode by mutableStateOf(false)
    var selectedBookmarkIds by mutableStateOf<Set<Int>>(emptySet())
    var pendingBookmarkDeletionIds by mutableStateOf<Set<Int>?>(null)

    val recentSearches = mutableStateListOf<RecentSearchEntry>().apply {
        addAll(recentSearchStore.readCached())
    }
    val offlinePreviewItems = mutableStateListOf<SearchItem>()

    var isImportingOfflineData by mutableStateOf(false)
    var offlineImportError by mutableStateOf(false)
    var offlineImportProgress by mutableStateOf(0f)
    var offlineImportPhase by mutableStateOf<String?>(null)
    var offlineImportStatus by mutableStateOf<String?>(null)
    var offlineLastImportEpochMs by mutableStateOf<Long?>(null)
    var offlineTermCount by mutableStateOf(0)
    var isOfflineDictionaryStateLoaded by mutableStateOf(false)
        private set
    var offlineLastImportSource by mutableStateOf<String?>(null)
    var installedDictionaries by mutableStateOf(installedDictionaryStore.read())
    val searchUiState: SearchUiState
        get() = SearchUiState(
            term = term,
            activeCategoryId = activeCategoryId,
            activeCategoryName = activeCategoryName,
            resultMode = searchSpec.value?.mode ?: ResultMode.None,
            recentSearches = recentSearches.toList(),
            offlinePreviewItems = offlinePreviewItems.toList(),
            isImportingOfflineData = isImportingOfflineData,
            offlineImportError = offlineImportError,
            offlineImportStatus = offlineImportStatus,
            offlineImportPhase = offlineImportPhase,
            offlineTermCount = offlineTermCount,
            isOfflineDictionaryStateLoaded = isOfflineDictionaryStateLoaded
        )

    val detailUiState: DetailUiState
        get() = DetailUiState(
            loading = loadingDetails,
            error = detailsError,
            details = details,
            selectedItem = selectedItem,
            isBookmarked = selectedItem?.let { item ->
                bookmarkedIds.contains(item.id)
            } == true,
            categoryNameById = categoryNameById,
            isImportingOfflineData = isImportingOfflineData,
            offlineImportPhase = offlineImportPhase
        )

    val bookmarksUiState: BookmarksUiState
        get() = BookmarksUiState(
            items = bookmarkedItems.toList(),
            isEditMode = isBookmarkEditMode,
            selectedIds = selectedBookmarkIds,
            pendingDeletionIds = pendingBookmarkDeletionIds,
            useOfflineMode = settings.value.useOfflineMode,
            activeCategoryId = activeCategoryId
        )

    val settingsUiState: SettingsUiState
        get() = SettingsUiState(
            settings = settings.value,
            showIntroduction = !settings.value.hasSeenIntroduction && !introDismissedThisSession,
            isImportingOfflineData = isImportingOfflineData,
            offlineImportError = offlineImportError,
            offlineImportProgress = offlineImportProgress,
            offlineImportPhase = offlineImportPhase,
            offlineImportStatus = offlineImportStatus,
            offlineLastImportEpochMs = offlineLastImportEpochMs,
            offlineTermCount = offlineTermCount,
            isOfflineDictionaryStateLoaded = isOfflineDictionaryStateLoaded,
            offlineLastImportSource = offlineLastImportSource,
            installedDictionaries = installedDictionaries.map { record ->
                InstalledDictionaryUiItem(
                    id = record.id,
                    name = record.name,
                    source = record.source,
                    fileName = record.fileName,
                    sourceKey = record.sourceKey,
                    enabled = record.enabled
                )
            }.let { records ->
                listOf(
                    InstalledDictionaryUiItem(
                        id = "bundled-1selxo-shinjikai-jsonl",
                        name = "Shinjikai",
                        source = context.getString(R.string.settings_bundled_dictionary_source),
                        sourceKey = BundledDictionaryInstaller.BUNDLED_SOURCE_LABEL,
                        isBuiltIn = true
                    )
                ) + records.filterNot { it.id == "bundled-1selxo-shinjikai-jsonl" }
            }
        )

    init {
        installBundledDictionaryIfNeeded()
        viewModelScope.launch { settingsStore.ensureOfflineMode() }
        refreshOfflineTermCount(waitForBundledInstall = true)
        refreshOfflinePreview()

        viewModelScope.launch {
            settings.collect {
                requestCategoriesPreload()
            }
        }

        viewModelScope.launch {
            bookmarkedItems.clear()
            bookmarkedItems.addAll(bookmarkRepository.getAll())
        }

        viewModelScope.launch {
            bookmarkRepository.observeBookmarkedIds().collect { ids ->
                bookmarkedIds = ids
            }
        }

        viewModelScope.launch {
            recentSearchStore.recentSearchesFlow.collect { stored ->
                if (recentSearches.toList() != stored) {
                    recentSearches.clear()
                    recentSearches.addAll(stored)
                }
            }
        }
    }

    fun focusSearchField() {
        searchFocusNonce += 1
    }

    fun clearCategorySearch() {
        activeCategoryId = null
        activeCategoryName = null
        term = ""
        searchSpec.value = null
    }

    fun runSearchForTerm(rawTerm: String) {
        val query = rawTerm.trim()

        if (query.isBlank()) {
            if (
                term.isBlank() &&
                activeCategoryId == null &&
                activeCategoryName == null &&
                activeResultQuery.isBlank() &&
                searchSpec.value == null
            ) {
                return
            }
            activeCategoryId = null
            activeCategoryName = null
            activeResultQuery = ""
            term = ""
            searchSpec.value = null
            return
        }

        val nextSpec = SearchRequestSpec(
            mode = ResultMode.Search,
            query = query
        )
        if (
            activeCategoryId == null &&
            activeCategoryName == null &&
            activeResultQuery == query &&
            term == query &&
            searchSpec.value == nextSpec
        ) {
            return
        }

        activeCategoryId = null
        activeCategoryName = null
        activeResultQuery = query
        rememberRecentSearch(query)
        term = query
        searchSpec.value = nextSpec
    }

    fun runSearch() {
        runSearchForTerm(term)
    }

    fun runCategorySearch(categoryId: Int, categoryName: String) {
        val nextSpec = SearchRequestSpec(
            mode = ResultMode.Category,
            categoryId = categoryId,
            categoryName = categoryName
        )
        if (
            activeCategoryId == categoryId &&
            activeCategoryName == categoryName &&
            term == categoryName &&
            activeResultQuery == categoryName &&
            searchSpec.value == nextSpec
        ) {
            return
        }

        activeCategoryId = categoryId
        activeCategoryName = categoryName
        term = categoryName
        activeResultQuery = categoryName
        searchSpec.value = nextSpec
    }

    private fun openDetailsInternal(item: SearchItem) {
        detailsLoadJob?.cancel()
        detailsOpenedFromBookmarks = false
        selectedItem = item
        details = null
        detailsError = null
        loadingDetails = true

        detailsLoadJob = viewModelScope.launch {
            val result = takePrefetchedDetails(item.id)?.await()
                ?: repository.loadWordDetails(item.id)
            loadingDetails = false
            result.onSuccess { details = it }
                .onFailure { detailsError = it.message ?: context.getString(R.string.error_details_load) }
            requestCategoriesPreload()
        }
    }

    fun openDetails(item: SearchItem) {
        openDetailsInternal(item)
    }

    fun prefetchDetails(id: Int) {
        if (id <= 0) return
        synchronized(detailPrefetches) {
            if (detailPrefetches[id]?.isActive == true) return
            detailPrefetches[id] = viewModelScope.async {
                repository.loadWordDetails(id)
            }
        }
    }

    fun openBookmarkedDetails(item: SearchItem) {
        detailsLoadJob?.cancel()
        detailsOpenedFromBookmarks = true
        selectedItem = item
        details = null
        detailsError = null
        loadingDetails = true
        detailsLoadJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                bookmarkRepository.getSavedDetails(item.id)
            }
            if (cached != null) {
                details = cached
            } else {
                // Older database migrations could leave bookmarks without detailsJson.
                // Recover from the installed local dictionary when possible.
                repository.loadWordDetails(item.id)
                    .onSuccess { details = it }
                    .onFailure {
                        detailsError = context.getString(R.string.error_bookmark_not_available_offline)
                    }
            }
            loadingDetails = false
        }
    }

    fun retryDetailsLoad() {
        val item = selectedItem ?: return
        if (detailsOpenedFromBookmarks) {
            detailsLoadJob?.cancel()
            details = null
            detailsError = null
            loadingDetails = true
            detailsLoadJob = viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    bookmarkRepository.getSavedDetails(item.id)
                }
                if (cached != null) {
                    details = cached
                } else {
                    repository.loadWordDetails(item.id)
                        .onSuccess { details = it }
                        .onFailure {
                            detailsError = context.getString(R.string.error_bookmark_not_available_offline)
                        }
                }
                loadingDetails = false
            }
        } else {
            openDetailsInternal(item)
        }
    }

    fun openBookmarkedDetailsById(id: Int) {
        val bookmarkItem = bookmarkedItems.firstOrNull { it.id == id }?.item ?: SearchItem(id = id)
        openBookmarkedDetails(bookmarkItem)
    }

    fun openDetailsById(id: Int) {
        openDetails(SearchItem(id = id))
    }

    fun openOnlineGlossaryReference(id: Int) {
        if (id <= 0) return
        detailsLoadJob?.cancel()
        detailsOpenedFromBookmarks = false
        selectedItem = SearchItem(id = id)
        details = null
        detailsError = null
        loadingDetails = true

        detailsLoadJob = viewModelScope.launch {
            dictionaryInstallJob?.join()
            val result = takePrefetchedDetails(id)?.await()
                ?: repository.loadWordDetails(id)
            loadingDetails = false
            result.onSuccess { details = it }
                .onFailure { detailsError = it.message ?: context.getString(R.string.error_details_load) }
            requestCategoriesPreload()
        }
    }

    fun openDetailsByRelatedItem(relatedItem: RelatedWordItem) {
        if (relatedItem.wordId > 0) {
            openDetailsById(relatedItem.wordId)
        } else {
            val lookupTerm = relatedItem.text.ifBlank { relatedItem.kana }.trim()
            if (lookupTerm.isNotEmpty()) {
                term = lookupTerm
                runSearchForTerm(lookupTerm)
            }
        }
    }

    fun toggleBookmark(item: SearchItem) {
        viewModelScope.launch {
            bookmarkMutationMutex.withLock {
                val isBookmarked = bookmarkedIds.contains(item.id)
                if (isBookmarked) {
                    bookmarkRepository.deleteById(item.id)
                    bookmarkedItems.removeAll { it.id == item.id }
                } else {
                    val existingDetails = details?.takeIf { it.word.id == item.id }
                    val detailsResult = existingDetails?.let { Result.success(it) }
                        ?: repository.loadWordDetails(item.id)

                    detailsResult.onSuccess { response ->
                        val existingCreatedAt = bookmarkedItems.firstOrNull { it.id == item.id }?.createdAt
                            ?: java.util.Date().time
                        val word = response.word
                        val summaryFromDetails = word.meanings
                            .asSequence()
                            .map { it.arabic.trim() }
                            .firstOrNull { it.isNotBlank() }
                            .orEmpty()

                        val normalizedItem = SearchItem(
                            id = word.id,
                            kana = word.kana,
                            writings = word.writings,
                            meaningSummary = item.meaningSummary.ifBlank { summaryFromDetails },
                            jlpt = word.jlpt,
                            difficulty = word.difficulty
                        )

                        bookmarkRepository.upsertWithDetails(normalizedItem, response)
                        bookmarkedItems.removeAll { it.id == normalizedItem.id }
                        bookmarkedItems.add(
                            0,
                            BookmarkItem(item = normalizedItem, createdAt = existingCreatedAt)
                        )
                    }.onFailure { throwable ->
                        Toast.makeText(
                            context,
                            throwable.message ?: context.getString(R.string.error_bookmark_save),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun requestDeleteBookmarks(ids: Set<Int>) {
        pendingBookmarkDeletionIds = ids
    }

    fun updateBookmarkEditMode(enabled: Boolean) {
        isBookmarkEditMode = enabled
        if (!enabled) {
            selectedBookmarkIds = emptySet()
        }
    }

    fun toggleBookmarkSelection(id: Int) {
        selectedBookmarkIds = if (selectedBookmarkIds.contains(id)) {
            selectedBookmarkIds - id
        } else {
            selectedBookmarkIds + id
        }
    }

    fun pruneBookmarkSelection(validIds: Set<Int>) {
        selectedBookmarkIds = selectedBookmarkIds.intersect(validIds)
    }

    fun deleteBookmarks(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookmarkRepository.deleteByIds(ids)
            bookmarkedItems.removeAll { it.id in ids }
            pendingBookmarkDeletionIds = null
            selectedBookmarkIds = emptySet()
        }
    }

    fun suggestBookmarkExportFileName(): String = BookmarkCsvExporter.suggestedFileName()

    fun exportBookmarksToCsv(uri: Uri) {
        viewModelScope.launch {
            val exported = runCatching {
                val bookmarks = withContext(Dispatchers.IO) { bookmarkRepository.getAll() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(BookmarkCsvExporter.toCsv(bookmarks))
                    } ?: error("Could not open export destination")
                }
                bookmarks.size
            }
            val message = exported.fold(
                onSuccess = { count -> context.getString(R.string.bookmarks_export_success, count) },
                onFailure = { context.getString(R.string.bookmarks_export_failure) }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun clearRecentSearches() {
        recentSearches.clear()
        viewModelScope.launch { recentSearchStore.save(emptyList()) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDarkMode(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseDynamicColor(enabled) }
    }

    fun openRandomDictionaryEntry(onEntry: (SearchItem) -> Unit) {
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) {
                database.yomitanDao()
                    .loadRandomTerm()
                    ?.toBrowseSearchItem()
            }
            item?.let(onEntry)
        }
    }

    fun retryBundledDictionaryInstall() {
        installBundledDictionaryIfNeeded(force = true)
    }

    private fun installBundledDictionaryIfNeeded(force: Boolean = false) {
        if (!bundledDictionaryInstaller.hasBundledDictionary() || isOfflineDataOperationActive) return
        isOfflineDataOperationActive = true
        dictionaryInstallJob = viewModelScope.launch {
            var workStarted = false

            Log.i(TAG, "Starting bundled dictionary install. force=$force")
            val result = bundledDictionaryInstaller.installIfNeeded(force = force) { phase, progress ->
                if (!workStarted) {
                    withContext(Dispatchers.Main.immediate) {
                        workStarted = true
                        isImportingOfflineData = true
                        offlineImportError = false
                        offlineImportStatus = null
                        offlineImportProgress = 0f
                    }
                }
                updateOfflineImportProgress(phase, progress)
            }

            isOfflineDataOperationActive = false
            if (workStarted) {
                isImportingOfflineData = false
                offlineImportPhase = null
            }
            result.onSuccess { installResult ->
                offlineImportError = false
                if (workStarted) offlineImportProgress = 1f
                if (installResult.available && installResult.dictionaryImported) {
                    offlineImportStatus = if (installResult.imagesInstalled) {
                        context.getString(
                            R.string.offline_import_success_with_images,
                            installResult.importedCount
                        )
                    } else {
                        context.getString(
                            R.string.offline_import_success,
                            installResult.importedCount
                        )
                    }
                    categoryNameById = emptyMap()
                    refreshOfflineTermCount()
                    refreshOfflinePreview()
                    dictionarySource = createDictionarySource()
                } else if (installResult.available && installResult.imagesInstalled) {
                    offlineImportStatus = context.getString(R.string.offline_images_install_success)
                } else {
                    refreshOfflineTermCount()
                    refreshOfflinePreview()
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Bundled dictionary install failed", throwable)
                offlineImportError = true
                offlineImportStatus = throwable.toImportFailureMessage(
                    context.getString(R.string.offline_import_failure)
                )
            }
        }
    }

    fun setSelectedAnkiDeckName(name: String) {
        viewModelScope.launch { settingsStore.setSelectedAnkiDeckName(name) }
    }

    fun dismissIntroduction() {
        if (settings.value.hasSeenIntroduction || introDismissedThisSession) return
        introDismissedThisSession = true
        viewModelScope.launch {
            runCatching { settingsStore.setHasSeenIntroduction(true) }
                .onFailure { introDismissedThisSession = false }
        }
    }

    fun dismissIntroductionAndOpenSettings() {
        dismissIntroduction()
    }

    fun showIntroductionAgain() {
        introDismissedThisSession = false
        viewModelScope.launch { settingsStore.setHasSeenIntroduction(false) }
    }

    fun refreshOfflineTermCount(waitForBundledInstall: Boolean = false) {
        viewModelScope.launch {
            if (waitForBundledInstall) dictionaryInstallJob?.join()
            val snapshot = runCatching {
                withContext(Dispatchers.IO) {
                    val dao = database.yomitanDao()
                    val count = dao.countTerms()
                    val epoch = dao.getMetaValue("last_import_epoch_ms")?.toLongOrNull()
                    val source = dao.getMetaValue("last_import_source")
                    Triple(count, epoch, source)
                }
            }
            snapshot.onSuccess { values ->
                offlineTermCount = values.first
                offlineLastImportEpochMs = values.second
                offlineLastImportSource = values.third
            }
            isOfflineDictionaryStateLoaded = true
        }
    }

    fun refreshOfflinePreview() {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                database.yomitanDao().loadPreviewTerms(limit = 6).map { term ->
                    SearchItem(
                        id = term.id,
                        kana = term.reading,
                        writings = listOf(Writing(text = term.expression)),
                        meaningSummary = term.glossary,
                        difficulty = term.difficulty
                    )
                }
            }
            offlinePreviewItems.clear()
            offlinePreviewItems.addAll(preview)
        }
    }

    fun importOfflineDictionaryFromUri(uri: Uri) {
        if (isOfflineDataOperationActive) return
        val archiveName = resolvePickedArchiveName(uri)?.trim().orEmpty()
        if (!archiveName.endsWith(".zip", ignoreCase = true)) {
            offlineImportError = true
            offlineImportStatus = context.getString(R.string.offline_import_invalid_zip)
            return
        }
        isOfflineDataOperationActive = true
        viewModelScope.launch {
            isImportingOfflineData = true
            offlineImportError = false
            offlineImportStatus = null
            offlineImportProgress = 0f
            offlineImportPhase = context.getString(R.string.offline_import_phase_prepare)

            val result = runCatching {
                val imported = importFromPickedArchive(uri)
                offlineImportProgress = 1f
                imported
            }

            isImportingOfflineData = false
            isOfflineDataOperationActive = false
            offlineImportPhase = null
            result.onSuccess { importResult ->
                offlineImportError = false
                offlineImportStatus = when {
                    importResult.importedCount > 0 && importResult.installedImages ->
                        context.getString(R.string.offline_import_success_with_images, importResult.importedCount)
                    importResult.importedCount > 0 ->
                        context.getString(R.string.offline_import_success, importResult.importedCount)
                    importResult.installedImages ->
                        context.getString(R.string.offline_images_install_success)
                    else ->
                        context.getString(R.string.offline_import_failure)
                }
                categoryNameById = emptyMap()
                refreshOfflineTermCount()
                registerInstalledDictionary(uri)
                refreshOfflinePreview()
                // Any PagingSource that loaded during the import may have cached an
                // empty first page. Recreate it now that the new source is committed.
                dictionarySource = createDictionarySource()
                if (settings.value.useOfflineMode) {
                    viewModelScope.launch { ensureCategoriesPreloadedIfNeeded() }
                }
            }.onFailure { throwable ->
                offlineImportError = true
                offlineImportStatus = throwable.toImportFailureMessage(
                    context.getString(R.string.offline_import_failure)
                )
            }
        }
    }

    fun setDictionaryEnabled(dictionaryId: String, enabled: Boolean) {
        if (dictionaryId == BundledDictionaryInstaller.BUNDLED_SOURCE_LABEL) return
        val item = installedDictionaries.firstOrNull { it.id == dictionaryId } ?: return
        val sourceKey = item.sourceKey ?: sourceKeyForPickedArchive(item.fileName.orEmpty())
        installedDictionaryStore.setEnabled(dictionaryId, enabled)
        installedDictionaries = installedDictionaryStore.read()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.yomitanDao().setSourceEnabled(sourceKey, enabled)
            }
            invalidateSearchAndBrowsePaging()
        }
    }

    fun deleteDictionary(dictionaryId: String) {
        val item = installedDictionaries.firstOrNull { it.id == dictionaryId } ?: return
        val sourceKey = item.sourceKey ?: sourceKeyForPickedArchive(item.fileName.orEmpty())
        installedDictionaryStore.remove(dictionaryId)
        installedDictionaries = installedDictionaryStore.read()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dao = database.yomitanDao()
                dao.deleteCategoryRefsForSource(sourceKey)
                dao.deleteTermsFtsForSource(sourceKey)
                dao.deleteTermsForSource(sourceKey)
            }
            invalidateSearchAndBrowsePaging()
            refreshOfflineTermCount()
        }
    }

    private fun registerInstalledDictionary(uri: Uri) {
        val fileName = resolvePickedArchiveName(uri)?.trim().orEmpty().ifBlank { "Imported Yomitan dictionary" }
        val id = "yomitan-" + fileName.lowercase().hashCode().toUInt().toString(16)
        val record = InstalledDictionaryRecord(
            id = id,
            name = fileName.substringBeforeLast('.').ifBlank { fileName },
            source = "Yomitan ZIP",
            fileName = fileName,
            sourceKey = sourceKeyForPickedArchive(fileName)
        )
        installedDictionaryStore.add(record)
        installedDictionaries = installedDictionaryStore.read()
    }

    private fun requestCategoriesPreload() {
        if (categoryNameById.isNotEmpty()) return
        if (categoriesPreloadJob?.isActive == true) return
        categoriesPreloadJob = viewModelScope.launch {
            repository.loadCategories().onSuccess { response ->
                categoryNameById = response.categories
                    .associate { it.id to it.name.trim() }
                    .filterValues { it.isNotEmpty() }
            }
        }
    }

    private suspend fun ensureCategoriesPreloadedIfNeeded() {
        requestCategoriesPreload()
        categoriesPreloadJob?.join()
    }

    private suspend fun importFromPickedZip(uri: Uri): OfflineImportResult {
        return withContext(Dispatchers.IO) {
            val targetDir = File(context.cacheDir, "picked-import/extracted")
            try {
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()

                updateOfflineImportProgress(
                    context.getString(R.string.offline_import_phase_index),
                    0.15f
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    extractZipStream(input.buffered(), targetDir)
                } ?: error("Unable to read selected zip.")

                expandNestedOfflineArchives(targetDir)
                processPickedImport(targetDir, pickedSourceLabel(uri, PICKED_ZIP_SHINJIKAI_SOURCE))
            } finally {
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
            }
        }
    }

    private suspend fun importFromPickedArchive(uri: Uri): OfflineImportResult {
        return importFromPickedZip(uri)
    }

    private suspend fun processPickedImport(targetDir: File, sourceLabel: String): OfflineImportResult {
        val payload = findOfflineImportPayload(targetDir)
        val jsonlFile = payload.jsonlFile
        val extractedImagesDir = payload.extractedImagesDir
        val yomitanDirectory = payload.yomitanDirectory

        require(jsonlFile != null || extractedImagesDir != null || yomitanDirectory != null) {
            "Selected archive does not contain dictionary text, Yomitan term banks, or yomitan_images."
        }

        val imageTargetDir = File(context.filesDir, "offline/yomitan_images")
        val stagedImages = if (extractedImagesDir != null) {
            updateOfflineImportProgress(
                context.getString(R.string.offline_import_phase_images),
                0.2f
            )
            stageDirectoryMergedCopy(extractedImagesDir, imageTargetDir)
        } else {
            null
        }

        try {
            val importedCount = if (jsonlFile != null) {
                updateOfflineImportProgress(
                    context.getString(R.string.offline_import_phase_index),
                    0.35f
                )
                rawShinjikaiImporter.importFromJsonLines(
                    jsonlFile = jsonlFile,
                    sourceLabel = sourceLabel
                ).getOrThrow()
            } else if (yomitanDirectory != null) {
                updateOfflineImportProgress(
                    context.getString(R.string.offline_import_phase_index),
                    0.35f
                )
                yomitanImporter.importFromDirectory(
                    directory = yomitanDirectory,
                    sourceLabel = sourceLabel
                ).getOrThrow()
            } else {
                0
            }

            if (stagedImages != null) {
                replaceStagedDirectoryAtomically(stagedImages, imageTargetDir)
                database.yomitanDao().upsertMeta(
                    YomitanMetaEntity(
                        key = OFFLINE_IMAGE_DIR_META_KEY,
                        value = imageTargetDir.absolutePath.replace('\\', '/')
                    )
                )
            }

            return OfflineImportResult(
                importedCount = importedCount,
                installedImages = stagedImages != null
            )
        } finally {
            if (stagedImages?.exists() == true) {
                stagedImages.deleteRecursively()
            }
        }
    }

    private suspend fun updateOfflineImportProgress(
        phase: String,
        progress: Float
    ) {
        withContext(Dispatchers.Main.immediate) {
            offlineImportPhase = phase
            offlineImportProgress = maxOf(offlineImportProgress, progress.coerceIn(0f, 1f))
        }
    }

    private fun resolvePickedArchiveName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
    }

    private fun pickedSourceLabel(uri: Uri, baseLabel: String): String {
        val name = resolvePickedArchiveName(uri).orEmpty().lowercase()
        val suffix = name.ifBlank { uri.toString().lowercase() }.hashCode().toUInt().toString(16)
        return "$baseLabel-$suffix"
    }

    private fun sourceKeyForPickedArchive(fileName: String): String {
        val base = if (fileName.lowercase().endsWith(".tar.xz") || fileName.lowercase().endsWith(".txz")) {
            PICKED_TXZ_SHINJIKAI_SOURCE
        } else {
            PICKED_ZIP_SHINJIKAI_SOURCE
        }
        val suffix = fileName.lowercase().hashCode().toUInt().toString(16)
        return "$base-$suffix"
    }

    private fun expandNestedOfflineArchives(rootDir: File) {
        val expandedArchives = linkedSetOf<String>()
        var passCount = 0
        while (passCount < MAX_NESTED_ARCHIVE_PASSES) {
            var expandedThisPass = false
            passCount += 1

            val archives = rootDir.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        generateSequence(file.parentFile) { it.parentFile }
                            .none { it.name == NESTED_ARCHIVE_DIR_NAME } &&
                        detectOfflineArchiveKind(file.name) != null
                }
                .sortedBy { it.absolutePath.length }
                .toList()

            archives.forEach { archive ->
                val canonicalPath = archive.canonicalPath
                if (!expandedArchives.add(canonicalPath)) return@forEach

                val extractDir = File(archive.parentFile, NESTED_ARCHIVE_DIR_NAME)
                    .resolve(archive.name.replace('.', '_'))
                if (extractDir.exists()) {
                    extractDir.deleteRecursively()
                }
                extractDir.mkdirs()
                extractOfflineArchive(archive, extractDir)
                expandedThisPass = true
            }

            if (!expandedThisPass) break
        }
    }

    private fun Throwable.toImportFailureMessage(defaultMessage: String): String {
        val messages = generateSequence(this) { it.cause }
            .mapNotNull { cause -> cause.message?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
            .toList()

        if (messages.isEmpty()) return defaultMessage

        return messages.firstOrNull { message ->
            !message.contains("cannot rollback - no transaction is active", ignoreCase = true)
        } ?: messages.first()
    }

    private fun rememberRecentSearch(term: String) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        recentSearches.removeAll { it.term.equals(normalized, ignoreCase = true) }
        recentSearches.add(
            0,
            RecentSearchEntry(
                term = normalized,
                searchedAtEpochMs = System.currentTimeMillis()
            )
        )
        while (recentSearches.size > MAX_RECENT_SEARCHES) {
            recentSearches.removeAt(recentSearches.lastIndex)
        }
        val snapshot = recentSearches.toList()
        viewModelScope.launch { recentSearchStore.save(snapshot) }
    }

    fun removeRecentSearch(term: String) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        if (recentSearches.removeAll { it.term.equals(normalized, ignoreCase = true) }) {
            val snapshot = recentSearches.toList()
            viewModelScope.launch { recentSearchStore.save(snapshot) }
        }
    }

    private fun createDictionarySource(): DictionarySource {
        return LocalYomitanSource(database.yomitanDao())
    }

    private fun takePrefetchedDetails(id: Int): Deferred<Result<WordDetailsResponse>>? {
        return synchronized(detailPrefetches) {
            detailPrefetches.remove(id)
        }
    }

    private fun invalidateSearchAndBrowsePaging() {
        searchRefreshNonce.value = searchRefreshNonce.value + 1
    }

    private companion object {
        private const val MAX_RECENT_SEARCHES = 15
        private const val MAX_NESTED_ARCHIVE_PASSES = 3
        private const val NESTED_ARCHIVE_DIR_NAME = "__offline_import_archives__"
        private const val OFFLINE_CATEGORIES_META_KEY = "categories_json"
        private const val PICKED_ZIP_SHINJIKAI_SOURCE = "raw-shinjikai-jp-ar-picked-zip"
        private const val PICKED_TXZ_SHINJIKAI_SOURCE = "raw-shinjikai-jp-ar-picked-tar-xz"
        private const val TAG = "ShinjikaiViewModel"
    }
}
