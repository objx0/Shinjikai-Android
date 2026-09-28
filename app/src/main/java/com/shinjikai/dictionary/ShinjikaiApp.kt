package com.shinjikai.dictionary

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
                                if (wordId > 0) {
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
                                    viewModel.runCategorySearch(categoryId, categoryName)
                                    navController.navigate(AppRoute.Search.route) {
                                        launchSingleTop = true
                                    }
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

                    if (currentScreen in setOf(Screen.Search, Screen.Browse, Screen.History, Screen.Bookmarks, Screen.Settings)) {
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun IntroductionScreen(
    onFinish: () -> Unit,
    onOpenOfflineSetup: () -> Unit
) {
    val onboardingPages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Default.Search,
                eyebrowRes = R.string.intro_page_1_eyebrow,
                titleRes = R.string.intro_page_1_title,
                bodyRes = R.string.intro_page_1_body
            ),
            OnboardingPage(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                eyebrowRes = R.string.intro_page_2_eyebrow,
                titleRes = R.string.intro_page_2_title,
                bodyRes = R.string.intro_page_2_body
            ),
            OnboardingPage(
                icon = Icons.Default.DownloadForOffline,
                eyebrowRes = R.string.intro_page_3_eyebrow,
                titleRes = R.string.intro_page_3_title,
                bodyRes = R.string.intro_page_3_body
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == onboardingPages.lastIndex

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.intro_app_name),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = stringResource(R.string.intro_title),
                style = MaterialTheme.typography.headlineMedium
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) { page ->
                IntroPageCard(page = onboardingPages[page], accentIndex = page)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                onboardingPages.forEachIndexed { index, _ ->
                    val selected = index == currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (selected) 28.dp else 10.dp, height = 10.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            if (isLastPage) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_start))
                }
                OutlinedButton(
                    onClick = onOpenOfflineSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_offline_setup))
                }
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(currentPage + 1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_next))
                }
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.intro_action_skip))
                }
            }
        }
    }
}

@Composable
private fun IntroPageCard(
    page: OnboardingPage,
    accentIndex: Int
) {
    val accentContainer = when (accentIndex % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val accentContent = when (accentIndex % 3) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        shape = ShinjikaiUi.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = ShinjikaiUi.cardBorder(),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = accentContent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(page.eyebrowRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            Text(
                text = stringResource(page.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val eyebrowRes: Int,
    val titleRes: Int,
    val bodyRes: Int
)

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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onGoBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    val isBookmarked = detailState.isBookmarked
                    val isAddedToAnki = item?.id != null && item.id == ankiAddedItemId
                    FilledTonalIconButton(
                        onClick = {
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
                                    else -> {
                                        launchAnkiExport(note, item?.id)
                                    }
                                }
                            }
                        },
                        enabled = !isAddingToAnki,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isAddedToAnki) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isAddedToAnki) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isAddedToAnki) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = addToAnkiLabel
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { item?.let(viewModel::toggleBookmark) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isBookmarked) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isBookmarked) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.nav_bookmarks)
                        )
                    }
                },
                colors = shinjikaiTopAppBarColors()
            )
        }
    ) { padding ->
        DetailScreenBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            useOfflineMode = useOfflineMode,
            detailState = detailState,
            onSpeakJapaneseText = onSpeakJapaneseText,
            context = context,
            clipboardManager = clipboardManager,
            focusManager = focusManager,
            viewModel = viewModel,
            onOpenCategorySearch = onOpenCategorySearch,
            onOpenGlossaryReference = onOpenGlossaryReference,
            onOpenRelatedWord = onOpenRelatedWord
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
