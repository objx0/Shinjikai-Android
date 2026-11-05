package com.shinjikai.dictionary

import androidx.compose.foundation.layout.heightIn
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.data.AppSettings
import com.shinjikai.dictionary.data.AppThemeMode

// Maroon palette (original brand colours). Users can switch to wallpaper colours with
// "الألوان الديناميكية" in Settings. Every surface-container role is set so Material
// components never fall back to the default purple baseline.
private val ShinjikaiDarkColors = darkColorScheme(
    primary = Color(0xFFFF9E98),
    onPrimary = Color(0xFF470B08),
    primaryContainer = Color(0xFF5D2724),
    onPrimaryContainer = Color(0xFFFFDAD7),
    inversePrimary = Color(0xFF9B3C36),
    secondary = Color(0xFFD0BFBC),
    onSecondary = Color(0xFF342725),
    secondaryContainer = Color(0xFF302725),
    onSecondaryContainer = Color(0xFFF1E3E0),
    tertiary = Color(0xFFE2C36C),
    onTertiary = Color(0xFF3B2F00),
    tertiaryContainer = Color(0xFF534500),
    onTertiaryContainer = Color(0xFFFFE58F),
    background = Color(0xFF0E0D0D),
    onBackground = Color(0xFFF0EDEC),
    surface = Color(0xFF171515),
    onSurface = Color(0xFFF0EDEC),
    surfaceVariant = Color(0xFF242020),
    onSurfaceVariant = Color(0xFFCFC5C3),
    surfaceTint = Color(0xFFFF9E98),
    inverseSurface = Color(0xFFF0EDEC),
    inverseOnSurface = Color(0xFF211B1A),
    outline = Color(0xFF8F7F7D),
    outlineVariant = Color(0xFF3A3231),
    surfaceBright = Color(0xFF2F2A2A),
    surfaceDim = Color(0xFF0E0D0D),
    surfaceContainerLowest = Color(0xFF0A0909),
    surfaceContainerLow = Color(0xFF131111),
    surfaceContainer = Color(0xFF171515),
    surfaceContainerHigh = Color(0xFF1E1B1B),
    surfaceContainerHighest = Color(0xFF242020),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8F3934),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ShinjikaiLightColors = lightColorScheme(
    primary = Color(0xFF9B3C36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF3A0806),
    inversePrimary = Color(0xFFFF9E98),
    secondary = Color(0xFF715B58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E5E3),
    onSecondaryContainer = Color(0xFF2A1917),
    tertiary = Color(0xFF765B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE39D),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFFAF8F7),
    onBackground = Color(0xFF211B1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF211B1A),
    surfaceVariant = Color(0xFFF1ECEB),
    onSurfaceVariant = Color(0xFF5D504E),
    surfaceTint = Color(0xFF9B3C36),
    inverseSurface = Color(0xFF362F2E),
    inverseOnSurface = Color(0xFFFBEEEC),
    outline = Color(0xFF7A6866),
    outlineVariant = Color(0xFFE1D8D6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE3DCDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F7),
    surfaceContainer = Color(0xFFF6F2F1),
    surfaceContainerHigh = Color(0xFFF1ECEB),
    surfaceContainerHighest = Color(0xFFEBE5E4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun ShinjikaiTheme(
    settings: AppSettings,
    supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = settings.resolveDarkTheme()
    val baseScheme = if (darkTheme) ShinjikaiDarkColors else ShinjikaiLightColors
    val dynamicScheme = if (
        settings.useDynamicColor &&
        supportsDynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        createDynamicColorScheme(context, darkTheme)
    } else {
        null
    }
    val colorScheme = dynamicScheme ?: baseScheme
    val arabicFontFamily = remember { FontFamily(Font(R.font.noto_sans_arabic)) }
    val baseTypography = remember { Typography() }
    val appShapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    }
    val appTypography = remember(arabicFontFamily, baseTypography) {
        Typography(
            displayLarge = baseTypography.displayLarge.copy(fontFamily = arabicFontFamily),
            displayMedium = baseTypography.displayMedium.copy(fontFamily = arabicFontFamily),
            displaySmall = baseTypography.displaySmall.copy(fontFamily = arabicFontFamily),
            headlineLarge = baseTypography.headlineLarge.copy(fontFamily = arabicFontFamily),
            headlineMedium = baseTypography.headlineMedium.copy(fontFamily = arabicFontFamily),
            headlineSmall = baseTypography.headlineSmall.copy(fontFamily = arabicFontFamily),
            titleLarge = baseTypography.titleLarge.copy(fontFamily = arabicFontFamily),
            titleMedium = baseTypography.titleMedium.copy(fontFamily = arabicFontFamily),
            titleSmall = baseTypography.titleSmall.copy(fontFamily = arabicFontFamily),
            bodyLarge = baseTypography.bodyLarge.copy(fontFamily = arabicFontFamily),
            bodyMedium = baseTypography.bodyMedium.copy(fontFamily = arabicFontFamily),
            bodySmall = baseTypography.bodySmall.copy(fontFamily = arabicFontFamily),
            labelLarge = baseTypography.labelLarge.copy(fontFamily = arabicFontFamily),
            labelMedium = baseTypography.labelMedium.copy(fontFamily = arabicFontFamily),
            labelSmall = baseTypography.labelSmall.copy(fontFamily = arabicFontFamily)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography,
        shapes = appShapes,
        content = content
    )
}

@Composable
fun AppSettings.resolveDarkTheme(): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun createDynamicColorScheme(
    context: Context,
    darkTheme: Boolean
): androidx.compose.material3.ColorScheme {
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

object ShinjikaiUi {
    val CardShape = RoundedCornerShape(12.dp)
    val CompactShape = RoundedCornerShape(8.dp)
    val PillShape = RoundedCornerShape(999.dp)
    val SectionShape = RoundedCornerShape(28.dp)
    val ScreenPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp)
    val BottomBarClearance = 96.dp
    val SuccessColor = Color(0xFF1F8A55)

    @Composable
    fun cardColors(containerColor: Color = MaterialTheme.colorScheme.surface): CardColors {
        return CardDefaults.cardColors(containerColor = containerColor)
    }

    @Composable
    fun cardBorder(alpha: Float = 0.48f): BorderStroke {
        return BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha))
    }

    @Composable
    fun panelColor(alpha: Float = 0.32f): Color {
        return MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
    }

    @Composable
    fun chipColor(alpha: Float = 0.52f): Color {
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    }

    @Composable
    fun mutedTextColor(alpha: Float = 0.72f): Color {
        return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    }
}

