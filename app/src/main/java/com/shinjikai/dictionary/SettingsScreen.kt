package com.shinjikai.dictionary

import androidx.compose.ui.text.style.TextDirection
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.shinjikai.dictionary.data.AppThemeMode
import com.shinjikai.dictionary.integration.ANKIDROID_PERMISSION
import com.shinjikai.dictionary.integration.AnkiExporter
import com.shinjikai.dictionary.ui.SettingsUiState
import com.shinjikai.dictionary.ui.InstalledDictionaryUiItem
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.text.DateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsCardShape = RoundedCornerShape(28.dp)
private val SettingsTileShape = RoundedCornerShape(22.dp)
private val SettingsChipShape = RoundedCornerShape(12.dp)
private val SettingsTileChipShape = RoundedCornerShape(14.dp)

private const val ShinjikaiWebsiteUrl = "https://shinjikai.app"
private const val ShinjikaiSourceUrl = "https://github.com/objx0/Shinjikai-Android"
private const val CcByLicenseUrl = "https://creativecommons.org/licenses/by/4.0/"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreenContent(
    appVersionLabel: String,
    supportsDynamicColor: Boolean,
    uiState: SettingsUiState,
    viewModel: ShinjikaiViewModel,
    onOpenLocalDictionary: () -> Unit,
    onOpenAnkiExporterSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    val loadingLabel = stringResource(R.string.settings_loading_inline)
    val dictionariesSubtitle = when {
        uiState.isImportingOfflineData -> uiState.offlineImportPhase ?: loadingLabel
        !uiState.isOfflineDictionaryStateLoaded -> loadingLabel
        uiState.offlineImportError -> stringResource(R.string.offline_import_failure)
        uiState.offlineTermCount > 0 -> stringResource(R.string.settings_dictionaries_tile_description)
        else -> stringResource(R.string.settings_offline_status_missing)
    }
    val ankiFallback = stringResource(R.string.settings_anki_exporter_description)
    val ankiSubtitle = settings.selectedAnkiDeckName.ifBlank { ankiFallback }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = ShinjikaiUi.BottomBarClearance
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsHeader()

            SettingsSectionCard(title = stringResource(R.string.settings_appearance_title)) {
                ThemeModeSegmentedControl(
                    selectedMode = settings.themeMode,
                    onModeSelected = viewModel::setThemeMode
                )
                if (supportsDynamicColor) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        iconRes = R.drawable.ic_settings_palette,
                        title = stringResource(R.string.settings_dynamic_color),
                        description = stringResource(R.string.settings_dynamic_color_description),
                        checked = settings.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor
                    )
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_data_title)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsTile(
                        iconRes = R.drawable.ic_settings_dictionaries,
                        title = stringResource(R.string.settings_dictionaries_tile_title),
                        subtitle = dictionariesSubtitle,
                        onClick = onOpenLocalDictionary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    SettingsTile(
                        iconRes = R.drawable.ic_anki,
                        title = stringResource(R.string.settings_anki_exporter_title),
                        subtitle = ankiSubtitle,
                        onClick = onOpenAnkiExporterSettings,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            AboutCreditCard(
                onOpenWebsite = { openUrl(ShinjikaiWebsiteUrl) },
                onOpenLicense = { openUrl(CcByLicenseUrl) }
            )
            AboutSourceRow(onClick = { openUrl(ShinjikaiSourceUrl) })
            AboutFooter(versionLabel = appVersionLabel)
        }
    }
}

