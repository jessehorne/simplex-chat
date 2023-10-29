package chat.simplex.common.views.newchat

import SectionBottomSpacer
import SectionTextFooter
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.model.SharedPreference
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.usersettings.IncognitoView
import chat.simplex.common.views.usersettings.SettingsActionItem
import chat.simplex.res.MR
import java.net.URI

@Composable
fun PasteToConnectView(chatModel: ChatModel, close: () -> Unit) {
  val connectionLink = remember { mutableStateOf("") }
  val clipboard = LocalClipboardManager.current
  PasteToConnectLayout(
    chatModel = chatModel,
    incognitoPref = chatModel.controller.appPrefs.incognito,
    connectionLink = connectionLink,
    pasteFromClipboard = {
      connectionLink.value = clipboard.getText()?.text ?: return@PasteToConnectLayout
    },
    close = close
  )
}

@Composable
fun PasteToConnectLayout(
  chatModel: ChatModel,
  incognitoPref: SharedPreference<Boolean>,
  connectionLink: MutableState<String>,
  pasteFromClipboard: () -> Unit,
  close: () -> Unit
) {
  val incognito = remember { mutableStateOf(incognitoPref.get()) }

  fun connectViaLink(connReqUri: String) {
    try {
      val uri = URI(connReqUri)
      withApi {
        planAndConnect(chatModel, uri, incognito = incognito.value, close)
      }
    } catch (e: RuntimeException) {
      AlertManager.shared.showAlertMsg(
        title = generalGetString(MR.strings.invalid_connection_link),
        text = generalGetString(MR.strings.this_string_is_not_a_connection_link)
      )
    }
  }

  Column(
    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = DEFAULT_PADDING),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    AppBarTitle(stringResource(MR.strings.connect_via_link), false)

    Box(Modifier.padding(top = DEFAULT_PADDING, bottom = 6.dp)) {
      TextEditor(
        connectionLink,
        Modifier.height(180.dp),
        contentPadding = PaddingValues(),
        placeholder = stringResource(MR.strings.paste_the_link_you_received_to_connect_with_your_contact)
      )
    }

    if (connectionLink.value == "") {
      SettingsActionItem(
        painterResource(MR.images.ic_content_paste),
        stringResource(MR.strings.paste_button),
        click = pasteFromClipboard,
      )
    } else {
      SettingsActionItem(
        painterResource(MR.images.ic_close),
        stringResource(MR.strings.clear_verb),
        click = { connectionLink.value = "" },
      )
    }

    SettingsActionItem(
      painterResource(MR.images.ic_link),
      stringResource(MR.strings.connect_button),
      click = { connectViaLink(connectionLink.value) },
      textColor = MaterialTheme.colors.primary,
      iconColor = MaterialTheme.colors.primary,
      disabled = connectionLink.value.isEmpty() || connectionLink.value.trim().contains(" ")
    )

    IncognitoToggle(incognitoPref, incognito) { ModalManager.start.showModal { IncognitoView() } }

    SectionTextFooter(
      buildAnnotatedString {
        append(sharedProfileInfo(chatModel, incognito.value))
        append("\n\n")
        append(annotatedStringResource(MR.strings.you_can_also_connect_by_clicking_the_link))
      }
    )

    SectionBottomSpacer()
  }
}


@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  name = "Dark Mode"
)*/
@Composable
fun PreviewPasteToConnectTextbox() {
  SimpleXTheme {
    PasteToConnectLayout(
      chatModel = ChatModel,
      incognitoPref = SharedPreference({ false }, {}),
      connectionLink = remember { mutableStateOf("") },
      pasteFromClipboard = {},
      close = {}
    )
  }
}
