package com.shinjikai.dictionary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val DialogShape = RoundedCornerShape(32.dp)
private val DialogIconShape = RoundedCornerShape(20.dp)

/**
 * The app's one dialog style (bento card): icon chip, centered title and message, optional
 * custom content, then a full-width confirm button above an outlined cancel button.
 * [destructive] switches the icon and confirm button to the error colors and adds the
 * "can't be undone" line.
 */
@Composable
fun ShinjikaiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    @DrawableRes iconRes: Int,
    confirmLabel: String,
    onConfirm: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    @DrawableRes confirmIconRes: Int? = if (destructive) iconRes else null,
    dismissLabel: String = stringResource(R.string.action_cancel),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = DialogShape,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (destructive) colors.errorContainer else colors.primaryContainer,
                            DialogIconShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (destructive) colors.onErrorContainer else colors.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, lineHeight = 30.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                if (!message.isNullOrBlank() || destructive) {
                    Text(
                        text = listOfNotNull(
                            message?.takeIf { it.isNotBlank() },
                            if (destructive) stringResource(R.string.dialog_cannot_undo) else null
                        ).joinToString(" "),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                if (content != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ShinjikaiUi.PillShape,
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                    }
                ) {
                    if (confirmIconRes != null) {
                        Icon(
                            painter = painterResource(confirmIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ShinjikaiUi.PillShape,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface)
                ) {
                    Text(
                        text = dismissLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
