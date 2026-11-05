package com.shinjikai.dictionary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.BookmarkItem
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.ui.BookmarksUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun BookmarksScreenContent(
    viewModel: ShinjikaiViewModel,
    uiState: BookmarksUiState,
    bookmarkFlow: Flow<PagingData<BookmarkItem>>,
    onOpenBookmarkDetails: (SearchItem) -> Unit,
    onExportBookmarks: () -> Unit,
) {
    val bookmarks = bookmarkFlow.collectAsLazyPagingItems()
    val locale = Locale.getDefault()
    val fallbackBookmarkLabel = stringResource(R.string.detail_word_fallback)
    val dateFormatter = remember(locale) {
        SimpleDateFormat("MMM dd, yyyy", locale)
    }
    val timeFormatter = remember(locale) {
        SimpleDateFormat("HH:mm", locale)
    }
    val allIds = remember(uiState.items) {
        uiState.items.mapTo(linkedSetOf()) { it.id }
    }

    LaunchedEffect(uiState.isEditMode, allIds) {
        viewModel.pruneBookmarkSelection(if (uiState.isEditMode) allIds else emptySet())
    }

    fun localDateOf(epochMs: Long): String {
        return dateFormatter.format(Date(epochMs))
    }

    fun localTimeLabel(epochMs: Long): String {
        return timeFormatter.format(Date(epochMs))
    }

    val isAllSelected = allIds.isNotEmpty() && uiState.selectedIds.size == allIds.size
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShinjikaiTabHeader(
                title = stringResource(R.string.bookmarks_title),
                subtitle = stringResource(R.string.bookmarks_count, allIds.size),
                kanji = stringResource(R.string.bookmarks_kanji)
            )
            if (!uiState.isEditMode && allIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShinjikaiActionChip(
                        text = stringResource(R.string.bookmarks_export_chip),
                        iconRes = R.drawable.ic_settings_download,
                        onClick = onExportBookmarks
                    )
                    ShinjikaiActionChip(
                        text = stringResource(R.string.bookmarks_select_chip),
                        iconRes = R.drawable.ic_settings_edit,
                        onClick = { viewModel.updateBookmarkEditMode(true) }
                    )
                }
            }
        }
    }

    BackHandler(enabled = uiState.isEditMode) {
        viewModel.updateBookmarkEditMode(false)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (bookmarks.loadState.refresh is LoadState.NotLoading && bookmarks.itemCount == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = ShinjikaiUi.BottomBarClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    header()
                    ShinjikaiEmptyState(
                        title = stringResource(R.string.bookmarks_empty),
                        message = stringResource(R.string.bookmarks_empty_message),
                        iconRes = R.drawable.ic_nav_bookmarks
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = ShinjikaiUi.BottomBarClearance),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "bookmarks-header") { header() }
                    items(
                        count = bookmarks.itemCount,
                        key = { index -> bookmarks[index]?.id ?: "bookmark-$index" },
                        contentType = { "bookmark-entry" }
                    ) { index ->
                        val bookmark = bookmarks[index] ?: return@items
                        val previous = if (index > 0) bookmarks.peek(index - 1) else null
                        val thisDate = localDateOf(bookmark.createdAt)
                        val prevDate = previous?.let { localDateOf(it.createdAt) }
                        val showHeader = prevDate == null || prevDate != thisDate
                        val item = bookmark.item
                        val isSelected = uiState.selectedIds.contains(bookmark.id)

                        Column {
                            if (showHeader) {
                                ShinjikaiSectionCaption(text = thisDate)
                            }
                            DictionaryEntryCard(
                                item = item,
                                onClick = {
                                    if (uiState.isEditMode) {
                                        viewModel.toggleBookmarkSelection(bookmark.id)
                                    } else {
                                        onOpenBookmarkDetails(item)
                                    }
                                },
                                previewMaxLines = if (uiState.useOfflineMode || uiState.activeCategoryId != null) 1 else Int.MAX_VALUE,
                                previewText = if (uiState.useOfflineMode) null else item.meaningSummary,
                                highlighted = uiState.isEditMode && isSelected,
                                footer = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_settings_clock),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.bookmarks_added_at, localTimeLabel(bookmark.createdAt)),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (uiState.isEditMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { viewModel.toggleBookmarkSelection(bookmark.id) }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            if (uiState.isEditMode) {
                BookmarksEditBar(
                    selectedCount = uiState.selectedIds.size,
                    isAllSelected = isAllSelected,
                    onDone = { viewModel.updateBookmarkEditMode(false) },
                    onToggleSelectAll = {
                        if (isAllSelected) {
                            viewModel.pruneBookmarkSelection(emptySet())
                        } else {
                            (allIds - uiState.selectedIds).forEach(viewModel::toggleBookmarkSelection)
                        }
                    },
                    onDelete = { viewModel.requestDeleteBookmarks(uiState.selectedIds) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

        }

        uiState.pendingDeletionIds?.let { ids ->
            val idsList = ids.toList()
            val label = if (idsList.size == 1) {
                val firstId = idsList.first()
                val target = uiState.items.firstOrNull { it.id == firstId }?.item
                (target?.primaryWriting ?: "").ifBlank {
                    (target?.kana ?: "").ifBlank { fallbackBookmarkLabel.format(firstId) }
                }
            } else {
                stringResource(R.string.bookmarks_delete_count, idsList.size)
            }

            ShinjikaiDialog(
                onDismissRequest = { viewModel.pendingBookmarkDeletionIds = null },
                title = stringResource(R.string.bookmarks_delete_confirmation_title),
                message = label,
                iconRes = R.drawable.ic_settings_trash,
                confirmLabel = stringResource(R.string.bookmarks_delete),
                onConfirm = { viewModel.deleteBookmarks(idsList) },
                destructive = true
            )
        }
    }
}

@Composable
private fun BookmarksEditBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onDone: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = ShinjikaiUi.PillShape,
            color = colors.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDone,
                    shape = ShinjikaiUi.PillShape,
                    color = colors.primary,
                    contentColor = colors.onPrimary
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_check),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.bookmarks_done),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.bookmarks_selected_count, selectedCount),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                IconButton(onClick = onToggleSelectAll, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_select_all),
                        contentDescription = stringResource(
                            if (isAllSelected) R.string.bookmarks_clear_selection else R.string.bookmarks_select_all
                        ),
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    shape = CircleShape,
                    color = if (selectedCount > 0) colors.errorContainer else colors.surfaceVariant,
                    contentColor = if (selectedCount > 0) colors.onErrorContainer else colors.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_trash),
                            contentDescription = stringResource(R.string.bookmarks_delete),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
