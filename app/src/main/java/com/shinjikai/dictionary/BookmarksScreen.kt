package com.shinjikai.dictionary

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.text.style.TextAlign
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

    val headerActions: @Composable () -> Unit = {
        if (uiState.isEditMode) {
            val isAllSelected = allIds.isNotEmpty() && uiState.selectedIds.size == allIds.size
            IconButton(
                enabled = allIds.isNotEmpty(),
                onClick = {
                    if (isAllSelected) {
                        viewModel.pruneBookmarkSelection(emptySet())
                    } else {
                        val missingIds = allIds - uiState.selectedIds
                        missingIds.forEach(viewModel::toggleBookmarkSelection)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = stringResource(
                        if (isAllSelected) {
                            R.string.bookmarks_clear_selection
                        } else {
                            R.string.bookmarks_select_all
                        }
                    )
                )
            }
            IconButton(
                enabled = uiState.selectedIds.isNotEmpty(),
                onClick = {
                    viewModel.requestDeleteBookmarks(uiState.selectedIds)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.bookmarks_delete)
                )
            }
            IconButton(onClick = { viewModel.updateBookmarkEditMode(false) }) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = stringResource(R.string.bookmarks_done)
                )
            }
        } else {
            FilledTonalIconButton(
                enabled = allIds.isNotEmpty(),
                onClick = onExportBookmarks
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.bookmarks_export_csv)
                )
            }
            FilledTonalIconButton(
                enabled = allIds.isNotEmpty(),
                onClick = { viewModel.updateBookmarkEditMode(true) }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.bookmarks_manage)
                )
            }
        }
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ShinjikaiPageHeader(
                        title = stringResource(R.string.bookmarks_title),
                        subtitle = stringResource(R.string.bookmarks_empty),
                        icon = if (uiState.isEditMode) null else Icons.Default.Bookmark,
                        action = headerActions
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ShinjikaiEmptyState(
                            title = stringResource(R.string.bookmarks_empty),
                            icon = Icons.Default.Bookmark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = ShinjikaiUi.BottomBarClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                item(key = "bookmarks-header") {
                    ShinjikaiPageHeader(
                        title = if (uiState.isEditMode) {
                            stringResource(R.string.bookmarks_selected_count, uiState.selectedIds.size)
                        } else {
                            stringResource(R.string.bookmarks_title)
                        },
                        subtitle = if (uiState.isEditMode) null else {
                            stringResource(R.string.bookmarks_manage)
                        },
                        icon = if (uiState.isEditMode) null else Icons.Default.Bookmark,
                        action = headerActions
                    )
                }
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

                    Column(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showHeader) {
                            Text(
                                text = thisDate,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                            )
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
                            footer = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.bookmarks_added_at, localTimeLabel(bookmark.createdAt)),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Right
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

            AlertDialog(
                onDismissRequest = { viewModel.pendingBookmarkDeletionIds = null },
                title = { Text(stringResource(R.string.bookmarks_delete_confirmation_title)) },
                text = { Text(label) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteBookmarks(idsList) }) {
                        Text(stringResource(R.string.bookmarks_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.pendingBookmarkDeletionIds = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
