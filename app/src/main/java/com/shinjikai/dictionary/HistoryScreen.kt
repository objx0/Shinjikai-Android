package com.shinjikai.dictionary

import java.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
    val timeFormatter = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateFormatter = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)
    val olderLabel = stringResource(R.string.history_older)
    val groups = remember(uiState.recentSearches, todayLabel, yesterdayLabel, olderLabel, dateFormatter) {
        uiState.recentSearches.groupBy { entry ->
            historyDayLabel(entry.searchedAtEpochMs, todayLabel, yesterdayLabel, olderLabel, dateFormatter)
        }
    }
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShinjikaiTabHeader(
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_count, uiState.recentSearches.size),
                kanji = stringResource(R.string.history_kanji)
            )
            if (uiState.recentSearches.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShinjikaiActionChip(
                        text = stringResource(R.string.history_clear_all_chip),
                        iconRes = R.drawable.ic_settings_trash,
                        onClick = { pendingClearAllHistory = true },
                        destructive = true
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (uiState.recentSearches.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = ShinjikaiUi.BottomBarClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                header()
                ShinjikaiEmptyState(
                    title = stringResource(R.string.history_empty),
                    message = stringResource(R.string.history_empty_message),
                    iconRes = R.drawable.ic_nav_history
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = ShinjikaiUi.BottomBarClearance)
            ) {
                item(key = "history-header") { header() }
                groups.forEach { (label, entries) ->
                    item(key = "history-label-$label") {
                        ShinjikaiSectionCaption(text = label)
                    }
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.term.lowercase(Locale.ROOT) },
                        contentType = { _, _ -> "history-entry" }
                    ) { index, historyEntry ->
                        HistoryRow(
                            term = historyEntry.term,
                            time = historyEntry.searchedAtEpochMs?.let { timeFormatter.format(Date(it)) },
                            isFirst = index == 0,
                            isLast = index == entries.lastIndex,
                            onOpen = { onOpenHistoryTerm(historyEntry.term) },
                            onRemove = { pendingDeleteTerm = historyEntry.term }
                        )
                    }
                }
            }
        }
    }

    pendingDeleteTerm?.let { historyTerm ->
        ShinjikaiDialog(
            onDismissRequest = { pendingDeleteTerm = null },
            title = stringResource(R.string.history_delete_confirm_title),
            message = stringResource(R.string.history_delete_confirm_message, historyTerm),
            iconRes = R.drawable.ic_settings_trash,
            confirmLabel = stringResource(R.string.search_remove_history),
            onConfirm = {
                viewModel.removeRecentSearch(historyTerm)
                pendingDeleteTerm = null
            },
            destructive = true
        )
    }

    if (pendingClearAllHistory) {
        ShinjikaiDialog(
            onDismissRequest = { pendingClearAllHistory = false },
            title = stringResource(R.string.history_clear_all_confirm_title),
            message = stringResource(R.string.history_clear_all_confirm_message),
            iconRes = R.drawable.ic_settings_trash,
            confirmLabel = stringResource(R.string.search_clear_history),
            onConfirm = {
                viewModel.clearRecentSearches()
                pendingClearAllHistory = false
            },
            destructive = true
        )
    }
}

private fun historyDayLabel(
    epochMs: Long?,
    today: String,
    yesterday: String,
    older: String,
    dateFormatter: DateFormat
): String {
    if (epochMs == null) return older
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMs }
    fun Calendar.dayKey() = get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when (then.dayKey()) {
        now.dayKey() -> today
        yesterdayCal.dayKey() -> yesterday
        else -> dateFormatter.format(Date(epochMs))
    }
}

private val HistoryGroupRadius = 28.dp

@Composable
private fun HistoryRow(
    term: String,
    time: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(
        topStart = if (isFirst) HistoryGroupRadius else 0.dp,
        topEnd = if (isFirst) HistoryGroupRadius else 0.dp,
        bottomStart = if (isLast) HistoryGroupRadius else 0.dp,
        bottomEnd = if (isLast) HistoryGroupRadius else 0.dp
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = 64.dp)
                .padding(
                    start = 14.dp,
                    end = 6.dp,
                    top = if (isFirst) 12.dp else 8.dp,
                    bottom = if (isLast) 12.dp else 8.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_history),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = term,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (time != null) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_close),
                    contentDescription = stringResource(R.string.search_remove_history),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                color = colors.outlineVariant
            )
        }
    }
}
