package com.shinjikai.dictionary

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val COMMONNESS_MAX = 5

@StringRes
private fun commonnessLevelRes(level: Int): Int = when (level) {
    5 -> R.string.commonness_level_5
    4 -> R.string.commonness_level_4
    3 -> R.string.commonness_level_3
    2 -> R.string.commonness_level_2
    else -> R.string.commonness_level_1
}

/**
 * Compact commonness indicator for list items: level word on top, 5-dot meter below.
 * Dots always fill from the right (Arabic reading direction), even inside LTR rows.
 */
@Composable
internal fun CommonnessIndicator(
    difficulty: Int,
    modifier: Modifier = Modifier
) {
    if (difficulty !in 1..COMMONNESS_MAX) return
    val colors = MaterialTheme.colorScheme
    val levelLabel = stringResource(commonnessLevelRes(difficulty))
    val description = stringResource(R.string.commonness_a11y, levelLabel, difficulty, COMMONNESS_MAX)

    Column(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = description }
            .background(
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = levelLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSecondaryContainer,
            maxLines = 1
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(COMMONNESS_MAX) { index ->
                    val filled = index < difficulty
                    Row(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (filled) colors.primary else colors.outlineVariant,
                                shape = CircleShape
                            )
                    ) {}
                }
            }
        }
    }
}
