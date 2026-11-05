package com.shinjikai.dictionary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shinjikai.dictionary.integration.ANKIDROID_PERMISSION
import com.shinjikai.dictionary.integration.AnkiAddResult
import com.shinjikai.dictionary.integration.AnkiExporter
import com.shinjikai.dictionary.integration.AnkiNoteContent
import com.shinjikai.dictionary.ui.AppRoute
import com.shinjikai.dictionary.ui.DetailSource
import com.shinjikai.dictionary.ui.Screen
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import com.shinjikai.dictionary.ui.buildDetailRoute
import com.shinjikai.dictionary.ui.buildSearchRoute
import com.shinjikai.dictionary.ui.toScreen
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShinjikaiApp(
    externalSearchTerm: String? = null,
    externalDeepLink: String? = null,
    onExternalSearchTermConsumed: () -> Unit = {},
    onExternalDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val viewModel: ShinjikaiViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = backStackEntry?.destination?.route.toScreen()
    val appName = stringResource(id = R.string.app_name)
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appVersionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "v${info.versionName ?: "?"} ($code)"
        }.getOrDefault("v1.0")
    }
    val pickOfflineZipLauncher = rememberLauncherForActivityResult(
        contract = OpenZipDocumentContract
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importOfflineDictionaryFromUri(uri)
        }
    }
    val exportBookmarksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportBookmarksToCsv(uri)
    }
    val japaneseTextToSpeech = rememberJapaneseTextToSpeechController()

    LaunchedEffect(externalSearchTerm) {
        val incoming = externalSearchTerm?.trim().orEmpty()
        if (incoming.isNotBlank()) {
            navController.navigateToPrimary(AppRoute.Search, buildSearchRoute(incoming))
            onExternalSearchTermConsumed()
        }
    }

    LaunchedEffect(externalDeepLink) {
        val incoming = externalDeepLink?.trim().orEmpty()
        if (incoming.isNotBlank()) {
            runCatching {
                navController.navigate(Uri.parse(incoming))
            }.onFailure {
                navController.navigateToPrimary(AppRoute.Search)
            }
            onExternalDeepLinkConsumed()
        }
    }

    ShinjikaiTheme(settings = settings, supportsDynamicColor = supportsDynamicColor) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val handleSearchTabClick = {
                if (currentScreen == Screen.Search) {
                    viewModel.focusSearchField()
                } else {
                    focusManager.clearFocus()
                    navController.navigateToPrimary(AppRoute.Search)
                    viewModel.focusSearchField()
                }
            }
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Search.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        composable(
                            route = "${AppRoute.Search.route}?${AppRoute.SEARCH_QUERY_ARG}={${AppRoute.SEARCH_QUERY_ARG}}",
                            arguments = listOf(
                                navArgument(AppRoute.SEARCH_QUERY_ARG) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            ),
                            deepLinks = listOf(
                                navDeepLink {
                                    uriPattern = "shinjikai://app/search"
                                },
                                navDeepLink {
                                    uriPattern = "https://shinjikai.app/search"
                                },
                                navDeepLink {
                                    uriPattern = "shinjikai://app/search?${AppRoute.SEARCH_QUERY_ARG}={${AppRoute.SEARCH_QUERY_ARG}}"
                                },
                                navDeepLink {
                                    uriPattern = "https://shinjikai.app/search?${AppRoute.SEARCH_QUERY_ARG}={${AppRoute.SEARCH_QUERY_ARG}}"
                                }
                            ),
                            enterTransition = { primaryEnterTransition() },
                            exitTransition = { primaryExitTransition() },
                            popEnterTransition = { primaryPopEnterTransition() },
                            popExitTransition = { primaryPopExitTransition() }
                            ) { entry ->
                            val incomingQuery = entry.arguments?.getString(AppRoute.SEARCH_QUERY_ARG)?.trim().orEmpty()
                            LaunchedEffect(incomingQuery) {
                                if (incomingQuery.isNotBlank() && incomingQuery != viewModel.activeResultQuery) {
                                    viewModel.runSearchForTerm(incomingQuery)
                                }
                            }
                            SearchScreenContent(
                                appName = appName,
                                useOfflineMode = settings.useOfflineMode,
                                hasOfflineDictionary = viewModel.settingsUiState.offlineTermCount > 0,
                                searchFocusNonce = viewModel.searchFocusNonce,
                                viewModel = viewModel,
                                uiState = viewModel.searchUiState,
                                searchResults = viewModel.searchResults,
                                onRetryBundledImport = viewModel::retryBundledDictionaryInstall,
                                onOpenDetails = {
                                    focusManager.clearFocus()
                                    viewModel.prefetchDetails(it.id)
                                    navController.navigate(buildDetailRoute(it.id))
                                },
                                onSpeakJapanese = { text ->
                                    japaneseTextToSpeech.speak(
                                        text = text,
                                        utteranceId = "landing-word-$text",
                                        onUnavailable = {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.detail_japanese_audio_unavailable),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            )
                        }
                        composable(AppRoute.Browse.route,
                            enterTransition = { primaryEnterTransition() },
                            exitTransition = { primaryExitTransition() },
                            popEnterTransition = { primaryPopEnterTransition() },
                            popExitTransition = { primaryPopExitTransition() }
                        ) {
                            BrowseScreenContent(
                                viewModel = viewModel,
                                browseFlow = viewModel.browsePagingFlow,
                                totalEntries = viewModel.settingsUiState.offlineTermCount,
                                onOpenDetails = {
                                    focusManager.clearFocus()
                                    viewModel.prefetchDetails(it.id)
                                    navController.navigate(buildDetailRoute(it.id))
                                }
                            )
                        }
                        composable(AppRoute.History.route,
                            enterTransition = { primaryEnterTransition() },
                            exitTransition = { primaryExitTransition() },
                            popEnterTransition = { primaryPopEnterTransition() },
                            popExitTransition = { primaryPopExitTransition() }
                        ) {
                            HistoryScreenContent(
                                uiState = viewModel.searchUiState,
                                viewModel = viewModel,
                                onOpenHistoryTerm = { historyTerm ->
                                    focusManager.clearFocus()
                                    navController.navigateToPrimary(AppRoute.Search, buildSearchRoute(historyTerm))
                                }
                            )
                        }
                        composable(AppRoute.Bookmarks.route,
                            enterTransition = { primaryEnterTransition() },
                            exitTransition = { primaryExitTransition() },
                            popEnterTransition = { primaryPopEnterTransition() },
                            popExitTransition = { primaryPopExitTransition() }
                        ) {
                            BookmarksScreenContent(
                                viewModel = viewModel,
                                uiState = viewModel.bookmarksUiState,
                                bookmarkFlow = viewModel.bookmarkPagingFlow,
                                onExportBookmarks = {
                                    exportBookmarksLauncher.launch(viewModel.suggestBookmarkExportFileName())
                                },
                                onOpenBookmarkDetails = {
                                    focusManager.clearFocus()
                                    navController.navigate(
                                        buildDetailRoute(
                                            wordId = it.id,
                                            source = DetailSource.Bookmark
                                        )
                                    )
                                }
                            )
                        }
                        composable(AppRoute.Settings.route,
                            enterTransition = { primaryEnterTransition() },
                            exitTransition = { primaryExitTransition() },
                            popEnterTransition = { primaryPopEnterTransition() },
                            popExitTransition = { primaryPopExitTransition() }
                        ) {
                            SettingsScreenContent(
                                appVersionLabel = appVersionLabel,
                                supportsDynamicColor = supportsDynamicColor,
                                uiState = viewModel.settingsUiState,
                                viewModel = viewModel,
                                onOpenLocalDictionary = {
                                    focusManager.clearFocus()
                                    navController.navigate(AppRoute.LocalDictionary.route)
                                },
                                onOpenAnkiExporterSettings = {
                                    focusManager.clearFocus()
                                    navController.navigate(AppRoute.AnkiExporterSettings.route)
                                }
                            )
                        }
                        composable(
                            AppRoute.LocalDictionary.route,
                            enterTransition = { detailEnterTransition() },
                            exitTransition = { detailExitTransition() },
                            popEnterTransition = { detailPopEnterTransition() },
                            popExitTransition = { detailPopExitTransition() }
                        ) {
                            LocalDictionaryScreenContent(
                                uiState = viewModel.settingsUiState,
                                onPickOfflineZip = {
                                    pickOfflineZipLauncher.launch(Unit)
                                },
                                onToggleDictionary = viewModel::setDictionaryEnabled,
                                onDeleteDictionary = viewModel::deleteDictionary,
                                onGoBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            AppRoute.Category.route,
                            enterTransition = { detailEnterTransition() },
                            exitTransition = { detailExitTransition() },
                            popEnterTransition = { detailPopEnterTransition() },
                            popExitTransition = { detailPopExitTransition() }
                        ) {
                            CategoryResultsScreen(
                                categoryName = viewModel.categoryScreenName.orEmpty(),
                                results = viewModel.categoryResults,
                                onOpenDetails = {
                                    focusManager.clearFocus()
                                    viewModel.prefetchDetails(it.id)
                                    navController.navigate(buildDetailRoute(it.id))
                                },
                                onGoBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            AppRoute.AnkiExporterSettings.route,
                            enterTransition = { detailEnterTransition() },
                            exitTransition = { detailExitTransition() },
                            popEnterTransition = { detailPopEnterTransition() },
                            popExitTransition = { detailPopExitTransition() }
                        ) {
                            AnkiExporterSettingsScreenContent(
                                selectedDeckName = viewModel.settingsUiState.settings.selectedAnkiDeckName,
                                onSelectDeck = viewModel::setSelectedAnkiDeckName,
                                onGoBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "${AppRoute.Detail.route}/{${AppRoute.DETAIL_WORD_ID_ARG}}?${AppRoute.DETAIL_SOURCE_ARG}={${AppRoute.DETAIL_SOURCE_ARG}}",
                            arguments = listOf(
                                navArgument(AppRoute.DETAIL_WORD_ID_ARG) {
                                    type = NavType.IntType
                                },
                                navArgument(AppRoute.DETAIL_SOURCE_ARG) {
                                    type = NavType.StringType
                                    defaultValue = DetailSource.Online.value
                                }
                            ),
                            deepLinks = listOf(
                                navDeepLink {
                                    uriPattern = "shinjikai://app/word/{${AppRoute.DETAIL_WORD_ID_ARG}}"
                                },
                                navDeepLink {
                                    uriPattern = "https://shinjikai.app/word/{${AppRoute.DETAIL_WORD_ID_ARG}}"
                                }
                            ),
                            enterTransition = { detailEnterTransition() },
                            exitTransition = { detailExitTransition() },
                            popEnterTransition = { detailPopEnterTransition() },
                            popExitTransition = { detailPopExitTransition() }
                        ) { entry ->
                            val wordId = entry.arguments?.getInt(AppRoute.DETAIL_WORD_ID_ARG) ?: 0
                            val source = entry.arguments?.getString(AppRoute.DETAIL_SOURCE_ARG)
                                ?.let { value -> DetailSource.entries.firstOrNull { it.value == value } }
                                ?: DetailSource.Online
                            LaunchedEffect(wordId, source) {
                                if (wordId != 0) {
                                    when (source) {
                                        DetailSource.Bookmark -> viewModel.openBookmarkedDetailsById(wordId)
                                        DetailSource.Online -> viewModel.openDetailsById(wordId)
                                    }
                                }
                            }
                            DetailScreenContent(
                                useOfflineMode = settings.useOfflineMode,
                                selectedAnkiDeckName = settings.selectedAnkiDeckName,
                                loadJapaneseTextToSpeech = japaneseTextToSpeech::await,
                                onSpeakJapaneseText = { text, utteranceId, unavailableMessage ->
                                    japaneseTextToSpeech.speak(
                                        text = text,
                                        utteranceId = utteranceId,
                                        onUnavailable = {
                                            Toast.makeText(
                                                context,
                                                unavailableMessage,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                },
                                clipboardManager = clipboardManager,
                                focusManager = focusManager,
                                viewModel = viewModel,
                                onGoBack = { navController.popBackStack() },
                                onOpenCategorySearch = { categoryId, categoryName ->
                                    viewModel.openCategory(categoryId, categoryName)
                                    navController.navigate(AppRoute.Category.route)
                                },
                                onOpenGlossaryReference = { referenceId ->
                                    viewModel.prefetchDetails(referenceId)
                                    navController.navigate(buildDetailRoute(referenceId))
                                },
                                onOpenRelatedWord = { relatedItem ->
                                    if (relatedItem.wordId > 0) {
                                        viewModel.prefetchDetails(relatedItem.wordId)
                                        navController.navigate(buildDetailRoute(relatedItem.wordId))
                                    } else {
                                        val lookupTerm = relatedItem.text.ifBlank { relatedItem.kana }.trim()
                                        if (lookupTerm.isNotBlank()) {
                                            navController.navigate(buildSearchRoute(lookupTerm)) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    if (
                        currentScreen in setOf(Screen.Search, Screen.Browse, Screen.History, Screen.Bookmarks, Screen.Settings) &&
                        !(currentScreen == Screen.Bookmarks && viewModel.isBookmarkEditMode)
                    ) {
                        PrimaryBottomBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            currentScreen = currentScreen ?: Screen.Search,
                            onSearchClick = handleSearchTabClick,
                            onBrowseClick = {
                                focusManager.clearFocus()
                                navController.navigateToPrimary(AppRoute.Browse)
                            },
                            onHistoryClick = {
                                focusManager.clearFocus()
                                navController.navigateToPrimary(AppRoute.History)
                            },
                            onBookmarksClick = {
                                focusManager.clearFocus()
                                navController.navigateToPrimary(AppRoute.Bookmarks)
                            },
                            onSettingsClick = {
                                focusManager.clearFocus()
                                navController.navigateToPrimary(AppRoute.Settings)
                            }
                        )
                    }

                    if (viewModel.settingsUiState.showIntroduction) {
                        IntroductionScreen(
                            entryCount = viewModel.settingsUiState.offlineTermCount,
                            onFinish = viewModel::dismissIntroduction,
                            onOpenOfflineSetup = {
                                viewModel.dismissIntroduction()
                                navController.navigateToPrimary(AppRoute.Settings)
                            }
                        )
                        }
                    }
                }
        }
    }
}

private data class IntroPage(
    val titleRes: Int,
    val bodyRes: Int
)

private val IntroPages = listOf(
    IntroPage(R.string.intro_page_1_title, R.string.intro_page_1_body),
    IntroPage(R.string.intro_page_2_title, R.string.intro_page_2_body),
    IntroPage(R.string.intro_page_3_title, R.string.intro_page_3_body)
)

private val IntroTileShape = RoundedCornerShape(22.dp)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun IntroductionScreen(
    entryCount: Int,
    onFinish: () -> Unit,
    onOpenOfflineSetup: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { IntroPages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == IntroPages.lastIndex
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "深",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                }
                if (!isLastPage) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(IntroPages.lastIndex) }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.intro_action_skip),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)
                ) {
                    when (page) {
                        0 -> IntroSearchPreview()
                        1 -> IntroWordPreview()
                        else -> IntroOfflinePreview(entryCount = entryCount)
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.intro_step, page + 1, IntroPages.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                        Text(
                            text = stringResource(IntroPages[page].titleRes),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 36.sp),
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = stringResource(IntroPages[page].bodyRes),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 26.sp),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Laid out LTR with the indices reversed so slide 1 is always the rightmost dot,
            // matching the right-to-left page order.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                IntroPages.indices.reversed().forEach { index ->
                    val selected = index == currentPage
                    val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "introDot")
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.primary else colors.outlineVariant)
                    )
                }
            }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoBack = currentPage > 0
                    val backAlpha by animateFloatAsState(if (canGoBack) 1f else 0.35f, label = "introBackAlpha")
                    Surface(
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                        },
                        enabled = canGoBack,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer { alpha = backAlpha },
                        shape = CircleShape,
                        color = colors.surface,
                        contentColor = colors.onSurface,
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_nav_back),
                                contentDescription = stringResource(R.string.intro_action_back),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (isLastPage) {
                                onFinish()
                            } else {
                                coroutineScope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        shape = ShinjikaiUi.PillShape
                    ) {
                        Text(
                            text = stringResource(if (isLastPage) R.string.intro_action_start else R.string.intro_action_next),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_intro_forward),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isLastPage) {
                    OutlinedButton(
                        onClick = onOpenOfflineSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = ShinjikaiUi.PillShape,
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.intro_action_offline_setup),
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroTile(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = IntroTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun IntroSearchPreview() {
    val colors = MaterialTheme.colorScheme
    val samples = listOf("猫", "قطة", "neko")
    val labels = listOf("日本語", "عربي", "rōmaji")
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            index = (index + 1) % samples.size
        }
    }
    Column(
        modifier = Modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IntroTile(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(colors.background, ShinjikaiUi.PillShape)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_search),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = samples[index],
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = colors.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 2.dp, height = 20.dp)
                            .background(colors.primary)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    labels.forEachIndexed { i, label ->
                        val selected = i == index
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(ShinjikaiUi.PillShape)
                                .background(if (selected) colors.primaryContainer else Color.Transparent)
                                .border(1.dp, if (selected) Color.Transparent else colors.outlineVariant, ShinjikaiUi.PillShape)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        IntroTile(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The headword layout fills whatever width it is given, so it takes the
                // remaining space (weight) after the meaning text is measured.
                HeadwordFuriganaText(
                    text = "猫",
                    reading = "ねこ",
                    parts = null,
                    modifier = Modifier.weight(1f),
                    baseStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    rubyStyle = MaterialTheme.typography.labelSmall,
                    // Headword layout is forced LTR, so End = right edge (start of the RTL row).
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "قِطّ، قِطّة",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun IntroWordPreview() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IntroTile(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeadwordFuriganaText(
                    text = "勉強",
                    reading = "べんきょう",
                    parts = null,
                    modifier = Modifier.fillMaxWidth(),
                    baseStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    rubyStyle = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IntroPill(
                        text = stringResource(R.string.detail_pronounce),
                        iconRes = R.drawable.ic_detail_speaker,
                        container = colors.primary,
                        content = colors.onPrimary,
                        modifier = Modifier.weight(1.2f)
                    )
                    IntroPill(
                        text = stringResource(R.string.detail_saved),
                        iconRes = R.drawable.ic_nav_bookmarks_filled,
                        container = colors.primaryContainer,
                        content = colors.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IntroTile(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.intro_preview_meaning),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "دراسة، تعلُّم",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
            }
            IntroTile(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_example_fallback),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        RubyTextLayout(
                            segments = listOf(
                                RubyTextSegment(base = "毎日", ruby = "まいにち"),
                                RubyTextSegment(base = "勉強", ruby = "べんきょう"),
                                RubyTextSegment(base = "する。", ruby = null)
                            ),
                            baseStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            rubyStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
                            baseColor = { colors.onSurface },
                            rubyColor = { colors.onSurfaceVariant },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroOfflinePreview(entryCount: Int) {
    val colors = MaterialTheme.colorScheme
    val entryLabel = remember(entryCount) {
        java.text.NumberFormat.getIntegerInstance().format(entryCount)
    }
    Column(
        modifier = Modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IntroTile(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_intro_offline),
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.intro_preview_offline_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.intro_preview_offline_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (entryCount > 0) {
                IntroTile(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = entryLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.intro_preview_entries),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            IntroTile(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_download),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.intro_preview_yomitan),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPill(
    text: String,
    iconRes: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .background(container, ShinjikaiUi.PillShape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DetailScreenContent(
    useOfflineMode: Boolean,
    selectedAnkiDeckName: String,
    loadJapaneseTextToSpeech: suspend () -> TextToSpeech?,
    onSpeakJapaneseText: (text: String, utteranceId: String, unavailableMessage: String) -> Unit,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    focusManager: androidx.compose.ui.focus.FocusManager,
    viewModel: ShinjikaiViewModel,
    onGoBack: () -> Unit,
    onOpenCategorySearch: (Int, String) -> Unit,
    onOpenGlossaryReference: (Int) -> Unit,
    onOpenRelatedWord: (com.shinjikai.dictionary.data.RelatedWordItem) -> Unit
) {
    val context = LocalContext.current
    val detailState = viewModel.detailUiState
    val item = detailState.selectedItem
    val addToAnkiLabel = stringResource(R.string.detail_add_to_anki)
    val addToAnkiSuccessMessage = stringResource(R.string.detail_add_to_anki_success)
    val addToAnkiFailedMessage = stringResource(R.string.detail_add_to_anki_failed)
    val addToAnkiOpenedShareMessage = stringResource(R.string.detail_add_to_anki_opened_share)
    val addToAnkiNotInstalledMessage = stringResource(R.string.detail_add_to_anki_not_installed)
    val addToAnkiNoAudioMessage = stringResource(R.string.detail_add_to_anki_success_no_audio)
    val coroutineScope = rememberCoroutineScope()
    val ankiNote = remember(useOfflineMode, detailState.selectedItem, detailState.details) {
        buildDetailAnkiNoteContent(
            useOfflineMode = useOfflineMode,
            detailState = detailState
        )
    }
    var pendingAnkiNote by remember { mutableStateOf<AnkiNoteContent?>(null) }
    var ankiAddedItemId by remember { mutableStateOf<Int?>(null) }
    var isAddingToAnki by remember { mutableStateOf(false) }
    LaunchedEffect(item?.id) {
        if (item?.id != ankiAddedItemId) {
            ankiAddedItemId = null
        }
        isAddingToAnki = false
    }
    fun handleAnkiResult(result: AnkiAddResult, selectedItemId: Int?) {
        when (result) {
            is AnkiAddResult.Added -> {
                ankiAddedItemId = selectedItemId
                Toast.makeText(
                    context,
                    if (result.hasAudio) addToAnkiSuccessMessage else addToAnkiNoAudioMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
            AnkiAddResult.OpenedShareFallback ->
                Toast.makeText(context, addToAnkiOpenedShareMessage, Toast.LENGTH_SHORT).show()
            AnkiAddResult.AnkiNotInstalled ->
                Toast.makeText(context, addToAnkiNotInstalledMessage, Toast.LENGTH_SHORT).show()
            else ->
                Toast.makeText(context, addToAnkiFailedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    fun launchAnkiExport(note: AnkiNoteContent, selectedItemId: Int?) {
        coroutineScope.launch {
            isAddingToAnki = true
            val textToSpeech = if (
                note.speechText.isNotBlank() &&
                AnkiExporter.canRequestDirectAdd(context) &&
                AnkiExporter.hasDatabasePermission(context)
            ) {
                loadJapaneseTextToSpeech()
            } else {
                null
            }
            val result = AnkiExporter.addNote(
                context = context,
                note = note,
                deckName = selectedAnkiDeckName,
                textToSpeech = textToSpeech,
                canSpeakJapanese = textToSpeech != null
            )
            isAddingToAnki = false
            handleAnkiResult(result, selectedItemId)
        }
    }
    val ankiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingNote = pendingAnkiNote
        pendingAnkiNote = null
        if (pendingNote != null) {
            if (granted) {
                launchAnkiExport(pendingNote, item?.id)
            } else {
                AnkiExporter.shareToAnki(context, pendingNote)
                        .also { handleAnkiResult(it, item?.id) }
            }
        }
    }

    val detailActions = DetailActions(
        isBookmarked = detailState.isBookmarked,
        onToggleBookmark = { item?.let(viewModel::toggleBookmark) },
        isAddedToAnki = item?.id != null && item.id == ankiAddedItemId,
        isAddingToAnki = isAddingToAnki,
        onAddToAnki = {
            val note = ankiNote
            if (note == null) {
                Toast.makeText(context, addToAnkiFailedMessage, Toast.LENGTH_SHORT).show()
            } else {
                when {
                    AnkiExporter.canRequestDirectAdd(context) &&
                        !AnkiExporter.hasDatabasePermission(context) -> {
                        pendingAnkiNote = note
                        ankiPermissionLauncher.launch(ANKIDROID_PERMISSION)
                    }
                    else -> launchAnkiExport(note, item?.id)
                }
            }
        },
        onGoBack = onGoBack
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        DetailScreenBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp),
            useOfflineMode = useOfflineMode,
            detailState = detailState,
            onSpeakJapaneseText = onSpeakJapaneseText,
            context = context,
            clipboardManager = clipboardManager,
            focusManager = focusManager,
            viewModel = viewModel,
            onOpenCategorySearch = onOpenCategorySearch,
            onOpenGlossaryReference = onOpenGlossaryReference,
            onOpenRelatedWord = onOpenRelatedWord,
            actions = detailActions
        )
    }
}

private object OpenZipDocumentContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
    }
}

private fun NavHostController.navigateToPrimary(route: AppRoute, targetRoute: String = route.route) {
    val startDestinationId = graph.startDestinationId
    navigate(targetRoute) {
        if (startDestinationId != 0) {
            popUpTo(startDestinationId) {
                saveState = true
            }
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun primaryRouteIndex(screen: Screen?): Int = when (screen) {
    Screen.Browse -> 0
    Screen.History -> 1
    Screen.Search -> 2
    Screen.Bookmarks -> 3
    Screen.Settings -> 4
    else -> 2
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primarySlideDirection(): AnimatedContentTransitionScope.SlideDirection {
    val initialIndex = primaryRouteIndex(initialState.destination.route.toScreen())
    val targetIndex = primaryRouteIndex(targetState.destination.route.toScreen())
    return if (targetIndex >= initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryEnterTransition() =
    slideIntoContainer(
        towards = primarySlideDirection(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 500f
        ),
        initialOffset = { distance -> distance / 7 }
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = 190,
            delayMillis = 35,
            easing = LinearOutSlowInEasing
        )
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryExitTransition() =
    slideOutOfContainer(
        towards = primarySlideDirection(),
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        targetOffset = { distance -> distance / 9 }
    ) + fadeOut(animationSpec = tween(durationMillis = 135))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryPopEnterTransition() =
    primaryEnterTransition()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryPopExitTransition() =
    primaryExitTransition()

private fun AnimatedContentTransitionScope<*>.detailEnterTransition() =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(160)
    ) + fadeIn(animationSpec = tween(140))

private fun AnimatedContentTransitionScope<*>.detailExitTransition() =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(140)
    ) + fadeOut(animationSpec = tween(100))

private fun AnimatedContentTransitionScope<*>.detailPopEnterTransition() =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(160)
    ) + fadeIn(animationSpec = tween(140))

private fun AnimatedContentTransitionScope<*>.detailPopExitTransition() =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(140)
    ) + fadeOut(animationSpec = tween(100))
