package chat.simplex.common.views.remote

import SectionItemView
import SectionTextFooter
import SectionView
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.*
import androidx.compose.material.*
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.ui.theme.DEFAULT_PADDING_HALF
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.newchat.QRCode
import chat.simplex.common.views.usersettings.SettingsActionItem
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun ConnectMobileView(
  m: ChatModel
) {
  val connecting = rememberSaveable() { mutableStateOf(false) }
  val remoteHosts = remember { chatModel.remoteHosts }
  val deviceName = m.controller.appPrefs.deviceNameForRemoteAccess
  LaunchedEffect(Unit) {
    val hosts = m.controller.listRemoteHosts() ?: return@LaunchedEffect
    remoteHosts.clear()
    remoteHosts.addAll(hosts)
  }
  ConnectMobileLayout(
    deviceName = deviceName,
    remoteHosts = remoteHosts,
    connecting,
    connectedHost = remember { m.currentRemoteHost },
    updateDeviceName = {
      withBGApi {
        if (it != "") {
          m.controller.setLocalDeviceName(it)
          deviceName.set(it)
        }
      }
    },
    addMobileDevice = {
      ModalManager.start.showModalCloseable { close ->
        val invitation = rememberSaveable { mutableStateOf<String?>(null) }
        ConnectMobileViewLayout(stringResource(MR.strings.add_mobile_device), invitation.value)
        DisposableEffect(Unit) {
          val oldRemoteHostId = m.currentRemoteHost.value?.remoteHostId
          withBGApi {
            val r = chatModel.controller.startRemoteHost(null)
            if (r != null) {
              connecting.value = true
              invitation.value = r.second
            }
          }
          onDispose {
            if (m.currentRemoteHost.value?.remoteHostId == oldRemoteHostId) {
              withBGApi {
                chatController.stopRemoteHost(null)
              }
            }
          }
        }
      }
    },
    connectMobileDevice = { connectMobileDevice(it, connecting) }
  )
}

@Composable
fun ConnectMobileLayout(
  deviceName: SharedPreference<String?>,
  remoteHosts: List<RemoteHostInfo>,
  connecting: MutableState<Boolean>,
  connectedHost: MutableState<RemoteHostInfo?>,
  updateDeviceName: (String) -> Unit,
  addMobileDevice: () -> Unit,
  connectMobileDevice: (RemoteHostInfo) -> Unit,
) {
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    AppBarTitle(stringResource(MR.strings.add_mobile_device))
    SectionView(generalGetString(MR.strings.this_device_name)) {
      DeviceNameField(deviceName.state.value ?: "") { updateDeviceName(it) }
      for (host in remoteHosts) {
        SectionItemView({ connectMobileDevice(host) }, disabled = connecting.value) {
          Text(host.hostDeviceName)
        }
      }
      SettingsActionItem(painterResource(MR.images.ic_smartphone), stringResource(MR.strings.add_mobile_device), addMobileDevice, disabled = connecting.value, extraPadding = false)
    }
    SectionTextFooter(generalGetString(MR.strings.this_device_name_shared_with_mobile))
  }
}

@Composable
private fun DeviceNameField(
  initialValue: String,
  onChange: (String) -> Unit
) {
  // TODO get user-defined device name
  val state = remember { mutableStateOf(TextFieldValue(initialValue)) }
  val colors = TextFieldDefaults.textFieldColors(
    backgroundColor = Color.Unspecified,
    textColor = MaterialTheme.colors.onBackground,
    focusedIndicatorColor = Color.Unspecified,
    unfocusedIndicatorColor = Color.Unspecified,
  )
  val enabled = true
  val shape = MaterialTheme.shapes.small.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize)
  val interactionSource = remember { MutableInteractionSource() }
  BasicTextField(
    value = state.value,
    modifier = Modifier
      .padding(horizontal = DEFAULT_PADDING)
      .fillMaxWidth()
      .background(colors.backgroundColor(enabled).value, shape)
      .indicatorLine(enabled, false, interactionSource, colors)
      .defaultMinSize(
        minWidth = TextFieldDefaults.MinWidth,
        minHeight = TextFieldDefaults.MinHeight
      ),
    onValueChange = {
      state.value = it
      onChange(it.text)
    },
    cursorBrush = SolidColor(colors.cursorColor(false).value),
    singleLine = true,
    textStyle = TextStyle.Default.copy(
      color = MaterialTheme.colors.onBackground,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp
    ),
    interactionSource = interactionSource,
    decorationBox = @Composable { innerTextField ->
      TextFieldDefaults.TextFieldDecorationBox(
        value = state.value.text,
        innerTextField = innerTextField,
        placeholder = { Text(generalGetString(MR.strings.enter_this_device_name), color = MaterialTheme.colors.secondary) },
        singleLine = true,
        enabled = enabled,
        isError = false,
        interactionSource = interactionSource,
        contentPadding = TextFieldDefaults.textFieldWithLabelPadding(start = 0.dp, end = 0.dp),
        visualTransformation = VisualTransformation.None,
        colors = colors
      )
    }
  )
}

@Composable
private fun ConnectMobileViewLayout(
  title: String,
  invitation: String?,
) {
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    AppBarTitle(title)
    SectionView {
      if (invitation != null) {
        QRCode(
          invitation, Modifier
            .padding(horizontal = DEFAULT_PADDING, vertical = DEFAULT_PADDING_HALF)
            .aspectRatio(1f)
        )
      }
    }
  }
}

fun connectMobileDevice(rh: RemoteHostInfo, connecting: MutableState<Boolean>) {
    ModalManager.start.showModalCloseable { close ->
      val invitation = rememberSaveable { mutableStateOf<String?>(null) }
      ConnectMobileViewLayout(
        title = stringResource(MR.strings.connect_mobile_device),
        invitation = invitation.value,
      )
      val remoteHost = rememberSaveable(stateSaver = serializableSaver()) { mutableStateOf<RemoteHostInfo?>(null) }
      LaunchedEffect(Unit) {
        val r = chatModel.controller.startRemoteHost(rh.remoteHostId)
        if (r != null) {
          val (rh_, inv) = r
          connecting.value = true
          remoteHost.value = rh_
          invitation.value = inv
        }
      }
      DisposableEffect(remember { chatModel.currentRemoteHost }.value) {
        if (remoteHost.value != null && chatModel.currentRemoteHost.value?.remoteHostId == remoteHost.value?.remoteHostId) {
          close()
        }
        onDispose {
          if (remoteHost.value != null && chatModel.currentRemoteHost.value?.remoteHostId != remoteHost.value?.remoteHostId) {
            withBGApi {
              chatController.stopRemoteHost(remoteHost.value?.remoteHostId)
            }
          }
        }
      }
    }
}