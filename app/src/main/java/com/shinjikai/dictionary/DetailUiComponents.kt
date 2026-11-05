package com.shinjikai.dictionary

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.JsonElement
import com.shinjikai.dictionary.data.Meaning
import com.shinjikai.dictionary.data.KanjiInfo
import com.shinjikai.dictionary.data.RelatedGroup
import com.shinjikai.dictionary.data.RelatedWordItem
import com.shinjikai.dictionary.data.SentenceExample
import com.shinjikai.dictionary.data.WordDetailsResponse
import com.shinjikai.dictionary.data.additionalExamples
import com.shinjikai.dictionary.data.displayCharacter
import com.shinjikai.dictionary.data.directMeaningExamples
import com.shinjikai.dictionary.data.examplesForMeaning
import com.shinjikai.dictionary.data.extractPictureDescription
import com.shinjikai.dictionary.data.extractPictureReference
import com.shinjikai.dictionary.data.stableContentKey
import com.shinjikai.dictionary.ui.DetailUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.text.DateFormat
import java.util.Date

private data class CategoryChipModel(val id: Int, val label: String)
private data class MeaningEntry(val definition: String, val note: String)
private data class DetailMeaningEntry(
    val definition: String,
    val note: String,
    val definitionReferences: List<GlossaryReference>,
    val noteReferences: List<GlossaryReference>,
    val japanese: String,
    val source: String,
    val references: List<GlossaryReference>,
    val relatedGroups: List<RelatedGroup>,
    val pictures: List<DetailPicture>,
    val examples: List<SentenceExample>
)
private data class DetailPicture(val url: String, val description: String)
internal data class GlossaryReference(val id: Int, val label: String, val start: Int = -1, val end: Int = -1)
internal data class DefinitionContent(val text: String, val references: List<GlossaryReference>)
private data class DefinitionTextSegment(val text: String, val referenceId: Int?)

private const val GLOSSARY_REFERENCE_ANNOTATION_TAG = "glossary_reference"
private const val NO_BREAK_JOINER = "\u2060"
private const val EXAMPLES_PAGE_SIZE = 3
private val MEANING_BULLET_PREFIX_REGEX =
    Regex("(?m)^\\s*[\\uD83D\\uDD39\\u25AA\\u2022\\u25CF\\u25E6]\\s*")