/** Credits the dictionary source (shinjikai.app) and states the app is unofficial. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AboutCreditCard(
    onOpenWebsite: () -> Unit,
    onOpenLicense: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpenWebsite,
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            KanjiWatermark(mark = "辞", fontSize = 130.sp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(colors.primary, SettingsTileChipShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "深",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 22.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_about_credit_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_external),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_unofficial),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                        color = colors.onSurfaceVariant
                    )
                    AboutLicenseBadge(
                        text = stringResource(R.string.settings_about_license_cc_by),
                        onClick = onOpenLicense,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSourceRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconChip(
                iconRes = R.drawable.ic_settings_github,
                size = 44.dp,
                iconSize = 22.dp,
                shape = SettingsTileChipShape
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_open_source),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    AboutLicenseBadge(text = stringResource(R.string.settings_about_license_mit))
                }
                Text(
                    text = stringResource(R.string.settings_about_repo_label),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_settings_external),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AboutLicenseBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.primaryContainer.copy(alpha = 0.55f), CircleShape)
            .border(1.dp, colors.primaryContainer, CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
        fontWeight = FontWeight.Bold,
        color = colors.onPrimaryContainer
    )
}

@Composable
private fun AboutFooter(versionLabel: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name_kanji),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp
                ),
                fontWeight = FontWeight.Bold,
                color = colors.primary
            )
            Text(
                text = versionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_about_made_with),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Icon(
                painter = painterResource(R.drawable.ic_settings_heart),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = stringResource(R.string.settings_about_made_for),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.settings_title_kanji),
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    titlePadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .padding(titlePadding)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

private data class ThemeModeOption(
    val mode: AppThemeMode,
    val iconRes: Int,
    val label: String
)

@Composable
private fun ThemeModeSegmentedControl(
    selectedMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        ThemeModeOption(AppThemeMode.System, R.drawable.ic_settings_theme_system, stringResource(R.string.settings_theme_system)),
        ThemeModeOption(AppThemeMode.Light, R.drawable.ic_settings_theme_light, stringResource(R.string.settings_theme_light)),
        ThemeModeOption(AppThemeMode.Dark, R.drawable.ic_settings_theme_dark, stringResource(R.string.settings_theme_dark))
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, ShinjikaiUi.PillShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val selected = option.mode == selectedMode
            val containerColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "themeOptionContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "themeOptionContent"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(ShinjikaiUi.PillShape)
                    .background(containerColor)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onModeSelected(option.mode) }
                    ),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsIconChip(
    iconRes: Int,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = SettingsChipShape
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SettingsRowText(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconChip(iconRes = iconRes)
        SettingsRowText(
            title = title,
            description = description,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun SettingsTile(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 160.dp),
        shape = SettingsTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                SettingsIconChip(
                    iconRes = iconRes,
                    size = 44.dp,
                    iconSize = 22.dp,
                    shape = SettingsTileChipShape
                )
                Icon(
                    painter = painterResource(R.drawable.ic_settings_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LocalDictionaryScreenContent(
    uiState: SettingsUiState,
    onPickOfflineZip: () -> Unit,
    onToggleDictionary: (String, Boolean) -> Unit,
    onDeleteDictionary: (String) -> Unit,
    onGoBack: () -> Unit
) {
    val dictionaries = uiState.installedDictionaries
    val activeCount = dictionaries.count { it.enabled || it.isBuiltIn }
    val entryCountLabel = remember(uiState.offlineTermCount) {
        NumberFormat.getIntegerInstance().format(uiState.offlineTermCount)
    }
    val lastUpdateLabel = remember(uiState.offlineLastImportEpochMs) {
        uiState.offlineLastImportEpochMs?.let {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
        } ?: "—"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSubpageHeader(
                title = stringResource(R.string.settings_local_dictionary),
                onGoBack = onGoBack
            )

            SettingsSectionCard(
                title = stringResource(R.string.settings_overview_title),
                spacing = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DictionaryStatTile(
                        iconRes = R.drawable.ic_settings_entries,
                        value = entryCountLabel,
                        label = stringResource(R.string.settings_entries_offline_label),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    DictionaryStatTile(
                        iconRes = R.drawable.ic_settings_clock,
                        value = lastUpdateLabel,
                        label = stringResource(R.string.settings_offline_metric_last_update_title),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            if (dictionaries.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsCardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(start = 4.dp, top = 16.dp, end = 4.dp, bottom = 6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_dictionaries_tile_title),
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_active_dictionaries,
                                    activeCount,
                                    dictionaries.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        dictionaries.forEachIndexed { index, dictionary ->
                            InstalledDictionaryRow(
                                dictionary = dictionary,
                                isImporting = uiState.isImportingOfflineData,
                                onToggle = { enabled -> onToggleDictionary(dictionary.id, enabled) },
                                onDelete = { onDeleteDictionary(dictionary.id) }
                            )
                            if (index < dictionaries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 12.dp, end = 64.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isImportingOfflineData) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = SettingsCardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, SettingsTileChipShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_download),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            SettingsRowText(
                                title = stringResource(R.string.settings_offline_status_importing),
                                description = uiState.offlineImportPhase
                                    ?: stringResource(R.string.settings_loading_inline),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { uiState.offlineImportProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(ShinjikaiUi.PillShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            AddDictionaryButton(
                enabled = !uiState.isImportingOfflineData,
                onClick = onPickOfflineZip
            )

            uiState.offlineImportStatus?.let { status ->
                StatusMessage(message = status, isError = uiState.offlineImportError)
            }

            Text(
                text = stringResource(R.string.settings_dictionaries_footer),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DictionaryStatTile(
    iconRes: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = SettingsTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsIconChip(iconRes = iconRes, size = 40.dp, shape = SettingsTileChipShape)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DictionaryTag(text: String, highlighted: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .background(
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = ShinjikaiUi.PillShape
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1
    )
}

@Composable
private fun AddDictionaryButton(
    enabled: Boolean,
    onClick: () -> Unit,
    title: String = stringResource(R.string.settings_add_dictionary),
    description: String = stringResource(R.string.settings_add_dictionary_hint)
) {
    val dashColor = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.7f else 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(SettingsCardShape)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                    )
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = SettingsTileChipShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_add),
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
        }
        SettingsRowText(
            title = title,
            description = description,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InstalledDictionaryRow(
    dictionary: InstalledDictionaryUiItem,
    isImporting: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember(dictionary.id) { mutableStateOf(false) }
    val canDelete = !dictionary.isBuiltIn && !isImporting
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteConfirmation = true
            }
            false
        }
    )
    val subtitle = if (dictionary.isBuiltIn) {
        dictionary.source
    } else {
        dictionary.fileName ?: dictionary.source
    }
    val toggleLabel = stringResource(R.string.settings_toggle_dictionary_a11y, dictionary.name)

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canDelete,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SettingsTileShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_trash),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconChip(iconRes = R.drawable.ic_nav_browse, size = 40.dp, shape = SettingsTileChipShape)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dictionary.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DictionaryTag(
                            text = if (dictionary.isBuiltIn) {
                                stringResource(R.string.settings_builtin_tag)
                            } else {
                                "Yomitan"
                            },
                            highlighted = dictionary.isBuiltIn
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!dictionary.isBuiltIn) {
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = canDelete
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_trash),
                            contentDescription = stringResource(
                                R.string.settings_delete_dictionary_a11y,
                                dictionary.name
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Switch(
                        checked = dictionary.enabled,
                        onCheckedChange = onToggle,
                        enabled = !isImporting,
                        modifier = Modifier.semantics { contentDescription = toggleLabel }
                    )
                }
            }
        }
    )

    if (showDeleteConfirmation) {
        ShinjikaiDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = stringResource(R.string.settings_delete_dictionary_title),
            message = stringResource(R.string.settings_delete_dictionary_message, dictionary.name),
            iconRes = R.drawable.ic_settings_trash,
            confirmLabel = stringResource(R.string.settings_delete_dictionary_confirm),
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            destructive = true
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun AnkiExporterSettingsScreenContent(
    selectedDeckName: String,
    onSelectDeck: (String) -> Unit,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val canRequestDirectAnkiAdd = remember(context) {
        AnkiExporter.canRequestDirectAdd(context)
    }
    var hasAnkiDatabasePermission by remember(context) {
        mutableStateOf(AnkiExporter.hasDatabasePermission(context))
    }
    var availableDecks by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingDecks by remember { mutableStateOf(false) }
    var showNewDeckDialog by rememberSaveable { mutableStateOf(false) }
    suspend fun refreshAvailableDecks() {
        statusMessage = null
        isLoadingDecks = true
        val decks = loadAnkiDeckNamesOffMain(context)
        isLoadingDecks = false
        availableDecks = decks
        statusMessage = if (decks.isEmpty()) {
            context.getString(R.string.settings_anki_no_decks)
        } else {
            null
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAnkiDatabasePermission = granted
        if (granted) {
            coroutineScope.launch {
                refreshAvailableDecks()
            }
        } else {
            availableDecks = emptyList()
            isLoadingDecks = false
            statusMessage = context.getString(R.string.settings_anki_permission_required)
        }
    }

    LaunchedEffect(Unit) {
        when {
            !canRequestDirectAnkiAdd ->
                statusMessage = context.getString(R.string.settings_anki_install_required)
            hasAnkiDatabasePermission -> refreshAvailableDecks()
            else -> statusMessage = context.getString(R.string.settings_anki_allow_access)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSubpageHeader(
                title = stringResource(R.string.settings_anki_exporter_title),
                onGoBack = onGoBack
            )

            SettingsSectionCard(title = stringResource(R.string.settings_anki_selected_deck)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, SettingsTileChipShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_anki),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    val deckPath = selectedDeckName.split(AnkiDeckSeparator)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deckPath.last(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (deckPath.size > 1) {
                            Text(
                                text = stringResource(
                                    R.string.settings_anki_inside_parent,
                                    deckPath.dropLast(1).joinToString(AnkiDeckPathDisplaySeparator)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_anki_selected_deck_description),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val showConnectionCard = !canRequestDirectAnkiAdd ||
                !hasAnkiDatabasePermission ||
                isLoadingDecks ||
                statusMessage != null
            if (showConnectionCard) {
                SettingsSectionCard(title = stringResource(R.string.settings_anki_connection_title)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoadingDecks) {
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            SettingsIconChip(
                                iconRes = when {
                                    !canRequestDirectAnkiAdd -> R.drawable.ic_settings_info
                                    !hasAnkiDatabasePermission -> R.drawable.ic_settings_lock
                                    else -> R.drawable.ic_settings_info
                                }
                            )
                        }
                        Text(
                            text = if (isLoadingDecks) {
                                stringResource(R.string.settings_loading_inline)
                            } else {
                                statusMessage.orEmpty()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    when {
                        !canRequestDirectAnkiAdd -> SettingsPillButton(
                            text = stringResource(R.string.settings_anki_install_button),
                            iconRes = R.drawable.ic_settings_external,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ichi2.anki"))
                                    )
                                }.onFailure {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://play.google.com/store/apps/details?id=com.ichi2.anki")
                                        )
                                    )
                                }
                            }
                        )
                        !hasAnkiDatabasePermission -> SettingsPillButton(
                            text = stringResource(R.string.settings_anki_allow_access_button),
                            iconRes = R.drawable.ic_settings_lock,
                            onClick = { permissionLauncher.launch(ANKIDROID_PERMISSION) }
                        )
                        !isLoadingDecks && availableDecks.isEmpty() -> SettingsPillButton(
                            text = stringResource(R.string.settings_anki_refresh),
                            iconRes = R.drawable.ic_settings_refresh,
                            primary = false,
                            onClick = { coroutineScope.launch { refreshAvailableDecks() } }
                        )
                    }
                }
            }

            if (availableDecks.isNotEmpty()) {
                val deckGroups = remember(availableDecks, selectedDeckName) {
                    groupAnkiDecks((availableDecks + selectedDeckName).distinct())
                }
                val standaloneTitle = stringResource(R.string.settings_anki_standalone_decks)
                val parentDeckLabel = stringResource(R.string.settings_anki_parent_deck_all)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsCardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_anki_choose_deck) + " · " +
                                    stringResource(R.string.settings_anki_deck_count, availableDecks.size),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { heading() },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { coroutineScope.launch { refreshAvailableDecks() } },
                                enabled = !isLoadingDecks
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_refresh),
                                    contentDescription = stringResource(R.string.settings_anki_refresh),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        deckGroups.forEach { group ->
                            Column(
                                modifier = Modifier.selectableGroup(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnkiDeckGroupHeader(title = group.parent ?: standaloneTitle)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    group.decks.forEach { deckName ->
                                        AnkiDeckChip(
                                            label = when {
                                                group.parent == null -> deckName
                                                deckName == group.parent -> parentDeckLabel
                                                else -> deckName
                                                    .removePrefix(group.parent + AnkiDeckSeparator)
                                                    .split(AnkiDeckSeparator)
                                                    .joinToString(AnkiDeckPathDisplaySeparator)
                                            },
                                            fullName = deckName,
                                            selected = deckName == selectedDeckName,
                                            onSelect = { onSelectDeck(deckName) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (canRequestDirectAnkiAdd && hasAnkiDatabasePermission) {
                AddDictionaryButton(
                    enabled = true,
                    onClick = { showNewDeckDialog = true },
                    title = stringResource(R.string.settings_anki_new_deck),
                    description = stringResource(R.string.settings_anki_new_deck_description)
                )
            }

            if (selectedDeckName != DefaultAnkiDeckName) {
                SettingsPillButton(
                    text = stringResource(R.string.settings_anki_use_default_deck),
                    iconRes = R.drawable.ic_settings_refresh,
                    primary = false,
                    onClick = { onSelectDeck(DefaultAnkiDeckName) }
                )
            }
        }
    }

    if (showNewDeckDialog) {
        AnkiNewDeckDialog(
            onDismiss = { showNewDeckDialog = false },
            onConfirm = { name ->
                showNewDeckDialog = false
                onSelectDeck(name)
            }
        )
    }
}

private const val DefaultAnkiDeckName = "Shinjikai"
private const val AnkiDeckSeparator = "::"
private const val AnkiDeckPathDisplaySeparator = " › "

private data class AnkiDeckGroup(val parent: String?, val decks: List<String>)

/**
 * Groups Anki deck names by their top-level parent ("日本語::JLPT N2" goes under "日本語").
 * Decks with no children and no parent come first as one "standalone" group.
 */
