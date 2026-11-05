package com.shinjikai.dictionary

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.ui.ResultMode
import com.shinjikai.dictionary.ui.SearchUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import kotlinx.coroutines.flow.Flow

private val SearchCardShape = RoundedCornerShape(28.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun SearchScreenContent(
    appName: String,
    useOfflineMode: Boolean,
    hasOfflineDictionary: Boolean,
    searchFocusNonce: Int,
    viewModel: ShinjikaiViewModel,
    uiState: SearchUiState,
    searchResults: Flow<PagingData<SearchItem>>,
    onRetryBundledImport: () -> Unit,
    onOpenDetails: (SearchItem) -> Unit,
    onSpeakJapanese: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val lazyResults = searchResults.collectAsLazyPagingItems()
    val resultsListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFieldReady by remember { mutableStateOf(false) }

    val refreshState = lazyResults.loadState.refresh
    val hasActiveSearch = uiState.resultMode != ResultMode.None
    val isRefreshing = hasActiveSearch && refreshState is LoadState.Loading
    val refreshError = (refreshState as? LoadState.Error)?.error?.message?.takeIf { hasActiveSearch }
    val showLanding = !hasActiveSearch && uiState.term.isBlank() && !isRefreshing && refreshError == null
    val showNoResults =
        hasActiveSearch && uiState.term.isNotBlank() && lazyResults.itemCount == 0 && !isRefreshing && refreshError == null
    val offlineDictionaryUnavailable = useOfflineMode && (
        uiState.isImportingOfflineData ||
            (uiState.isOfflineDictionaryStateLoaded && !hasOfflineDictionary)
        )
    val landingSuggestions = remember {
        listOf(
            "猫",
            "学校",
            "食べる",
            "電車",
            "友達",
            "水",
            "先生",
            "日本語",
            "かわいい",
            "勉強",
            "本",
            "旅行",
            "海"
        ).shuffled().take(7)
    }

    LaunchedEffect(searchFocusNonce, isSearchFieldReady) {
        if (searchFocusNonce > 0 && isSearchFieldReady) {
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(uiState.term, uiState.resultMode, uiState.activeCategoryId, useOfflineMode) {
        resultsListState.scrollToItem(0)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchBrandHeader(
                    title = appName,
                    subtitle = if (hasOfflineDictionary) {
                        stringResource(R.string.browse_count, uiState.offlineTermCount)
                    } else {
                        stringResource(R.string.browse_subtitle)
                    }
                )

                if (uiState.activeCategoryName == null) {
                    SearchTopDock(
                        term = uiState.term,
                        activeCategoryName = null,
                        searchFocusNonce = searchFocusNonce,
                        onTermChange = { viewModel.term = it },
                        onRunSearch = {
                            focusManager.clearFocus()
                            viewModel.runSearch()
                        },
                        onClearTerm = { viewModel.runSearchForTerm("") },
                        onClearCategory = { viewModel.clearCategorySearch() },
                        focusRequester = searchFocusRequester,
                        onFieldReady = { isSearchFieldReady = true },
                        recentSearches = if (showLanding) {
                            uiState.recentSearches.take(3).map { it.term }
                        } else {
                            emptyList()
                        },
                        onRecentClick = { recent ->
                            viewModel.term = recent
                            focusManager.clearFocus()
                            viewModel.runSearchForTerm(recent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CategorySearchBanner(
                        label = uiState.activeCategoryName,
                        onClear = { viewModel.clearCategorySearch() }
                    )
                }

                if (isRefreshing) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                refreshError?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }

                if (offlineDictionaryUnavailable) {
                    OfflineDictionaryStateCard(
                        uiState = uiState,
                        onRetry = onRetryBundledImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f))
                } else {
                    AnimatedContent(
                    targetState = useOfflineMode,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val forward = targetState != initialState
                        (
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 240),
                                initialOffsetX = { fullWidth -> if (forward) fullWidth / 10 else -fullWidth / 10 }
                            ) + fadeIn(animationSpec = tween(durationMillis = 220))
                        ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 200),
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth / 12 else fullWidth / 12 }
                            ) + fadeOut(animationSpec = tween(durationMillis = 160))
                        ).using(SizeTransform(clip = false))
                    },
                    label = "searchModeContent"
                ) {
                    when {
                        showLanding -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!useOfflineMode) {
                                    LandingSuggestions(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        appName = appName,
                                        suggestions = landingSuggestions,
                                        onSuggestionClick = { suggestion ->
                                            viewModel.term = suggestion
                                            focusManager.clearFocus()
                                            viewModel.runSearchForTerm(suggestion)
                                        }
                                    )
                                }

                                val wordOfTheDay = uiState.wordOfTheDay
                                if (useOfflineMode && (wordOfTheDay != null || uiState.offlinePreviewItems.isNotEmpty())) {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(
                                            top = 4.dp,
                                            bottom = ShinjikaiUi.BottomBarClearance
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (wordOfTheDay != null) {
                                            item(key = "word-of-the-day", contentType = "word-of-the-day") {
                                                WordOfTheDayCard(
                                                    item = wordOfTheDay,
                                                    isBookmarked = uiState.isWordOfTheDayBookmarked,
                                                    onOpen = { onOpenDetails(wordOfTheDay) },
                                                    onSpeak = {
                                                        onSpeakJapanese(
                                                            wordOfTheDay.kana.ifBlank { wordOfTheDay.primaryWriting }
                                                        )
                                                    },
                                                    onToggleBookmark = { viewModel.toggleBookmark(wordOfTheDay) }
                                                )
                                            }
                                        }
                                        if (uiState.offlinePreviewItems.isNotEmpty()) {
                                            item(key = "landing-picks-header", contentType = "section-header") {
                                                LandingPicksHeader(onShuffle = viewModel::shuffleLandingPicks)
                                            }
                                            item(key = "landing-picks", contentType = "landing-picks") {
                                                LandingPicksCard(
                                                    items = uiState.offlinePreviewItems,
                                                    onOpen = onOpenDetails
                                                )
                                            }
                                        }
                                    }
                                } else if (useOfflineMode) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = appName,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        showNoResults -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ShinjikaiEmptyState(
                                    title = stringResource(R.string.search_no_results),
                                    iconRes = R.drawable.ic_nav_search,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (!useOfflineMode && uiState.activeCategoryId == null) {
                                    LandingSuggestions(
                                        modifier = Modifier.fillMaxWidth(),
                                        appName = appName,
                                        suggestions = landingSuggestions,
                                        title = stringResource(R.string.search_try_other_words),
                                        onSuggestionClick = { suggestion ->
                                            viewModel.term = suggestion
                                            focusManager.clearFocus()
                                            viewModel.runSearchForTerm(suggestion)
                                        }
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = resultsListState,
                                contentPadding = PaddingValues(
                                    top = 4.dp,
                                    bottom = ShinjikaiUi.BottomBarClearance
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (lazyResults.itemCount > 0) {
                                    item(key = "results-label") {
                                        SearchSectionLabel(text = stringResource(R.string.search_results_title))
                                    }
                                }
                                items(
                                    count = lazyResults.itemCount,
                                    key = { index -> lazyResults[index]?.id ?: "result-$index" },
                                    contentType = { "dictionary-entry" }
                                ) { index ->
                                    val item = lazyResults[index] ?: return@items
                                    DictionaryEntryCard(
                                        item = item,
                                        onClick = { onOpenDetails(item) },
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        previewMaxLines = if (uiState.activeCategoryId != null) 1 else 2,
                                        highlighted = index == 0 &&
                                            uiState.activeCategoryId == null &&
                                            uiState.term.isNotBlank()
                                    )
                                }

                                if (lazyResults.loadState.append is LoadState.Loading) {
                                    item(key = "append-loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }

                                (lazyResults.loadState.append as? LoadState.Error)?.let { appendError ->
                                    item(key = "append-error") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            TextButton(onClick = { lazyResults.retry() }) {
                                                Text(appendError.error.message ?: stringResource(R.string.detail_retry))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

        }
    }
}

@Composable
private fun OfflineDictionaryStateCard(
    uiState: SearchUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (uiState.isImportingOfflineData) {
        stringResource(R.string.offline_dictionary_indexing_title)
    } else {
        stringResource(R.string.offline_dictionary_missing_title)
    }
    val message = uiState.offlineImportStatus
        ?: uiState.offlineImportPhase
        ?: if (uiState.isImportingOfflineData) {
            stringResource(R.string.offline_dictionary_indexing_message)
        } else {
            stringResource(R.string.offline_dictionary_missing_message)
        }

    ShinjikaiCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (uiState.offlineImportError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            textAlign = TextAlign.Right
        )
        if (uiState.isImportingOfflineData) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            )
        } else {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(stringResource(R.string.offline_dictionary_retry))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun SearchTopDock(
    term: String,
    activeCategoryName: String?,
    searchFocusNonce: Int,
    onTermChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onClearTerm: () -> Unit,
    onClearCategory: () -> Unit,
    focusRequester: FocusRequester,
    onFieldReady: () -> Unit,
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(searchFocusNonce) {
        if (searchFocusNonce > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val colors = MaterialTheme.colorScheme
    val clearLabel = stringResource(R.string.nav_clear)
    Surface(
        modifier = modifier.padding(vertical = if (isImeVisible) 2.dp else 0.dp),
        shape = SearchCardShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            activeCategoryName?.let {
                CategorySearchBanner(
                    label = it,
                    onClear = onClearCategory
                )
            }

            BasicTextField(
                value = term,
                onValueChange = onTermChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned { onFieldReady() },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurface,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onRunSearch() }),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(colors.background, ShinjikaiUi.PillShape)
                            .padding(start = 16.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_search),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (term.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                        if (term.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceVariant)
                                    .clickable(role = Role.Button, onClick = onClearTerm)
                                    .semantics { contentDescription = clearLabel },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_close),
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )

            if (recentSearches.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.forEach { recent ->
                            RecentSearchChip(term = recent, onClick = { onRecentClick(recent) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchChip(term: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(ShinjikaiUi.PillShape)
            .background(colors.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, colors.outlineVariant, ShinjikaiUi.PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_history),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = term,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSecondaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchBrandHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.app_name_kanji),
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun SearchSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LandingSuggestions(
    modifier: Modifier = Modifier,
    appName: String,
    suggestions: List<String>,
    title: String = stringResource(R.string.search_try_japanese_words),
    onSuggestionClick: (String) -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    ShinjikaiChip(
                        text = suggestion,
                        onClick = { onSuggestionClick(suggestion) },
                        selected = false
                    )
                }
            }
        }
    }
}

/** Big landing card: one common word that stays the same for the whole day. */
@Composable
private fun WordOfTheDayCard(
    item: SearchItem,
    isBookmarked: Boolean,
    onOpen: () -> Unit,
    onSpeak: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val headword = item.primaryWriting.ifBlank { item.kana }
    val mark = remember(headword) { kanjiWatermarkChar(headword) }
    val dateLabel = remember {
        SimpleDateFormat("d MMMM", Locale.forLanguageTag("ar-u-nu-latn")).format(Date())
    }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            mark?.let { KanjiWatermark(mark = it, fontSize = 120.sp) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_word_of_day),
                        modifier = Modifier
                            .background(colors.primaryContainer, ShinjikaiUi.PillShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    HeadwordFuriganaText(
                        text = headword,
                        reading = item.kana,
                        parts = item.writings.firstOrNull()?.parts,
                        modifier = Modifier.weight(1f),
                        baseStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 34.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        rubyStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        rubyColor = colors.primary,
                        textAlign = TextAlign.End,
                        autoFit = true
                    )
                    CommonnessIndicator(difficulty = item.difficulty)
                }
                if (item.meaningSummary.isNotBlank()) {
                    Text(
                        text = forceRtlText(item.meaningSummary),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                        color = colors.onSurface.copy(alpha = 0.86f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LandingPillButton(
                        text = stringResource(R.string.detail_pronounce),
                        iconRes = R.drawable.ic_detail_speaker,
                        primary = true,
                        onClick = onSpeak
                    )
                    LandingPillButton(
                        text = stringResource(if (isBookmarked) R.string.detail_saved else R.string.detail_save),
                        iconRes = if (isBookmarked) R.drawable.ic_nav_bookmarks_filled else R.drawable.ic_nav_bookmarks,
                        primary = false,
                        onClick = onToggleBookmark
                    )
                }
            }
        }
    }
}

@Composable
private fun LandingPillButton(
    text: String,
    iconRes: Int,
    primary: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (primary) colors.onPrimary else colors.onSurface
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(ShinjikaiUi.PillShape)
            .then(
                if (primary) {
                    Modifier.background(colors.primary, ShinjikaiUi.PillShape)
                } else {
                    Modifier.border(1.dp, colors.outlineVariant, ShinjikaiUi.PillShape)
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun LandingPicksHeader(onShuffle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shuffleLabel = stringResource(R.string.search_shuffle_words_a11y)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchSectionLabel(text = stringResource(R.string.search_more_words))
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(ShinjikaiUi.PillShape)
                .background(colors.surface, ShinjikaiUi.PillShape)
                .border(1.dp, colors.outlineVariant, ShinjikaiUi.PillShape)
                .clickable(role = Role.Button, onClickLabel = shuffleLabel, onClick = onShuffle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_browse_shuffle),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.search_shuffle_words),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
        }
    }
}

@Composable
private fun LandingPicksCard(
    items: List<SearchItem>,
    onOpen: (SearchItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            items.forEachIndexed { index, item ->
                val headword = item.primaryWriting.ifBlank { item.kana }
                val reading = item.kana.takeIf { it.isNotBlank() && it != headword }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onOpen(item) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.widthIn(min = 72.dp, max = 160.dp)) {
                        if (reading != null) {
                            Text(
                                text = reading,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = headword,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Default
                            ),
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = forceRtlText(item.meaningSummary),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = colors.outlineVariant
                    )
                }
            }
        }
    }
}