private val MEANING_MULTISPACE_REGEX = Regex("""[ \t]{2,}""")
private val SEARCH_PREVIEW_MULTISPACE_REGEX = Regex("""\s{2,}""")
private val MEANING_EMPTY_BRACES_REGEX = Regex("""\{\s*\}""")
private val MEANING_TRAILING_SPACES_REGEX = Regex("""(?m)^\s+$""")
private val MEANING_CONTROL_MARKS_REGEX = Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]""")
private val MEANING_ARABIC_SEMICOLON_REGEX = Regex("""\s*؛\s*""")
private val MEANING_PAREN_BAR_REGEX = Regex("""\(\|\s*(.*?)\s*\|\)""")
private val MEANING_INLINE_TAG_REGEX =
    Regex("""\[\{\s*([^:{}\[\]]+)\s*:\s*([^{}\[\]]+)\s*\}\]|\{\s*([^:{}\[\]]+)\s*:\s*([^{}\[\]]+)\s*\}""")
private val MEANING_BRACED_LABEL_REGEX = Regex("""\{\s*([^:{}][^:{}]*?)\s*\}""")
private val GLOSSARY_REFERENCE_REGEX = Regex("""\{([^:{}]+):(\d+)\}""")
private val WINDOWS_ABSOLUTE_IMAGE_PATH_REGEX = Regex("""^[A-Za-z]:/.*""")
private val LOCAL_ABSOLUTE_IMAGE_PATH_REGEX = Regex("""^/(data|storage|sdcard|mnt)/.*""")

@Composable
fun DetailScreenBody(
    modifier: Modifier = Modifier,
    useOfflineMode: Boolean,
    detailState: DetailUiState,
    onSpeakJapaneseText: (text: String, utteranceId: String, unavailableMessage: String) -> Unit,
    context: Context,
    clipboardManager: ClipboardManager,
    focusManager: androidx.compose.ui.focus.FocusManager,
    viewModel: ShinjikaiViewModel,
    onOpenCategorySearch: (Int, String) -> Unit,
    onOpenGlossaryReference: (Int) -> Unit,
    onOpenRelatedWord: (RelatedWordItem) -> Unit,
    actions: DetailActions
) {
    val item = detailState.selectedItem
    var zoomedPictureUrl by remember { mutableStateOf<String?>(null) }
    val noReadingMessage = stringResource(R.string.detail_no_reading)
    val pronounceLabel = stringResource(R.string.detail_pronounce)
    val japaneseAudioUnavailableMessage = stringResource(R.string.detail_japanese_audio_unavailable)
    val wordCopiedMessage = stringResource(R.string.detail_word_copied)
    if (detailState.loading) {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { DetailBackPill(onClick = actions.onGoBack) }
            item {
                DetailLoadingSkeleton(
                    message = detailState.offlineImportPhase
                        ?.takeIf { detailState.isImportingOfflineData && it.isNotBlank() }
                        ?: stringResource(R.string.settings_loading_inline)
                )
            }
        }
        return
    }

    if (item == null) {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { DetailBackPill(onClick = actions.onGoBack) }
            detailState.error?.let { error ->
                item {
                    DetailStateCard(
                        title = stringResource(R.string.error_details_load),
                        message = error,
                        actionLabel = stringResource(R.string.detail_retry),
                        onAction = viewModel::retryDetailsLoad
                    )
                }
            }
            item { Text(stringResource(R.string.detail_not_selected)) }
        }
        return
    }

    val error = detailState.error

        val details = detailState.details
        val primaryWriting = remember(details) {
            details?.word?.writings?.firstOrNull { it.text.isNotBlank() }
        }
        val detailKana = details?.word?.kana.orEmpty()
        val kanji = primaryWriting?.text
            .orEmpty()
            .ifBlank {
                detailKana.ifBlank {
                    item.primaryWriting.ifBlank { item.kana.ifBlank { "-" } }
                }
            }
        val kana = detailKana.ifBlank { item.kana.ifBlank { "-" } }
        val alternateWritings = remember(details, kanji) {
            details?.word?.writings.orEmpty()
                .map { it.text.trim() }
                .filter { it.isNotBlank() && it != kanji }
                .distinct()
        }
        val notePrefix = stringResource(R.string.detail_note_prefix)
        val meaningEntries = remember(details) {
            formatDetailMeaningEntries(details)
        }
        val definitionContent = remember(details?.word?.meanings, notePrefix, useOfflineMode, item.meaningSummary) {
            formatDefinition(
                meanings = details?.word?.meanings,
                notePrefix = notePrefix,
                enableGlossaryLinks = true
            ).takeIf { it.text.isNotBlank() }
                ?: run {
                    if (useOfflineMode) {
                        DefinitionContent(
                            text = item.meaningSummary.ifBlank { "-" },
                            references = emptyList()
                        )
                    } else {
                        val fallbackReferences = mutableListOf<GlossaryReference>()
                        val fallbackText = stripGlossaryReferences(
                            raw = normalizeMeaningText(item.meaningSummary),
                            enableGlossaryLinks = true,
                            references = fallbackReferences
                        ).replace("\n", " ").replace(SEARCH_PREVIEW_MULTISPACE_REGEX, " ").trim()

                        DefinitionContent(
                            text = fallbackText.ifBlank { "-" },
                            references = fallbackReferences
                        )
                    }
                }
        }
        val jlptLevel = details?.word?.jlpt?.takeIf { it in 1..5 } ?: item.jlpt.takeIf { it in 1..5 }
        val commonnessLevel = details?.word?.difficulty?.takeIf { it in 1..5 }
            ?: item.difficulty.takeIf { it in 1..5 }
        val categoryChips = details?.word?.categoryIds.orEmpty()
            .map { id ->
                CategoryChipModel(
                    id = id,
                    label = detailState.categoryNameById[id]
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_category_fallback, id)
                )
            }
            .distinctBy { it.id }
        val wordPictures = remember(details?.word?.pictures) {
            extractDetailPictures(details?.word?.pictures.orEmpty())
        }
        val relatedItems = remember(details?.similarWords) {
            details?.similarWords.orEmpty().map {
                RelatedWordItem(wordId = it.id, text = it.primaryWriting, kana = it.kana)
            }
                .filter { it.text.isNotBlank() || it.kana.isNotBlank() }
                .distinctBy { "${it.wordId}|${it.meaningNo}|${it.text.trim()}|${it.kana.trim()}" }
        }
        val homophones = remember(details?.homophones) {
            details?.homophones.orEmpty()
                .map { RelatedWordItem(wordId = it.id, text = it.primaryWriting, kana = it.kana) }
                .filter { it.text.isNotBlank() || it.kana.isNotBlank() }
                .distinctBy { it.wordId }
        }
        val kanjiInfo = remember(details?.kanjis) {
            details?.kanjis.orEmpty()
                .filter { it.displayCharacter().isNotBlank() }
        }
        val directMeaningExampleList = remember(meaningEntries) {
            meaningEntries.flatMap { it.examples }
        }
        val examples = remember(details, directMeaningExampleList) {
            details?.additionalExamples(directMeaningExampleList).orEmpty()
        }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        error?.let { message ->
            item {
                DetailStateCard(
                    title = stringResource(R.string.error_details_load),
                    message = message,
                    actionLabel = stringResource(R.string.detail_retry),
                    onAction = viewModel::retryDetailsLoad
                )
            }
        }

        item(key = "detail-header") {
            val headerMark = remember(kanji, kana) { detailHeaderMark(kanji, kana) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailBackPill(onClick = actions.onGoBack)
                ShinjikaiTabHeader(
                    title = stringResource(R.string.detail_title),
                    subtitle = if (kanji != kana && kana != "-") "$kanji · $kana" else kanji,
                    kanji = headerMark ?: stringResource(R.string.browse_kanji)
                )
            }
        }

        item {
            DetailWordHeaderCard(
            kanji = kanji,
            kana = kana,
            writingParts = primaryWriting?.parts,
            alternateWritings = alternateWritings,
            pronounceLabel = pronounceLabel,
            actions = actions,
            onSpeakKana = {
                val speakText = kana.trim().takeIf { it.isNotEmpty() && it != "-" }
                when {
                    speakText == null -> Toast.makeText(context, noReadingMessage, Toast.LENGTH_SHORT).show()
                    else -> onSpeakJapaneseText(
                        speakText,
                        "word-kana-${item.id}",
                        japaneseAudioUnavailableMessage
                    )
                }
            },
            onKanjiClick = {
                kanji.trim().takeIf { it.isNotEmpty() && it != "-" }?.let {
                    clipboardManager.setText(AnnotatedString(it))
                    Toast.makeText(context, wordCopiedMessage, Toast.LENGTH_SHORT).show()
                }
            }
            )
        }

        if (commonnessLevel != null || jlptLevel != null) {
            item {
                DetailMetaTiles(
                    commonnessLevel = commonnessLevel,
                    jlptLevel = jlptLevel
                )
            }
        }

        if (categoryChips.isNotEmpty()) {
            item {
                DetailCategoryCard(
                    chips = categoryChips,
                    onCategoryClick = { chip ->
                        focusManager.clearFocus()
                        onOpenCategorySearch(chip.id, chip.label)
                    }
                )
            }
        }

        if (meaningEntries.isNotEmpty()) {
            item {
                MeaningEntriesCard(
                title = stringResource(R.string.detail_definitions_title),
                entries = meaningEntries,
                notePrefix = notePrefix,
                onImageClick = { zoomedPictureUrl = it },
                onGlossaryReferenceClick = { referenceId ->
                    focusManager.clearFocus()
                    onOpenGlossaryReference(referenceId)
                },
                onExampleWordClick = { wordId ->
                    focusManager.clearFocus()
                    onOpenGlossaryReference(wordId)
                },
                onRelatedWordClick = { relatedWord ->
                    focusManager.clearFocus()
                    onOpenRelatedWord(relatedWord)
                }
                )
            }
        } else {
            item {
                DefinitionsCard(
                title = stringResource(R.string.detail_definitions_title),
                content = definitionContent,
                onGlossaryReferenceClick = { referenceId ->
                    focusManager.clearFocus()
                    onOpenGlossaryReference(referenceId)
                }
                )
            }
        }

        if (wordPictures.isNotEmpty()) {
            item {
                PicturesSection(
                title = stringResource(R.string.detail_pictures_title),
                pictures = wordPictures,
                onImageClick = { zoomedPictureUrl = it }
                )
            }
        }

        if (relatedItems.isNotEmpty()) {
            item {
                RelatedWordsCard(
                title = stringResource(R.string.detail_similar_words_title),
                items = relatedItems,
                onWordClick = {
                    focusManager.clearFocus()
                    onOpenRelatedWord(it)
                }
                )
            }
        }

        if (homophones.isNotEmpty()) {
            item {
                RelatedWordsCard(
                title = stringResource(R.string.detail_homophones_title),
                items = homophones,
                onWordClick = {
                    focusManager.clearFocus()
                    onOpenRelatedWord(it)
                }
                )
            }
        }

        if (kanjiInfo.isNotEmpty()) {
            item {
                KanjiInformationSection(
                title = stringResource(R.string.detail_kanji_title),
                items = kanjiInfo
                )
            }
        }

        if (examples.isNotEmpty()) {
            item {
                ExamplesCard(
                title = stringResource(R.string.detail_additional_examples_title),
                items = examples,
                onWordClick = { wordId ->
                    focusManager.clearFocus()
                    onOpenGlossaryReference(wordId)
                }
                )
            }
        }

    }

    // Keep overlays outside LazyColumn: lazy items that are off-screen are not composed,
    // which previously made image taps appear to do nothing unless the list bottom was visible.
    zoomedPictureUrl?.let { imageUrl ->
        ZoomableImageDialog(
            imageUrl = imageUrl,
            onDismiss = { zoomedPictureUrl = null }
        )
    }
}

@Composable
private fun DetailLoadingSkeleton(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShinjikaiUi.CardShape,
        colors = ShinjikaiUi.cardColors(),
        border = ShinjikaiUi.cardBorder(alpha = 0.24f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DetailStateCard(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShinjikaiUi.CardShape,
        colors = ShinjikaiUi.cardColors(),
        border = ShinjikaiUi.cardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

internal fun normalizeMeaningText(raw: String): String = raw.trim()
    .replace("$", "")
    .replace(MEANING_CONTROL_MARKS_REGEX, "")
    .replace(MEANING_ARABIC_SEMICOLON_REGEX, " ")
    .replace(MEANING_PAREN_BAR_REGEX, "( $1 )")
    .replace(MEANING_INLINE_TAG_REGEX) { match ->
        val label = (match.groups[1]?.value ?: match.groups[3]?.value).orEmpty().trim()
        val value = (match.groups[2]?.value ?: match.groups[4]?.value).orEmpty().trim()
        when {
            label.isEmpty() -> match.value
            value.all(Char::isDigit) -> "{$label:$value}"
            else -> "$label:"
        }
    }
    .replace(MEANING_BRACED_LABEL_REGEX) { match ->
        val label = match.groupValues[1].trim()
        if (label.isEmpty()) "" else "$label:"
    }
    .replace(MEANING_BULLET_PREFIX_REGEX, "")
    .replace(MEANING_EMPTY_BRACES_REGEX, "")
    .replace(MEANING_TRAILING_SPACES_REGEX, "")
    .replace(MEANING_MULTISPACE_REGEX, " ")
    .trim()

private fun normalizeMeaningNote(raw: String): String {
    val cleaned = normalizeMeaningText(raw)
    return cleaned.takeUnless { it.equals("no", true) || it == "-" }.orEmpty()
}

private fun formatDefinition(
    meanings: List<Meaning>?,
    notePrefix: String,
    enableGlossaryLinks: Boolean
): DefinitionContent {
    val entries = formatMeaningEntries(meanings)
    if (entries.isEmpty()) return DefinitionContent(text = "", references = emptyList())

    val references = mutableListOf<GlossaryReference>()
    val text = buildString {
        entries.forEachIndexed { index, entry ->
            if (index > 0) append("\n\n")
            append("- ")
            append(
                stripGlossaryReferences(
                    raw = entry.definition,
                    enableGlossaryLinks = enableGlossaryLinks,
                    references = references,
                    baseOffset = length
                )
            )
            if (entry.note.isNotBlank()) {
                append("\n")
                append(notePrefix)
                append(
                    stripGlossaryReferences(
                        raw = entry.note,
                        enableGlossaryLinks = enableGlossaryLinks,
                        references = references,
                        baseOffset = length
                    )
                )
            }
        }
    }

    return DefinitionContent(text = text, references = references)
}

private fun formatMeaningEntries(meanings: List<Meaning>?): List<MeaningEntry> {
    if (meanings.isNullOrEmpty()) return emptyList()
    return meanings.mapNotNull { meaning ->
        val definition = normalizeMeaningText(meaning.arabic)
        val note = normalizeMeaningNote(meaning.note)
        if (definition.isEmpty() && note.isEmpty()) null else MeaningEntry(definition.ifBlank { "-" }, note)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ClickableDefinitionText(
    text: String,
    references: List<GlossaryReference>,
    style: TextStyle,
    textAlign: TextAlign,
    onReferenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    val referenceColor = MaterialTheme.colorScheme.primary
    val defaultColor = if (style.color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        style.color
    }
    val validReferences = references.filter { reference ->
        reference.id > 0 &&
            reference.start >= 0 &&
            reference.end > reference.start &&
            reference.end <= text.length
    }
    if (validReferences.isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = defaultColor,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout
        )
        return
    }

    val annotatedText = remember(text, validReferences, referenceColor) {
        buildAnnotatedString {
            append(text)
            validReferences.forEach { reference ->
                addStyle(
                    style = SpanStyle(
                        color = referenceColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = reference.start,
                    end = reference.end
                )
                addStringAnnotation(
                    tag = GLOSSARY_REFERENCE_ANNOTATION_TAG,
                    annotation = reference.id.toString(),
                    start = reference.start,
                    end = reference.end
                )
            }
        }
    }

    if (maxLines != Int.MAX_VALUE) {
        ClickableText(
            text = annotatedText,
            modifier = modifier,
            style = style.copy(color = defaultColor, textAlign = textAlign),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout,
            onClick = { offset ->
                annotatedText
                    .getStringAnnotations(GLOSSARY_REFERENCE_ANNOTATION_TAG, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.toIntOrNull()
                    ?.let(onReferenceClick)
            }
        )
        return
    }

    val lines = remember(text, validReferences) {
        splitDefinitionSegmentsByLine(buildDefinitionTextSegments(text, validReferences))
    }
    val horizontalAlignment = when (textAlign) {
        TextAlign.Right, TextAlign.End -> Alignment.End
        TextAlign.Center -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
    val horizontalArrangement = when (textAlign) {
        TextAlign.Right, TextAlign.End -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lines.forEach { line ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                line.forEach { segment ->
                    if (segment.referenceId != null) {
                        Text(
                            text = segment.text,
                            modifier = Modifier.clickable { onReferenceClick(segment.referenceId) },
                            style = style.copy(
                                color = referenceColor,
                                textDecoration = TextDecoration.Underline,
                                textDirection = TextDirection.ContentOrLtr
                            ),
                            color = referenceColor,
                            textAlign = TextAlign.Start
                        )
                    } else {
                        Text(
                            text = segment.text,
                            style = style,
                            color = defaultColor,
                            textAlign = textAlign
                        )
                    }
                }
            }
        }
    }
}

private fun buildDefinitionTextSegments(
    text: String,
    references: List<GlossaryReference>
): List<DefinitionTextSegment> {
    val segments = mutableListOf<DefinitionTextSegment>()
    var cursor = 0
    references.sortedWith(compareBy<GlossaryReference> { it.start }.thenBy { it.end })
        .forEach { reference ->
            if (reference.start < cursor || reference.end > text.length) return@forEach
            if (cursor < reference.start) {
                segments += DefinitionTextSegment(text.substring(cursor, reference.start), null)
            }
            segments += DefinitionTextSegment(text.substring(reference.start, reference.end), reference.id)
            cursor = reference.end
        }
    if (cursor < text.length) {
        segments += DefinitionTextSegment(text.substring(cursor), null)
    }
    return segments.filter { it.text.isNotEmpty() }
}

private fun splitDefinitionSegmentsByLine(
    segments: List<DefinitionTextSegment>
): List<List<DefinitionTextSegment>> {
    val lines = mutableListOf<MutableList<DefinitionTextSegment>>(mutableListOf())
    segments.forEach { segment ->
        val parts = segment.text.split('\n')
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                lines += mutableListOf<DefinitionTextSegment>()
            }
            if (part.isNotEmpty()) {
                lines.last() += DefinitionTextSegment(part, segment.referenceId)
            }
        }
    }
    return lines.map { line -> line.toList() }.ifEmpty { listOf(emptyList()) }
}

private fun shiftGlossaryReferences(
    references: List<GlossaryReference>,
    offset: Int
): List<GlossaryReference> {
    if (offset == 0) return references
    return references.map { reference ->
        reference.copy(
            start = if (reference.start >= 0) reference.start + offset else reference.start,
            end = if (reference.end >= 0) reference.end + offset else reference.end
        )
    }
}

private fun formatDetailMeaningEntries(details: WordDetailsResponse?): List<DetailMeaningEntry> {
    if (details == null || details.word.meanings.isEmpty()) return emptyList()
    return details.word.meanings.mapNotNull { meaning ->
        val definitionReferences = mutableListOf<GlossaryReference>()
        val noteReferences = mutableListOf<GlossaryReference>()
        val definition = stripGlossaryReferences(
            raw = normalizeMeaningText(meaning.arabic),
            enableGlossaryLinks = true,
            references = definitionReferences
        )
        val note = stripGlossaryReferences(
            raw = normalizeMeaningNote(meaning.note),
            enableGlossaryLinks = true,
            references = noteReferences
        )
        val references = definitionReferences + noteReferences
        val japanese = meaning.japanese.orEmpty().trim()
        val source = meaning.source.orEmpty().trim()
        val pictures = extractDetailPictures(meaning.pictures)
        val examples = details.examplesForMeaning(meaning)
        if (
            definition.isEmpty() &&
            note.isEmpty() &&
            japanese.isEmpty() &&
            pictures.isEmpty() &&
            examples.isEmpty()
        ) {
            null
        } else {
            DetailMeaningEntry(
                definition = definition.ifBlank { "-" },
                note = note,
                definitionReferences = definitionReferences,
                noteReferences = noteReferences,
                japanese = japanese,
                source = source,
                references = references,
                relatedGroups = meaning.related.filter { group ->
                    group.items.any { it.text.isNotBlank() || it.kana.isNotBlank() }
                },
                pictures = pictures,
                examples = examples
            )
        }
    }
}

private fun normalizeApiImageUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val normalized = trimmed.replace('\\', '/')
    return when {
        WINDOWS_ABSOLUTE_IMAGE_PATH_REGEX.matches(normalized) -> normalized
        normalized.startsWith("file:/", true) -> normalized
        LOCAL_ABSOLUTE_IMAGE_PATH_REGEX.matches(normalized) -> "file://$normalized"
        else -> null
    }
}

private fun extractDetailPictures(elements: List<JsonElement>): List<DetailPicture> {
    return elements.mapNotNull { element ->
        val url = extractApiPictureUrl(element)
            ?.let(::normalizeApiImageUrl)
            ?: return@mapNotNull null
        DetailPicture(
            url = url,
            description = extractPictureDescription(element).orEmpty()
        )
    }.distinctBy(DetailPicture::url)
}

private fun extractApiPictureUrl(element: JsonElement): String? {
    return extractPictureReference(element)
}

internal fun forceRtlText(text: String): String = "\u202B$text\u202C"

internal fun formatOfflineSearchPreview(raw: String): String {
    val references = mutableListOf<GlossaryReference>()
    return stripGlossaryReferences(
        raw = normalizeMeaningText(raw),
        enableGlossaryLinks = true,
        references = references
    ).replace("\n", " ").replace(SEARCH_PREVIEW_MULTISPACE_REGEX, " ").trim()
}

@Composable
internal fun CategorySearchBanner(label: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShinjikaiUi.CardShape,
        colors = CardDefaults.cardColors(containerColor = ShinjikaiUi.panelColor(alpha = 0.22f)),
        border = ShinjikaiUi.cardBorder(alpha = 0.22f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.detail_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DetailWordHeaderCard(
    kanji: String,
    kana: String,
    writingParts: List<com.shinjikai.dictionary.data.WritingPart>?,
    alternateWritings: List<String>,
    pronounceLabel: String,
    actions: DetailActions,
    onSpeakKana: () -> Unit,
    onKanjiClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val watermark = remember(kanji, kana) { kanjiWatermarkChar(kanji.ifBlank { kana }) }
    DetailSectionSurface {
        Box {
            watermark?.let { KanjiWatermark(mark = it, fontSize = 120.sp) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeadwordFuriganaText(
                    text = kanji,
                    reading = kana,
                    parts = writingParts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onKanjiClick),
                    baseStyle = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp,
                        lineHeight = 64.sp
                    ),
                    rubyStyle = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 15.sp,
                        color = colors.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    autoFit = true
                )
                if (alternateWritings.isNotEmpty()) {
                    var writingsExpanded by remember(alternateWritings) { mutableStateOf(false) }
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (writingsExpanded) 180f else 0f,
                        label = "otherWritingsArrow"
                    )
                    val toggleLabel = stringResource(R.string.detail_other_writings, alternateWritings.size)
                    Row(
                        modifier = Modifier
                            .clip(ShinjikaiUi.PillShape)
                            .clickable(role = Role.Button, onClickLabel = toggleLabel) {
                                writingsExpanded = !writingsExpanded
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .semantics { stateDescription = if (writingsExpanded) "مفتوحة" else "مطوية" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toggleLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_detail_expand),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(arrowRotation)
                        )
                    }
                    AnimatedVisibility(
                        visible = writingsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            alternateWritings.forEach { writing ->
                                DetailWordChip(text = writing)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailActionButton(
                        text = pronounceLabel,
                        iconRes = R.drawable.ic_detail_speaker,
                        onClick = onSpeakKana,
                        primary = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    DetailActionButton(
                        text = stringResource(if (actions.isBookmarked) R.string.detail_saved else R.string.detail_save),
                        iconRes = if (actions.isBookmarked) R.drawable.ic_nav_bookmarks_filled else R.drawable.ic_nav_bookmarks,
                        onClick = actions.onToggleBookmark,
                        active = actions.isBookmarked,
                        modifier = Modifier.weight(1f)
                    )
                    DetailActionButton(
                        text = stringResource(if (actions.isAddedToAnki) R.string.detail_anki_added else R.string.detail_anki_short),
                        iconRes = R.drawable.ic_anki,
                        onClick = actions.onAddToAnki,
                        active = actions.isAddedToAnki,
                        enabled = !actions.isAddingToAnki,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** First kanji of [text] (or its first character) for the faint corner watermark. */
internal fun kanjiWatermarkChar(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "-") return null
    var index = 0
    while (index < trimmed.length) {
        val codePoint = trimmed.codePointAt(index)
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
            return String(Character.toChars(codePoint))
        }
        index += Character.charCount(codePoint)
    }
    return String(Character.toChars(trimmed.codePointAt(0)))
}

/** Big faded kanji in the card's top-left corner, like the Anki card design. Decorative only. */
@Composable
internal fun BoxScope.KanjiWatermark(mark: String, fontSize: TextUnit) {
    Text(
        text = mark,
        modifier = Modifier
            .align(AbsoluteAlignment.TopLeft)
            .offset(x = 8.dp, y = (-fontSize.value * 0.1f).dp)
            .clearAndSetSemantics { },
        style = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        maxLines = 1,
        softWrap = false
    )
}

/** Actions owned by the detail route (bookmark state, Anki export, navigation). */
data class DetailActions(
    val isBookmarked: Boolean,
    val onToggleBookmark: () -> Unit,
    val isAddedToAnki: Boolean,
    val isAddingToAnki: Boolean,
    val onAddToAnki: () -> Unit,
    val onGoBack: () -> Unit
)

/** Up to three kanji of the word for the faint header mark; null when the word has none. */
private fun detailHeaderMark(kanji: String, kana: String): String? {
    val kanjiChars = kanji.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
    return when {
        kanjiChars.isNotEmpty() -> kanjiChars.take(3)
        kana.isNotBlank() && kana != "-" && kana.length <= 3 -> kana
        else -> null
    }
}

@Composable
internal fun DetailBackPill(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ShinjikaiUi.PillShape,
        color = colors.surface,
        contentColor = colors.onSurfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_back),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.nav_back),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun DetailActionButton(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        primary -> colors.primary
        active -> colors.primaryContainer
        else -> Color.Transparent
    }
    val content = when {
        primary -> colors.onPrimary
        active -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = ShinjikaiUi.PillShape,
        color = container,
        contentColor = if (enabled) content else content.copy(alpha = 0.38f),
        border = if (primary || active) null else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DetailCategoryCard(
    chips: List<CategoryChipModel>,
    onCategoryClick: (CategoryChipModel) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DetailSectionSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_category_label),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.detail_category_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { chip ->
                    DetailCategoryChip(text = chip.label, onClick = { onCategoryClick(chip) })
                }
            }
        }
    }
}

@Composable
private fun DetailMetaTiles(
    commonnessLevel: Int?,
    jlptLevel: Int?
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (commonnessLevel != null) {
            DetailMetaTile(
                label = stringResource(R.string.detail_meta_commonness),
                value = stringResource(detailCommonnessLevelRes(commonnessLevel)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (index < commonnessLevel) colors.primary else colors.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
        if (jlptLevel != null) {
            DetailMetaTile(
                label = stringResource(R.string.detail_meta_level),
                value = "JLPT N$jlptLevel",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@StringRes
private fun detailCommonnessLevelRes(level: Int): Int = when (level) {
    5 -> R.string.commonness_level_5
    4 -> R.string.commonness_level_4
    3 -> R.string.commonness_level_3
    2 -> R.string.commonness_level_2
    else -> R.string.commonness_level_1
}

@Composable
private fun DetailMetaTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = DetailTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            extra?.invoke()
        }
    }
}

private val DetailSectionShape = RoundedCornerShape(28.dp)
private val DetailTileShape = RoundedCornerShape(22.dp)
private val DetailInnerShape = RoundedCornerShape(18.dp)

@Composable
private fun DetailSectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DetailSectionShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

/** Titled bento card used by every detail section. */
@Composable
private fun DetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DetailSectionSurface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailSectionTitle(text = title, trailing = trailing)
            content()
        }
    }
}

@Composable
private fun DetailSectionTitle(
    text: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailWordChip(
    text: String,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(ShinjikaiUi.PillShape)
            .background(colors.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, colors.outlineVariant, ShinjikaiUi.PillShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = colors.onSecondaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun DetailCategoryChip(
    text: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(ShinjikaiUi.PillShape)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
            maxLines = 1
        )
        Icon(
            painter = painterResource(R.drawable.ic_settings_chevron),
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun DetailOutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = ShinjikaiUi.PillShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DefinitionsCard(
    title: String,
    content: DefinitionContent,
    onGlossaryReferenceClick: (Int) -> Unit
) {
    var expanded by remember(content.text) { mutableStateOf(false) }
    val definition = content.text
    val canExpand = definition.length > 260 || definition.count { it == '\n' } >= 4
    DetailSectionCard(title = title) {
        ClickableDefinitionText(
            text = definition,
            references = content.references,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textDirection = TextDirection.Rtl,
                fontSize = 17.sp,
                lineHeight = 29.sp
            ),
            textAlign = TextAlign.Right,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            onReferenceClick = onGlossaryReferenceClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
        if (content.references.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content.references.distinctBy { "${it.id}|${it.label}" }.forEach { reference ->
                    DetailWordChip(
                        text = reference.label,
                        onClick = { onGlossaryReferenceClick(reference.id) }
                    )
                }
            }
        }
        if (canExpand) {
            DetailOutlinedPillButton(
                text = if (expanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more),
                onClick = { expanded = !expanded }
            )
        }
    }
}

internal fun buildDetailAnkiNoteContent(
    useOfflineMode: Boolean,
    detailState: DetailUiState
): com.shinjikai.dictionary.integration.AnkiNoteContent? {
    val item = detailState.selectedItem ?: return null
    val detailKana = detailState.details?.word?.kana.orEmpty()
    val kanji = detailState.details?.word?.writings?.firstOrNull { it.text.isNotBlank() }?.text
        .orEmpty()
        .ifBlank {
            detailKana.ifBlank {
                item.primaryWriting.ifBlank { item.kana.ifBlank { "-" } }
            }
        }
    val kana = detailKana.ifBlank { item.kana.ifBlank { "-" } }
    // Prefer the full meanings from the loaded details (online or offline); the list-row
    // summary can be empty for entries opened from history, categories or random picks.
    val summaryText = normalizeMeaningText(item.meaningSummary).let { text ->
        if (useOfflineMode) {
            text
        } else {
            text.replace("\n", " ").replace(SEARCH_PREVIEW_MULTISPACE_REGEX, " ").trim()
        }
    }
    val definitionContent = formatDefinition(
        meanings = detailState.details?.word?.meanings,
        notePrefix = "",
        enableGlossaryLinks = false
    ).takeIf { it.text.isNotBlank() }
        ?: DefinitionContent(
            text = summaryText,
            references = emptyList()
        )

    return buildAnkiNoteContent(
        kanji = kanji,
        kana = kana,
        meaning = definitionContent.text,
        examples = detailState.details?.let { details ->
            (details.directMeaningExamples() + details.additionalExamples())
                .distinctBy { it.id.takeIf { id -> id > 0 } ?: it.stableContentKey() }
        }.orEmpty()
    )
}

private fun buildAnkiNoteContent(
    kanji: String,
    kana: String,
    meaning: String,
    examples: List<SentenceExample>
): com.shinjikai.dictionary.integration.AnkiNoteContent {
    val cleanedMeaning = meaning.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "-" }
        .joinToString("\n")

    val primaryExample = examples.firstOrNull()
    val example = buildString {
        primaryExample?.let {
            val exampleText = it.text.ifBlank { it.kana }.trim()
            if (exampleText.isNotBlank()) {
                append(exampleText)
            }
            if (it.arabic.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(it.arabic.trim())
            }
        }
    }.trim()

    return com.shinjikai.dictionary.integration.AnkiNoteContent(
        expression = kanji.ifBlank { kana }.ifBlank { "-" },
        reading = kana.takeIf { it.isNotBlank() && it != "-" && it != kanji }.orEmpty(),
        meaning = cleanedMeaning.ifBlank { "-" },
        example = example,
        speechText = kana.ifBlank { kanji }.ifBlank { "-" }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MeaningEntriesCard(
    title: String,
    entries: List<DetailMeaningEntry>,
    notePrefix: String,
    onImageClick: (String) -> Unit,
    onGlossaryReferenceClick: (Int) -> Unit,
    onExampleWordClick: (Int) -> Unit,
    onRelatedWordClick: (RelatedWordItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DetailSectionTitle(
            text = title,
            trailing = entries.size.toString(),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp)
        )
        entries.forEachIndexed { index, entry ->
            MeaningEntryCard(
                entryNumber = index + 1,
                entry = entry,
                notePrefix = notePrefix,
                onImageClick = onImageClick,
                onGlossaryReferenceClick = onGlossaryReferenceClick,
                onExampleWordClick = onExampleWordClick,
                onRelatedWordClick = onRelatedWordClick
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MeaningEntryCard(
    entryNumber: Int,
    entry: DetailMeaningEntry,
    notePrefix: String,
    onImageClick: (String) -> Unit,
    onGlossaryReferenceClick: (Int) -> Unit,
    onExampleWordClick: (Int) -> Unit,
    onRelatedWordClick: (RelatedWordItem) -> Unit
) {
    var definitionExpanded by remember(entry.definition) { mutableStateOf(false) }
    var definitionCanExpand by remember(entry.definition) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    DetailSectionSurface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entryNumber.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (entry.source.isNotBlank()) {
                    Text(
                        text = entry.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            ClickableDefinitionText(
                text = entry.definition,
                references = entry.definitionReferences,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurface,
                    textDirection = TextDirection.Rtl,
                    fontSize = 17.sp,
                    lineHeight = 29.sp
                ),
                textAlign = TextAlign.Right,
                onReferenceClick = onGlossaryReferenceClick,
                maxLines = if (definitionExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (!definitionExpanded) {
                        definitionCanExpand = textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > 3
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (definitionCanExpand) {
                DetailOutlinedPillButton(
                    text = if (definitionExpanded) {
                        stringResource(R.string.detail_show_less)
                    } else {
                        stringResource(R.string.detail_show_more)
                    },
                    onClick = { definitionExpanded = !definitionExpanded }
                )
            }
            if (entry.note.isNotBlank()) {
                ClickableDefinitionText(
                    text = "$notePrefix${entry.note}",
                    references = shiftGlossaryReferences(entry.noteReferences, notePrefix.length),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.onSurfaceVariant,
                        textDirection = TextDirection.Rtl,
                        lineHeight = 24.sp
                    ),
                    textAlign = TextAlign.Right,
                    onReferenceClick = onGlossaryReferenceClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (entry.japanese.isNotBlank()) {
                val citedJapaneseDefinition = formatJapaneseDefinitionWithSource(
                    japanese = entry.japanese,
                    source = entry.source
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DetailInnerShape,
                    color = colors.background
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = stringResource(R.string.detail_japanese_definition),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Rtl),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            textAlign = TextAlign.Right
                        )
                        SelectionContainer {
                            Text(
                                text = citedJapaneseDefinition,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = TextDirection.Ltr,
                                    lineHeight = 24.sp
                                ),
                                color = colors.onSurface,
                                textAlign = TextAlign.Left
                            )
                        }
                    }
                }
            }

            entry.relatedGroups.forEach { group ->
                MeaningRelatedGroupSection(
                    group = group,
                    onRelatedWordClick = onRelatedWordClick
                )
            }

            if (entry.pictures.isNotEmpty()) {
                EntryPicturesRow(
                    pictures = entry.pictures,
                    onImageClick = onImageClick
                )
            }

            if (entry.examples.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
                DetailSectionTitle(
                    text = stringResource(R.string.detail_meaning_examples_title),
                    trailing = stringResource(R.string.detail_examples_count, entry.examples.size)
                )
                ExampleList(
                    items = entry.examples,
                    initiallyVisible = EXAMPLES_PAGE_SIZE,
                    onWordClick = onExampleWordClick
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MeaningRelatedGroupSection(
    group: RelatedGroup,
    onRelatedWordClick: (RelatedWordItem) -> Unit
) {
    val title = group.label.ifBlank { stringResource(R.string.detail_related_words_title) }
    val visibleItems = group.items.filter { it.text.isNotBlank() || it.kana.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleItems.forEach { relatedWord ->
                DetailWordChip(
                    text = relatedWord.text.ifBlank { relatedWord.kana },
                    onClick = { onRelatedWordClick(relatedWord) }
                )
            }
        }
    }
}

internal fun stripGlossaryReferences(
    raw: String,
    enableGlossaryLinks: Boolean,
    references: MutableList<GlossaryReference>,
    baseOffset: Int = 0
): String {
    if (!enableGlossaryLinks) {
        return raw
    }

    val result = StringBuilder()
    var cursor = 0
    for (match in GLOSSARY_REFERENCE_REGEX.findAll(raw)) {
        val range = match.range
        if (cursor < range.first) {
            result.append(raw.substring(cursor, range.first))
        }
        val label = match.groupValues[1].trim()
        val id = match.groupValues[2].toIntOrNull()
        if (!label.isNullOrEmpty()) {
            val displayLabel = keepGlossaryTermOnOneLine(label)
            val start = result.length
            result.append(displayLabel)
            if (id != null && id > 0) {
                references += GlossaryReference(
                    id = id,
                    label = label,
                    start = baseOffset + start,
                    end = baseOffset + result.length
                )
            }
        }
        cursor = range.last + 1
    }
    if (cursor < raw.length) {
        result.append(raw.substring(cursor))
    }
    return result.toString()
}

private fun keepGlossaryTermOnOneLine(label: String): String {
    if (label.length < 2) return label
    return buildString {
        label.forEachIndexed { index, char ->
            if (index > 0 && shouldJoinGlossaryTermChars(label[index - 1], char)) {
                append(NO_BREAK_JOINER)
            }
            append(char)
        }
    }
}

private fun shouldJoinGlossaryTermChars(previous: Char, current: Char): Boolean {
    return !previous.isWhitespace() && !current.isWhitespace()
}

private fun formatJapaneseDefinitionWithSource(japanese: String, source: String): String {
    val trimmedJapanese = japanese.trim()
    val trimmedSource = source.trim()
    if (trimmedSource.isBlank() || trimmedJapanese.contains(trimmedSource)) {
        return trimmedJapanese
    }
    return "$trimmedJapanese\n\u3014$trimmedSource\u3015"
}

@Composable
private fun EntryPicturesRow(
    pictures: List<DetailPicture>,
    onImageClick: (String) -> Unit
) {
    if (pictures.size == 1) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PictureCard(
                picture = pictures.first(),
                onClick = { onImageClick(pictures.first().url) }
            )
        }
    } else {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = pictures,
                key = DetailPicture::url,
                contentType = { "detail-picture" }
            ) { picture ->
                PictureCard(picture = picture, onClick = { onImageClick(picture.url) })
            }
        }
    }
}

@Composable
private fun PictureCard(picture: DetailPicture, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = DetailTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(width = 240.dp, height = if (picture.description.isBlank()) 170.dp else 214.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = picture.url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
            )
            if (picture.description.isNotBlank()) {
                Text(
                    text = forceRtlText(picture.description),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PicturesSection(
    title: String,
    pictures: List<DetailPicture>,
    onImageClick: (String) -> Unit
) {
    DetailSectionCard(title = title, trailing = pictures.size.toString()) {
        EntryPicturesRow(pictures = pictures, onImageClick = onImageClick)
    }
}

@Composable
private fun KanjiInformationSection(
    title: String,
    items: List<KanjiInfo>
) {
    val colors = MaterialTheme.colorScheme
    DetailSectionCard(title = title) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = DetailTileShape,
                        color = colors.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, colors.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = item.displayCharacter(),
                                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                            }
                            if (item.onYomi.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.detail_on_yomi, item.onYomi),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (item.kunYomi.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.detail_kun_yomi, item.kunYomi),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (item.meaning.isNotBlank()) {
                                HorizontalDivider(color = colors.outlineVariant)
                                Text(
                                    text = forceRtlText(item.meaning),
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ZoomableImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember(imageUrl) { mutableStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            shape = ShinjikaiUi.CardShape,
            color = Color.Black.copy(alpha = 0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_settings_close), contentDescription = stringResource(R.string.action_cancel))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(ShinjikaiUi.CompactShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(imageUrl) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    if (newScale == 1f) {
                                        offset = Offset.Zero
                                    } else {
                                        offset += pan
                                    }
                                    scale = newScale
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
                Text(
                    text = stringResource(R.string.detail_pictures_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.74f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RelatedWordsCard(
    title: String,
    items: List<RelatedWordItem>,
    onWordClick: (RelatedWordItem) -> Unit
) {
    val wordFallback = stringResource(R.string.detail_word_fallback)
    DetailSectionCard(title = title) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                DetailWordChip(
                    text = item.text.ifBlank { item.kana.ifBlank { wordFallback.format(item.wordId) } },
                    onClick = { onWordClick(item) }
                )
            }
        }
    }
}

@Composable
private fun ExamplesCard(
    title: String,
    items: List<SentenceExample>,
    onWordClick: (Int) -> Unit
) {
    DetailSectionCard(
        title = title,
        trailing = stringResource(R.string.detail_examples_count, items.size)
    ) {
        ExampleList(
            items = items,
            initiallyVisible = items.size,
            onWordClick = onWordClick
        )
    }
}

@Composable
private fun ExampleList(
    items: List<SentenceExample>,
    initiallyVisible: Int,
    onWordClick: (Int) -> Unit
) {
    var visibleCount by remember(items, initiallyVisible) {
        mutableStateOf(initiallyVisible.coerceAtMost(items.size))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.take(visibleCount).forEach { item ->
            SentenceExampleRow(item = item, onWordClick = onWordClick)
        }
        if (visibleCount < items.size) {
            DetailOutlinedPillButton(
                text = stringResource(R.string.detail_show_more),
                onClick = {
                    visibleCount = (visibleCount + EXAMPLES_PAGE_SIZE).coerceAtMost(items.size)
                }
            )
        } else if (items.size > initiallyVisible) {
            DetailOutlinedPillButton(
                text = stringResource(R.string.detail_show_less),
                onClick = { visibleCount = initiallyVisible.coerceAtMost(items.size) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
private fun SentenceExampleRow(
    item: SentenceExample,
    onWordClick: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val exampleFallback = stringResource(R.string.detail_example_fallback)
    val exampleCopiedMessage = stringResource(R.string.detail_example_copied)
    val copyLabel = stringResource(R.string.detail_copy_example)
    val displayText = item.text.ifBlank { item.kana.ifBlank { exampleFallback } }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, colors.outlineVariant, shape)
            .combinedClickable(
                onClick = {},
                onLongClickLabel = copyLabel,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboardManager.setText(AnnotatedString(displayText))
                    Toast.makeText(context, exampleCopiedMessage, Toast.LENGTH_SHORT).show()
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RubyJapaneseText(
            text = displayText,
            kana = item.kana,
            links = item.wordLinks.orEmpty(),
            onWordClick = onWordClick,
            modifier = Modifier.fillMaxWidth()
        )
        if (item.arabic.isNotBlank()) {
            HorizontalDivider(color = colors.outlineVariant)
            Text(
                text = forceRtlText(item.arabic),
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = TextDirection.Rtl,
                    lineHeight = 24.sp
                ),
                color = colors.onSurface,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun isRubyKanji(char: Char): Boolean {
    val block = Character.UnicodeBlock.of(char)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        char == '々' ||
        char == '〆'
}

private fun rubyLiteralEquals(textChar: Char, kanaChar: Char): Boolean {
    return textChar == kanaChar || normalizeRubyLiteral(textChar) == normalizeRubyLiteral(kanaChar)
}

private fun normalizeRubyLiteral(char: Char): Char {
    return when (char) {
        '。', '.' -> '。'
        '、', ',' -> '、'
        '！', '!' -> '！'
        '？', '?' -> '？'
        '：', ':' -> '：'
        '；', ';' -> '；'
        '（', '(' -> '（'
        '）', ')' -> '）'
        '「', '『' -> '「'
        '」', '』' -> '」'
        'ー', 'ｰ' -> 'ー'
        else -> char
    }
}

private fun rubyCanDropLiteral(char: Char): Boolean {
    return char.isWhitespace() || char == '"' || char == '\'' || char == '“' || char == '”'
}
