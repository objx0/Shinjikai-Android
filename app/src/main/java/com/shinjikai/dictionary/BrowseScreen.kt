package com.shinjikai.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import kotlinx.coroutines.flow.Flow

/** Gojūon row starts; the browse list is sorted by reading, so each is a valid jump key. */
private val KanaRows = listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ")

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun BrowseScreenContent(
    viewModel: ShinjikaiViewModel,
    browseFlow: Flow<PagingData<SearchItem>>,
    totalEntries: Int,
    onOpenDetails: (SearchItem) -> Unit
) {
    val entries = browseFlow.collectAsLazyPagingItems()
    val refreshState = entries.loadState.refresh
    val startReading by viewModel.browseStartReading.collectAsState()
    val selectedRow = startReading ?: KanaRows.first()
    val listState = rememberLazyListState()
    var jumpNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(jumpNonce) {
        if (jumpNonce > 0) {
            // Header, random tile and kana card come first; land on the row caption.
            listState.animateScrollToItem(3)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = ShinjikaiUi.BottomBarClearance
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "browse-header") {
                ShinjikaiTabHeader(
                    title = stringResource(R.string.browse_title),
                    subtitle = stringResource(R.string.browse_count, totalEntries),
                    kanji = stringResource(R.string.browse_kanji)
                )
            }

            item(key = "browse-random") {
                RandomEntryTile(onClick = { viewModel.openRandomDictionaryEntry(onOpenDetails) })
            }

            item(key = "browse-kana") {
                KanaJumpCard(
                    selected = selectedRow,
                    onSelect = { row ->
                        viewModel.setBrowseStartReading(if (row == KanaRows.first()) null else row)
                        jumpNonce++
                    }
                )
            }

            item(key = "browse-caption") {
                ShinjikaiSectionCaption(
                    text = stringResource(R.string.browse_row_caption, selectedRow),
                    trailing = stringResource(R.string.browse_sorted_by_reading)
                )
            }

            if (refreshState is LoadState.Loading) {
                item(key = "browse-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (refreshState is LoadState.NotLoading && entries.itemCount == 0) {
                item(key = "browse-empty") {
                    ShinjikaiEmptyState(
                        title = stringResource(R.string.browse_empty),
                        iconRes = R.drawable.ic_nav_browse,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            (refreshState as? LoadState.Error)?.let { error ->
                item(key = "browse-refresh-error") {
                    TextButton(
                        onClick = { entries.retry() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(error.error.message ?: stringResource(R.string.detail_retry))
                    }
                }
            }

            items(
                count = entries.itemCount,
                key = { index -> entries[index]?.id ?: "browse-$index" },
                contentType = { "dictionary-entry" }
            ) { index ->
                val item = entries[index] ?: return@items
                DictionaryEntryCard(
                    item = item,
                    onClick = { onOpenDetails(item) },
                    modifier = Modifier,
                    previewMaxLines = 2
                )
            }

            if (entries.loadState.append is LoadState.Loading) {
                item(key = "browse-append-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            (entries.loadState.append as? LoadState.Error)?.let { appendError ->
                item(key = "browse-append-error") {
                    TextButton(
                        onClick = { entries.retry() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(appendError.error.message ?: stringResource(R.string.detail_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomEntryTile(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ShinjikaiUi.SectionShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 80.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_browse_shuffle),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.browse_random),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.browse_random_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_settings_chevron),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun KanaJumpCard(
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShinjikaiUi.SectionShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.browse_jump_title),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant
            )
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KanaRows.chunked(5).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowKeys.forEach { kana ->
                            val isSelected = kana == selected
                            val label = stringResource(R.string.browse_jump_row_a11y, kana)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(ShinjikaiUi.PillShape)
                                    .background(if (isSelected) colors.primary else colors.background)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                        } else {
                                            Modifier.border(1.dp, colors.outlineVariant, ShinjikaiUi.PillShape)
                                        }
                                    )
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(kana) }
                                    )
                                    .semantics { contentDescription = label },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = kana,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 17.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.onPrimary else colors.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
