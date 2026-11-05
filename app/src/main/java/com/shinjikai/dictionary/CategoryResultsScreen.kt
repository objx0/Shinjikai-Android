package com.shinjikai.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.shinjikai.dictionary.data.SearchItem
import kotlinx.coroutines.flow.Flow

private val CategoryTileShape = RoundedCornerShape(22.dp)

/** Words that share a category, opened from a word's التصنيف chip. Back returns to that word. */
@Composable
fun CategoryResultsScreen(
    categoryName: String,
    results: Flow<PagingData<SearchItem>>,
    onOpenDetails: (SearchItem) -> Unit,
    onGoBack: () -> Unit
) {
    val words = results.collectAsLazyPagingItems()
    val refresh = words.loadState.refresh

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "category-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailBackPill(onClick = onGoBack)
                CategoryHeader(categoryName = categoryName)
            }
        }

        if (refresh is LoadState.Loading && words.itemCount == 0) {
            item(key = "category-loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (refresh is LoadState.NotLoading && words.itemCount == 0) {
            item(key = "category-empty", span = { GridItemSpan(maxLineSpan) }) {
                ShinjikaiEmptyState(
                    title = stringResource(R.string.category_empty),
                    iconRes = R.drawable.ic_nav_browse
                )
            }
        }

        (refresh as? LoadState.Error)?.let { error ->
            item(key = "category-error", span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { words.retry() }, modifier = Modifier.fillMaxWidth()) {
                    Text(error.error.message ?: stringResource(R.string.detail_retry))
                }
            }
        }

        items(
            count = words.itemCount,
            key = { index -> words.peek(index)?.id ?: "category-$index" },
            contentType = { "category-word" }
        ) { index ->
            val item = words[index] ?: return@items
            CategoryWordTile(item = item, onClick = { onOpenDetails(item) })
        }

        if (words.loadState.append is LoadState.Loading) {
            item(key = "category-append", span = { GridItemSpan(maxLineSpan) }) {
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
    }
}

@Composable
private fun CategoryHeader(categoryName: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.detail_category_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
            Text(
                text = categoryName,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 34.sp),
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
            Text(
                text = stringResource(R.string.category_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.category_kanji),
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
            fontWeight = FontWeight.Bold,
            color = colors.primary.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun CategoryWordTile(
    item: SearchItem,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val headword = remember(item.primaryWriting, item.kana) {
        item.primaryWriting.ifBlank { item.kana }.trim()
    }
    val reading = remember(item.kana, headword) {
        item.kana.trim().takeIf { it.isNotBlank() && it != headword }
    }
    val preview = remember(item.meaningSummary) { formatOfflineSearchPreview(item.meaningSummary) }
    val watermark = remember(headword) { kanjiWatermarkChar(headword) }
    val level = item.difficulty.takeIf { it in 1..5 }
    val commonnessDescription = level?.let {
        stringResource(R.string.commonness_a11y, stringResource(categoryLevelRes(it)), it, 5)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp),
        shape = CategoryTileShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            watermark?.let { KanjiWatermark(mark = it, fontSize = 84.sp) }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (level != null) {
                    Row(
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = commonnessDescription.orEmpty()
                        },
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(5) { index ->
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        if (index < level) colors.primary else colors.outlineVariant,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
                Text(
                    text = reading.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = headword,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (preview.isNotBlank()) {
                    Text(
                        text = forceRtlText(preview),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun categoryLevelRes(level: Int): Int = when (level) {
    5 -> R.string.commonness_level_5
    4 -> R.string.commonness_level_4
    3 -> R.string.commonness_level_3
    2 -> R.string.commonness_level_2
    else -> R.string.commonness_level_1
}
