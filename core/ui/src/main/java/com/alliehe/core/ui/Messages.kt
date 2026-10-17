package com.alliehe.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text

enum class MessageKind { Success, Failure, Info }

data class AtriumMessage(val kind: MessageKind, @param:StringRes val text: Int)

/**
 * Replaces Toasts with Wear M3 confirmation dialogs (round-safe, themed, auto-dismissing).
 * One host at the app root; screens call [show] via [LocalMessageHost].
 */
@Stable
class MessageHost {
    var current by mutableStateOf<AtriumMessage?>(null)
        private set

    fun show(kind: MessageKind, @StringRes text: Int) {
        current = AtriumMessage(kind, text)
    }

    fun dismiss() {
        current = null
    }
}

/** A no-op default keeps previews and isolated screens working without a root host. */
val LocalMessageHost = staticCompositionLocalOf { MessageHost() }

@Composable
fun MessageHostDialog(host: MessageHost) {
    val message = host.current
    // Keep the last text while the dialog animates out.
    val text = message?.let { stringResource(it.text) }.orEmpty()
    ConfirmationDialog(
        visible = message != null,
        onDismissRequest = host::dismiss,
        text = { Text(text, textAlign = TextAlign.Center) },
        colors = when (message?.kind) {
            MessageKind.Success -> ConfirmationDialogDefaults.successColors()
            MessageKind.Failure -> ConfirmationDialogDefaults.failureColors()
            else -> ConfirmationDialogDefaults.colors()
        },
    ) {
        when (message?.kind) {
            MessageKind.Success -> ConfirmationDialogDefaults.SuccessIcon()
            MessageKind.Failure -> ConfirmationDialogDefaults.GenericFailureIcon()
            else -> Icon(painterResource(R.drawable.ic_atrium_info), contentDescription = null)
        }
    }
}
