package chat.simplex.common.views.helpers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.StringResource

class AlertManager {
  var alertViews = mutableStateListOf<(@Composable () -> Unit)>()

  fun showAlert(alert: @Composable () -> Unit) {
    Log.d(TAG, "AlertManager.showAlert")
    alertViews.add(alert)
  }

  fun hideAlert() {
    alertViews.removeLastOrNull()
  }

  fun showAlertDialogButtons(
    title: String,
    text: String? = null,
    buttons: @Composable () -> Unit,
  ) {
    showAlert {
      AlertDialog(
        onDismissRequest = this::hideAlert,
        title = alertTitle(title),
        text = alertText(text),
        buttons = buttons,
        shape = RoundedCornerShape(corner = CornerSize(25.dp))
      )
    }
  }

  fun showAlertDialogButtonsColumn(
    title: String,
    text: AnnotatedString? = null,
    onDismissRequest: (() -> Unit)? = null,
    buttons: @Composable () -> Unit,
  ) {
    showAlert {
      AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke(); hideAlert() },
        title = {
          Text(
            title,
            Modifier.fillMaxWidth().padding(vertical = DEFAULT_PADDING),
            textAlign = TextAlign.Center,
            fontSize = 20.sp
          )
        },
        buttons = {
          Column(
            Modifier
              .padding(bottom = DEFAULT_PADDING)
          ) {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
              if (text != null) {
                Text(text, Modifier.fillMaxWidth().padding(start = DEFAULT_PADDING, end = DEFAULT_PADDING, bottom = DEFAULT_PADDING * 1.5f), fontSize = 16.sp, textAlign = TextAlign.Center, color = MaterialTheme.colors.secondary)
              }
              buttons()
            }
          }
        },
        shape = RoundedCornerShape(corner = CornerSize(25.dp))
      )
    }
  }

  fun showAlertDialog(
    title: String,
    text: String? = null,
    confirmText: String = generalGetString(MR.strings.ok),
    onConfirm: (() -> Unit)? = null,
    dismissText: String = generalGetString(MR.strings.cancel_verb),
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    destructive: Boolean = false
  ) {
    showAlert {
      AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke(); hideAlert() },
        title = alertTitle(title),
        text = alertText(text),
        buttons = {
          Row (
            Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING).padding(bottom = DEFAULT_PADDING_HALF),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
              focusRequester.requestFocus()
            }
            TextButton(onClick = {
              onDismiss?.invoke()
              hideAlert()
            }) { Text(dismissText) }
            TextButton(onClick = {
              onConfirm?.invoke()
              hideAlert()
            }, Modifier.focusRequester(focusRequester)) { Text(confirmText, color = if (destructive) MaterialTheme.colors.error else Color.Unspecified) }
          }
        },
        shape = RoundedCornerShape(corner = CornerSize(25.dp))
      )
    }
  }

  fun showAlertDialogStacked(
    title: String,
    text: String? = null,
    confirmText: String = generalGetString(MR.strings.ok),
    onConfirm: (() -> Unit)? = null,
    dismissText: String = generalGetString(MR.strings.cancel_verb),
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    destructive: Boolean = false
  ) {
    showAlert {
      AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke(); hideAlert() },
        title = alertTitle(title),
        text = alertText(text),
        buttons = {
          Column(
            Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING_HALF).padding(top = DEFAULT_PADDING, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            TextButton(onClick = {
              onDismiss?.invoke()
              hideAlert()
            }) { Text(dismissText) }
            TextButton(onClick = {
              onConfirm?.invoke()
              hideAlert()
            }) { Text(confirmText, color = if (destructive) Color.Red else Color.Unspecified, textAlign = TextAlign.End) }
          }
        },
        shape = RoundedCornerShape(corner = CornerSize(25.dp))
      )
    }
  }

  fun showAlertMsg(
    title: String, text: String? = null,
    confirmText: String = generalGetString(MR.strings.ok)
  ) {
    showAlert {
      AlertDialog(
        onDismissRequest = this::hideAlert,
        title = alertTitle(title),
        text = alertText(text),
        buttons = {
          val focusRequester = remember { FocusRequester() }
          LaunchedEffect(Unit) {
            focusRequester.requestFocus()
          }
          Row(
            Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING).padding(bottom = DEFAULT_PADDING_HALF),
            horizontalArrangement = Arrangement.Center
          ) {
            TextButton(
              onClick = {
                hideAlert()
              },
              Modifier.focusRequester(focusRequester)
            ) {
              Text(confirmText, color = Color.Unspecified)
            }
          }
        },
        shape = RoundedCornerShape(corner = CornerSize(25.dp))
      )
    }
  }
  fun showAlertMsg(
    title: StringResource,
    text: StringResource? = null,
    confirmText: StringResource = MR.strings.ok,
  ) = showAlertMsg(generalGetString(title), if (text != null) generalGetString(text) else null, generalGetString(confirmText))

  @Composable
  fun showInView() {
    remember { alertViews }.lastOrNull()?.invoke()
  }

  companion object {
    val shared = AlertManager()
  }
}

private fun alertTitle(title: String): (@Composable () -> Unit)? {
  return {
    Text(
      title,
      Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      fontSize = 20.sp
    )
  }
}

private fun alertText(text: String?): (@Composable () -> Unit)? {
  return if (text == null) {
    null
  } else {
    ({
      Text(
        escapedHtmlToAnnotatedString(text, LocalDensity.current),
        Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        color = MaterialTheme.colors.secondary
      )
    })
  }
}
