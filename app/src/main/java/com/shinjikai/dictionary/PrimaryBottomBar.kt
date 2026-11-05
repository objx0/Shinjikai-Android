package com.shinjikai.dictionary

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.ui.Screen

private data class PrimaryNavItem(
    val screen: Screen,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int,
    @StringRes val labelRes: Int,
    val onClick: () -> Unit
)

/**
 * Floating pill navigation (M3 Expressive floating-toolbar style).
 * The active tab expands into an icon + label pill; the rest stay icon-only.
 * Drawn as an overlay: screens keep [ShinjikaiUi.BottomBarClearance] as bottom content padding.
 */
@Composable
fun PrimaryBottomBar(
    currentScreen: Screen,
    onSearchClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        PrimaryNavItem(Screen.Browse, R.drawable.ic_nav_browse, R.drawable.ic_nav_browse_filled, R.string.nav_browse, onBrowseClick),
        PrimaryNavItem(Screen.History, R.drawable.ic_nav_history, R.drawable.ic_nav_history_filled, R.string.history_title, onHistoryClick),
        PrimaryNavItem(Screen.Search, R.drawable.ic_nav_search, R.drawable.ic_nav_search_filled, R.string.nav_search, onSearchClick),
        PrimaryNavItem(Screen.Bookmarks, R.drawable.ic_nav_bookmarks, R.drawable.ic_nav_bookmarks_filled, R.string.nav_bookmarks, onBookmarksClick),
        PrimaryNavItem(Screen.Settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_filled, R.string.nav_settings, onSettingsClick)
    )
    val selectedScreen = items.firstOrNull { it.screen == currentScreen }?.screen ?: Screen.Search

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .selectableGroup()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = item.screen == selectedScreen
                    FloatingNavItem(
                        selected = selected,
                        iconRes = if (selected) item.selectedIconRes else item.iconRes,
                        label = stringResource(item.labelRes),
                        onClick = item.onClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    selected: Boolean,
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else Color.Transparent,
        label = "floatingNavContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        label = "floatingNavContent"
    )

    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(horizontal = if (selected) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = if (selected) null else label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
