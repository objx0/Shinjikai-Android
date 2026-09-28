package com.shinjikai.dictionary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.data.RecentSearchEntry
import com.shinjikai.dictionary.ui.SearchUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun HistoryScreenContent(
    uiState: SearchUiState,
    viewModel: ShinjikaiViewModel,
    onOpenHistoryTerm: (String) -> Unit,
) {
    var pendingDeleteTerm by remember { mutableStateOf<String?>(null) }
    var pendingClearAllHistory by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val historyDateFormatter = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.recentSearches.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ShinjikaiPageHeader(
                        title = stringResource(R.string.history_title),
                        subtitle = stringResource(R.string.search_recent_subtitle),
                        icon = Icons.Default.History
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ShinjikaiEmptyState(
                            title = stringResource(R.string.history_empty),
                            icon = Icons.Default.History,
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
                    item(key = "history-header") {
                        ShinjikaiPageHeader(
                            title = stringResource(R.string.history_title),
                            subtitle = stringResource(R.string.search_recent_subtitle),
                            icon = Icons.Default.History,
                            action = {
                                FilledTonalIconButton(
                                    onClick = { pendingClearAllHistory = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HistoryToggleOff,
                                        contentDescription = stringResource(R.string.search_clear_history)
                                    )
                                }
                            }
                        )
                    }

                    items(
                        items = uiState.recentSearches,
                        key = { it.term.lowercase(Locale.ROOT) },
                        contentType = { "history-entry" }
                    ) { historyEntry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShinjikaiUi.CardShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = ShinjikaiUi.cardBorder(alpha = 0.22f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenHistoryTerm(historyEntry.term)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ShinjikaiUi.panelColor(alpha = 0.42f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = historyEntry.term,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    formatHistoryDate(historyEntry, historyDateFormatter)?.let { formattedDate ->
                                        Text(
                                            text = formattedDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { pendingDeleteTerm = historyEntry.term }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.search_remove_history),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    pendingDeleteTerm?.let { historyTerm ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTerm = null },
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_message, historyTerm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeRecentSearch(historyTerm)
                        pendingDeleteTerm = null
                    }
                ) {
                    Text(stringResource(R.string.search_remove_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTerm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (pendingClearAllHistory) {
        AlertDialog(
            onDismissRequest = { pendingClearAllHistory = false },
            title = { Text(stringResource(R.string.history_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_all_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecentSearches()
                        pendingClearAllHistory = false
                    }
                ) {
                    Text(stringResource(R.string.search_clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearAllHistory = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun formatHistoryDate(
    historyEntry: RecentSearchEntry,
    formatter: DateFormat
): String? {
    val searchedAtEpochMs = historyEntry.searchedAtEpochMs ?: return null
    return formatter.format(Date(searchedAtEpochMs))
}
