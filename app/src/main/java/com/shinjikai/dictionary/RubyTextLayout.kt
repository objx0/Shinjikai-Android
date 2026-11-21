package com.shinjikai.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal data class RubyTextSegment(
    val base: String,
    val ruby: String?,
    val wordId: Int? = null,
    val wrapGroupId: Int? = null,
    val highlighted: Boolean = false
)

@Composable
internal fun RubyTextLayout(
    segments: List<RubyTextSegment>,
    baseStyle: TextStyle,
    rubyStyle: TextStyle,
    baseColor: (RubyTextSegment) -> Color,
    rubyColor: (RubyTextSegment) -> Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    // A small overlap counteracts font metrics so kana sits visibly close
    // to the kanji without changing the surrounding layout.
    rubyBaseGap: Dp = (-4).dp,
    // Background drawn behind highlighted segments (e.g. the headword in an example).
    highlightColor: Color = Color.Unspecified,
    // Lets furigana overhang neighbouring kana by up to one ruby character,
    // so a wide reading doesn't push the base text apart.
    allowRubyOverhang: Boolean = true,
    onWordClick: ((Int) -> Unit)? = null
) {
    val density = LocalDensity.current
    val highlightPadX = with(density) { 1.dp.toPx() }
    val highlightPadY = with(density) { 1.dp.toPx() }
    val highlightRadius = with(density) { 3.dp.toPx() }
    val highlightRects = remember { mutableStateOf<List<Rect>>(emptyList()) }
    val rowGapPx = with(LocalDensity.current) { 5.dp.roundToPx() }
    val rubyBaseGapPx = with(LocalDensity.current) { rubyBaseGap.roundToPx() }
    val compactBaseStyle = baseStyle.withoutFontPadding()
    val compactRubyStyle = rubyStyle.withoutFontPadding()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Layout(
            modifier = modifier.drawBehind {
                if (highlightColor.isSpecified) {
                    highlightRects.value.forEach { rect ->
                        // Highlighter stroke across the lower half of the glyphs.
                        val markerTop = rect.top + rect.height * 0.52f
                        val markerBottom = rect.bottom - rect.height * 0.04f
                        drawRoundRect(
                            color = highlightColor,
                            topLeft = Offset(rect.left - highlightPadX, markerTop),
                            size = Size(rect.width + highlightPadX * 2, markerBottom - markerTop),
                            cornerRadius = CornerRadius(highlightRadius)
                        )
                    }
                }
            },
            content = {
                segments.forEach { segment ->
                    val wordId = segment.wordId
                    val segmentModifier = if (wordId != null && onWordClick != null) {
                        Modifier.clickable { onWordClick(wordId) }
                    } else {
                        Modifier
                    }
                    Text(
                        text = segment.ruby.orEmpty(),
                        modifier = segmentModifier,
                        style = compactRubyStyle,
                        color = rubyColor(segment),
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                    Text(
                        text = segment.base,
                        modifier = segmentModifier,
                        style = compactBaseStyle,
                        color = baseColor(segment),
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val rubies = segments.indices.map { measurables[it * 2].measure(looseConstraints) }
            val bases = segments.indices.map { measurables[it * 2 + 1].measure(looseConstraints) }
            fun hasRubyAt(i: Int) = !segments[i].ruby.isNullOrBlank()
            fun overhangRoom(neighbor: Int, rubyCharWidth: Int): Int {
                if (!allowRubyOverhang || neighbor !in segments.indices || hasRubyAt(neighbor)) return 0
                return min(rubyCharWidth, bases[neighbor].width / 2)
            }
            val measured = segments.mapIndexed { index, segment ->
                val ruby = rubies[index]
                val base = bases[index]
                val hasRuby = hasRubyAt(index)
                var padStart = 0
                var padEnd = 0
                if (hasRuby && ruby.width > base.width) {
                    val excess = ruby.width - base.width
                    val rubyCharWidth = ruby.width / segment.ruby!!.length.coerceAtLeast(1)
                    val needStart = excess / 2
                    val needEnd = excess - needStart
                    padStart = needStart - min(needStart, overhangRoom(index - 1, rubyCharWidth))
                    padEnd = needEnd - min(needEnd, overhangRoom(index + 1, rubyCharWidth))
                }
                MeasuredRubyTextSegment(
                    ruby = ruby,
                    base = base,
                    hasRuby = hasRuby,
                    width = base.width + padStart + padEnd,
                    padStart = padStart,
                    wrapGroupId = segment.wrapGroupId,
                    highlighted = segment.highlighted
                )
            }

            val availableWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                measured.sumOf { it.width }.coerceAtLeast(constraints.minWidth)
            }.coerceAtLeast(1)

            val runs = mutableListOf<MeasuredRubyTextRun>()
            var index = 0
            while (index < measured.size) {
                val groupId = measured[index].wrapGroupId
                if (groupId == null) {
                    runs += MeasuredRubyTextRun(listOf(measured[index]))
                    index += 1
                } else {
                    val start = index
                    while (index < measured.size && measured[index].wrapGroupId == groupId) {
                        index += 1
                    }
                    runs += MeasuredRubyTextRun(measured.subList(start, index))
                }
            }

            val rows = mutableListOf<List<MeasuredRubyTextRun>>()
            var currentRow = mutableListOf<MeasuredRubyTextRun>()
            var currentWidth = 0
            runs.forEach { run ->
                if (currentRow.isNotEmpty() && currentWidth + run.width > availableWidth) {
                    rows += currentRow
                    currentRow = mutableListOf()
                    currentWidth = 0
                }
                currentRow += run
                currentWidth += run.width
            }
            if (currentRow.isNotEmpty()) rows += currentRow

            data class PlacedRubyItem(
                val item: MeasuredRubyTextSegment,
                val x: Int,
                val rowY: Int,
                val rubyLaneHeight: Int,
                val rubyBlockHeight: Int
            )

            val placed = mutableListOf<PlacedRubyItem>()
            var measuredHeight = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowWidth = row.sumOf { it.width }
                var x = when (textAlign) {
                    TextAlign.Center -> (availableWidth - rowWidth) / 2
                    TextAlign.Right, TextAlign.End -> availableWidth - rowWidth
                    else -> 0
                }.coerceAtLeast(0)
                val rowItems = row.flatMap { it.items }
                val rubyLaneHeight = rowItems.maxOfOrNull { if (it.hasRuby) it.ruby.height else 0 } ?: 0
                val baseLaneHeight = rowItems.maxOfOrNull { it.base.height } ?: 0
                // Keep a real, shared gap between the kana lane and the kanji lane.
                val rubyBlockHeight = rubyLaneHeight + rubyBaseGapPx
                row.forEach { run ->
                    run.items.forEach { item ->
                        placed += PlacedRubyItem(
                            item = item,
                            x = x,
                            rowY = measuredHeight,
                            rubyLaneHeight = rubyLaneHeight,
                            rubyBlockHeight = rubyBlockHeight
                        )
                        x += item.width
                    }
                }
                measuredHeight += rubyBlockHeight + baseLaneHeight
                if (rowIndex != rows.lastIndex) measuredHeight += rowGapPx
            }

            val layoutWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                rows.maxOfOrNull { row -> row.sumOf { it.width } } ?: 0
            }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val layoutHeight = measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

            val rects = mutableListOf<Rect>()
            var previous: PlacedRubyItem? = null
            placed.forEach { placedItem ->
                val item = placedItem.item
                if (item.highlighted) {
                    val left = (placedItem.x + item.padStart).toFloat()
                    val top = (placedItem.rowY + placedItem.rubyBlockHeight).toFloat()
                    val rect = Rect(left, top, left + item.base.width, top + item.base.height)
                    val prev = previous
                    if (prev != null && prev.item.highlighted && prev.rowY == placedItem.rowY && rects.isNotEmpty()) {
                        val last = rects.removeAt(rects.lastIndex)
                        rects += Rect(last.left, min(last.top, rect.top), rect.right, max(last.bottom, rect.bottom))
                    } else {
                        rects += rect
                    }
                }
                previous = placedItem
            }
            if (highlightRects.value != rects) highlightRects.value = rects

            layout(layoutWidth, layoutHeight) {
                placed.forEach { placedItem ->
                    val item = placedItem.item
                    val baseX = placedItem.x + item.padStart
                    val baseY = placedItem.rowY + placedItem.rubyBlockHeight
                    if (item.hasRuby) {
                        val rubyX = (baseX + (item.base.width - item.ruby.width) / 2)
                            .coerceIn(0, (layoutWidth - item.ruby.width).coerceAtLeast(0))
                        val rubyY = placedItem.rowY +
                            (placedItem.rubyLaneHeight - item.ruby.height).coerceAtLeast(0)
                        item.ruby.placeRelative(rubyX, rubyY)
                    }
                    item.base.placeRelative(baseX, baseY)
                }
            }
        }
    }
}

private data class MeasuredRubyTextSegment(
    val ruby: Placeable,
    val base: Placeable,
    val hasRuby: Boolean,
    val width: Int,
    val padStart: Int,
    val wrapGroupId: Int?,
    val highlighted: Boolean
)

private data class MeasuredRubyTextRun(
    val items: List<MeasuredRubyTextSegment>
) {
    val width: Int = items.sumOf { it.width }
}

@Suppress("DEPRECATION")
internal fun TextStyle.withoutFontPadding(): TextStyle {
    return copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
}