@Composable
fun ShinjikaiPageHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                shape = ShinjikaiUi.CompactShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun ShinjikaiCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    colors: CardColors = ShinjikaiUi.cardColors(),
    border: BorderStroke = ShinjikaiUi.cardBorder(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = ShinjikaiUi.CardShape,
        colors = colors,
        border = border
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ShinjikaiChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    maxLines: Int = 1
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
    } else {
        ShinjikaiUi.chipColor()
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
        shape = ShinjikaiUi.PillShape,
        color = containerColor,
        border = ShinjikaiUi.cardBorder(alpha = if (selected) 0.16f else 0.18f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ShinjikaiPanel(
    modifier: Modifier = Modifier,
    color: Color = ShinjikaiUi.panelColor(),
    border: BorderStroke = ShinjikaiUi.cardBorder(alpha = 0.28f),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = ShinjikaiUi.CompactShape,
        color = color,
        border = border,
        content = content
    )
}

@Composable
fun ShinjikaiEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    message: String? = null,
    @DrawableRes iconRes: Int? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShinjikaiUi.SectionShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (iconRes != null || icon != null) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Bento-style tab header: big title + subtitle, then actions or a faint kanji mark. */
@Composable
fun ShinjikaiTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    kanji: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actions != null) {
            actions()
        } else if (kanji != null) {
            Text(
                text = kanji,
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        }
    }
}

/** 44dp round header action. */
@Composable
fun ShinjikaiHeaderAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (emphasized) colors.primary else colors.surface,
        contentColor = if (emphasized) colors.onPrimary else colors.onSurface,
        border = if (emphasized) null else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
            )
        }
    }
}

/** Small muted section label that sits outside cards. */
@Composable
fun ShinjikaiSectionCaption(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
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

/** Small pill action shown under a tab header (export, select, clear all). */
@Composable
fun ShinjikaiActionChip(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        destructive -> colors.error
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 40.dp),
        shape = ShinjikaiUi.PillShape,
        color = colors.surface,
        contentColor = contentColor,
        border = BorderStroke(
            1.dp,
            if (destructive && enabled) colors.error.copy(alpha = 0.4f) else colors.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