private fun groupAnkiDecks(decks: List<String>): List<AnkiDeckGroup> {
    val sorted = decks.filter { it.isNotBlank() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    val parents = sorted
        .filter { it.contains(AnkiDeckSeparator) }
        .map { it.substringBefore(AnkiDeckSeparator) }
        .toSet()
    val standalone = sorted.filter { !it.contains(AnkiDeckSeparator) && it !in parents }
    val grouped = parents.sortedWith(String.CASE_INSENSITIVE_ORDER).map { parent ->
        AnkiDeckGroup(
            parent = parent,
            decks = sorted.filter { it == parent || it.startsWith(parent + AnkiDeckSeparator) }
        )
    }
    return buildList {
        if (standalone.isNotEmpty()) add(AnkiDeckGroup(parent = null, decks = standalone))
        addAll(grouped)
    }
}

@Composable
private fun AnkiDeckGroupHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun AnkiDeckChip(
    label: String,
    fullName: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(colors.primary, CircleShape)
                } else {
                    Modifier
                        .background(colors.background, CircleShape)
                        .border(1.dp, colors.outlineVariant, CircleShape)
                }
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = fullName }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnkiNewDeckDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    ShinjikaiDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_anki_new_deck),
        iconRes = R.drawable.ic_anki,
        confirmLabel = stringResource(R.string.settings_anki_new_deck_confirm),
        onConfirm = { onConfirm(trimmed) },
        confirmEnabled = trimmed.isNotEmpty()
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_anki_new_deck_name)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.settings_anki_new_deck_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsPillButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    primary: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = ShinjikaiUi.PillShape,
        color = if (primary) colors.primary else Color.Transparent,
        contentColor = if (primary) colors.onPrimary else colors.onSurface,
        border = if (primary) null else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingsSubpageHeader(
    title: String,
    onGoBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onGoBack,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_back),
                    contentDescription = stringResource(R.string.nav_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private suspend fun loadAnkiDeckNamesOffMain(context: Context): List<String> =
    withContext(Dispatchers.IO) {
        runCatching { AnkiExporter.loadDeckNames(context) }.getOrDefault(emptyList())
    }

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val tint = if (isError) MaterialTheme.colorScheme.error else ShinjikaiUi.SuccessColor
    val background = tint.copy(alpha = 0.12f)
    val iconRes = if (isError) R.drawable.ic_settings_info else R.drawable.ic_settings_check

    Surface(
        shape = ShinjikaiUi.CompactShape,
        color = background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint
                )
                if (isError) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message))
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_copy_message_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_copy),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_copy_message_action),
                            color = tint,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
