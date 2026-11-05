package com.shinjikai.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinjikai.dictionary.data.SearchItem

private val EntryCardShape = RoundedCornerShape(28.dp)

@Composable
fun DictionaryEntryCard(
    item: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewMaxLines: Int = 2,
    previewText: String? = null,
    highlighted: Boolean = false,
    footer: (@Composable () -> Unit)? = null
) {
    val headword = remember(item.primaryWriting, item.kana) {
        item.primaryWriting.ifBlank { item.kana }.trim()
    }
    val preview = remember(item.meaningSummary, previewText) {
        previewText ?: formatOfflineSearchPreview(item.meaningSummary)
    }
    if (headword.isBlank()) return

    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = EntryCardShape,
        color = if (highlighted) colors.surfaceVariant.copy(alpha = 0.45f) else colors.surface,
        border = BorderStroke(
            1.dp,
            if (highlighted) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommonnessIndicator(difficulty = item.difficulty)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        HeadwordFuriganaText(
                            text = headword,
                            reading = item.kana,
                            parts = item.writings.firstOrNull()?.parts,
                            modifier = Modifier.fillMaxWidth(),
                            baseStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (highlighted) 40.sp else 30.sp,
                                lineHeight = if (highlighted) 52.sp else 39.sp
                            ),
                            rubyStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            if (preview.isNotBlank()) {
                HorizontalDivider(color = colors.outlineVariant)
                Text(
                    text = forceRtlText(preview),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Rtl,
                        fontSize = 15.sp,
                        lineHeight = 26.sp
                    ),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    maxLines = if (highlighted) maxOf(previewMaxLines, 4) else previewMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }

            footer?.invoke()
        }
    }
}
