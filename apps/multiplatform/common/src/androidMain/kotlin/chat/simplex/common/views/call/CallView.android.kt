package chat.simplex.common.views.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.media.*
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import chat.simplex.common.model.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.model.ChatModel
import chat.simplex.common.model.Contact
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@SuppressLint("SourceLockedOrientationActivity")
@Composable
actual fun ActiveCallView() {
  val chatModel = ChatModel
  BackHandler(onBack = {
    val call = chatModel.activeCall.value
    if (call != null) withBGApi { chatModel.callManager.endCall(call) }
  })
  val audioViaBluetooth = rememberSaveable { mutableStateOf(false) }
  val ntfModeService = remember { chatModel.controller.appPrefs.notificationsMode.get() == NotificationsMode.SERVICE }
  LaunchedEffect(Unit) {
    // Start service when call happening since it's not already started.
    // It's needed to prevent Android from shutting down a microphone after a minute or so when screen is off
    if (!ntfModeService) platform.androidServiceStart()
  }
  DisposableEffect(Unit) {
    val am = androidAppContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var btDeviceCount = 0
    val audioCallback = object: AudioDeviceCallback() {
      override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
        Log.d(TAG, "Added audio devices: ${addedDevices.map { it.type }}")
        super.onAudioDevicesAdded(addedDevices)
        val addedCount = addedDevices.count { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        btDeviceCount += addedCount
        audioViaBluetooth.value = btDeviceCount > 0
        if (addedCount > 0 && chatModel.activeCall.value?.callState == CallState.Connected) {
          // Setting params in Connected state makes sure that Bluetooth will NOT be broken on Android < 12
          setCallSound(chatModel.activeCall.value?.soundSpeaker ?: return, audioViaBluetooth)
        }
      }
      override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
        Log.d(TAG, "Removed audio devices: ${removedDevices.map { it.type }}")
        super.onAudioDevicesRemoved(removedDevices)
        val removedCount = removedDevices.count { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        btDeviceCount -= removedCount
        audioViaBluetooth.value = btDeviceCount > 0
        if (btDeviceCount == 0 && chatModel.activeCall.value?.callState == CallState.Connected) {
          // Setting params in Connected state makes sure that Bluetooth will NOT be broken on Android < 12
          setCallSound(chatModel.activeCall.value?.soundSpeaker ?: return, audioViaBluetooth)
        }
      }
    }
    am.registerAudioDeviceCallback(audioCallback, null)
    val pm = (androidAppContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
    val proximityLock = if (pm.isWakeLockLevelSupported(PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      pm.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, androidAppContext.packageName + ":proximityLock")
    } else {
      null
    }
    proximityLock?.acquire()
    onDispose {
      // Stop it when call ended
      if (!ntfModeService) platform.androidServiceSafeStop()
      dropAudioManagerOverrides()
      am.unregisterAudioDeviceCallback(audioCallback)
      proximityLock?.release()
    }
  }
  val scope = rememberCoroutineScope()
  Box(Modifier.fillMaxSize()) {
    WebRTCView(chatModel.callCommand) { apiMsg ->
      Log.d(TAG, "received from WebRTCView: $apiMsg")
      val call = chatModel.activeCall.value
      if (call != null) {
        Log.d(TAG, "has active call $call")
        when (val r = apiMsg.resp) {
          is WCallResponse.Capabilities -> withBGApi {
            val callType = CallType(call.localMedia, r.capabilities)
            chatModel.controller.apiSendCallInvitation(call.contact, callType)
            chatModel.activeCall.value = call.copy(callState = CallState.InvitationSent, localCapabilities = r.capabilities)
          }
          is WCallResponse.Offer -> withBGApi {
            chatModel.controller.apiSendCallOffer(call.contact, r.offer, r.iceCandidates, call.localMedia, r.capabilities)
            chatModel.activeCall.value = call.copy(callState = CallState.OfferSent, localCapabilities = r.capabilities)
          }
          is WCallResponse.Answer -> withBGApi {
            chatModel.controller.apiSendCallAnswer(call.contact, r.answer, r.iceCandidates)
            chatModel.activeCall.value = call.copy(callState = CallState.Negotiated)
          }
          is WCallResponse.Ice -> withBGApi {
            chatModel.controller.apiSendCallExtraInfo(call.contact, r.iceCandidates)
          }
          is WCallResponse.Connection ->
            try {
              val callStatus = json.decodeFromString<WebRTCCallStatus>("\"${r.state.connectionState}\"")
              if (callStatus == WebRTCCallStatus.Connected) {
                chatModel.activeCall.value = call.copy(callState = CallState.Connected, connectedAt = Clock.System.now())
                setCallSound(call.soundSpeaker, audioViaBluetooth)
              }
              withBGApi { chatModel.controller.apiCallStatus(call.contact, callStatus) }
            } catch (e: Error) {
              Log.d(TAG,"call status ${r.state.connectionState} not used")
            }
          is WCallResponse.Connected -> {
            chatModel.activeCall.value = call.copy(callState = CallState.Connected, connectionInfo = r.connectionInfo)
            scope.launch {
              setCallSound(call.soundSpeaker, audioViaBluetooth)
            }
          }
          is WCallResponse.End -> {
            withBGApi { chatModel.callManager.endCall(call) }
          }
          is WCallResponse.Ended -> {
            chatModel.activeCall.value = call.copy(callState = CallState.Ended)
            withBGApi { chatModel.callManager.endCall(call) }
            chatModel.showCallView.value = false
          }
          is WCallResponse.Ok -> when (val cmd = apiMsg.command) {
            is WCallCommand.Answer ->
              chatModel.activeCall.value = call.copy(callState = CallState.Negotiated)
            is WCallCommand.Media -> {
              when (cmd.media) {
                CallMediaType.Video -> chatModel.activeCall.value = call.copy(videoEnabled = cmd.enable)
                CallMediaType.Audio -> chatModel.activeCall.value = call.copy(audioEnabled = cmd.enable)
              }
            }
            is WCallCommand.Camera -> {
              chatModel.activeCall.value = call.copy(localCamera = cmd.camera)
              if (!call.audioEnabled) {
                chatModel.callCommand.add(WCallCommand.Media(CallMediaType.Audio, enable = false))
              }
            }
            is WCallCommand.End ->
              chatModel.showCallView.value = false
            else -> {}
          }
          is WCallResponse.Error -> {
            Log.e(TAG, "ActiveCallView: command error ${r.message}")
          }
        }
      }
    }
    val call = chatModel.activeCall.value
    if (call != null)  ActiveCallOverlay(call, chatModel, audioViaBluetooth)
  }

  val context = LocalContext.current
  DisposableEffect(Unit) {
    val activity = context as? Activity ?: return@DisposableEffect onDispose {}
    val prevVolumeControlStream = activity.volumeControlStream
    activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL
    // Lock orientation to portrait in order to have good experience with calls
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    chatModel.activeCallViewIsVisible.value = true
    // After the first call, End command gets added to the list which prevents making another calls
    chatModel.callCommand.removeAll { it is WCallCommand.End }
    onDispose {
      activity.volumeControlStream = prevVolumeControlStream
      // Unlock orientation
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      chatModel.activeCallViewIsVisible.value = false
      chatModel.callCommand.clear()
    }
  }
}

@Composable
private fun ActiveCallOverlay(call: Call, chatModel: ChatModel, audioViaBluetooth: MutableState<Boolean>) {
  ActiveCallOverlayLayout(
    call = call,
    speakerCanBeEnabled = !audioViaBluetooth.value,
    dismiss = { withBGApi { chatModel.callManager.endCall(call) } },
    toggleAudio = { chatModel.callCommand.add(WCallCommand.Media(CallMediaType.Audio, enable = !call.audioEnabled)) },
    toggleVideo = { chatModel.callCommand.add(WCallCommand.Media(CallMediaType.Video, enable = !call.videoEnabled)) },
    toggleSound = {
      var call = chatModel.activeCall.value
      if (call != null) {
        call = call.copy(soundSpeaker = !call.soundSpeaker)
        chatModel.activeCall.value = call
        setCallSound(call.soundSpeaker, audioViaBluetooth)
      }
    },
    flipCamera = { chatModel.callCommand.add(WCallCommand.Camera(call.localCamera.flipped)) }
  )
}

private fun setCallSound(speaker: Boolean, audioViaBluetooth: MutableState<Boolean>) {
  val am = androidAppContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  Log.d(TAG, "setCallSound: set audio mode, speaker enabled: $speaker")
  am.mode = AudioManager.MODE_IN_COMMUNICATION
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val btDevice = am.availableCommunicationDevices.lastOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    val preferredSecondaryDevice = if (speaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    if (btDevice != null) {
      am.setCommunicationDevice(btDevice)
    } else if (am.communicationDevice?.type != preferredSecondaryDevice) {
      am.availableCommunicationDevices.firstOrNull { it.type == preferredSecondaryDevice }?.let {
        am.setCommunicationDevice(it)
      }
    }
  } else {
    if (audioViaBluetooth.value) {
      am.isSpeakerphoneOn = false
      am.startBluetoothSco()
    } else {
      am.stopBluetoothSco()
      am.isSpeakerphoneOn = speaker
    }
    am.isBluetoothScoOn = am.isBluetoothScoAvailableOffCall && audioViaBluetooth.value
  }
}

private fun dropAudioManagerOverrides() {
  val am = androidAppContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  am.mode = AudioManager.MODE_NORMAL
  // Clear selected communication device to default value after we changed it in call
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    am.clearCommunicationDevice()
  } else {
    am.isSpeakerphoneOn = false
    am.stopBluetoothSco()
  }
}

@Composable
private fun ActiveCallOverlayLayout(
  call: Call,
  speakerCanBeEnabled: Boolean,
  dismiss: () -> Unit,
  toggleAudio: () -> Unit,
  toggleVideo: () -> Unit,
  toggleSound: () -> Unit,
  flipCamera: () -> Unit
) {
  Column(Modifier.padding(DEFAULT_PADDING)) {
    when (call.peerMedia ?: call.localMedia) {
      CallMediaType.Video -> {
        CallInfoView(call, alignment = Alignment.Start)
        Box(Modifier.fillMaxWidth().fillMaxHeight().weight(1f), contentAlignment = Alignment.BottomCenter) {
          DisabledBackgroundCallsButton()
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          ToggleAudioButton(call, toggleAudio)
          Spacer(Modifier.size(40.dp))
          IconButton(onClick = dismiss) {
            Icon(painterResource(MR.images.ic_call_end_filled), stringResource(MR.strings.icon_descr_hang_up), tint = Color.Red, modifier = Modifier.size(64.dp))
          }
          if (call.videoEnabled) {
            ControlButton(call, painterResource(MR.images.ic_flip_camera_android_filled), MR.strings.icon_descr_flip_camera, flipCamera)
            ControlButton(call, painterResource(MR.images.ic_videocam_filled), MR.strings.icon_descr_video_off, toggleVideo)
          } else {
            Spacer(Modifier.size(48.dp))
            ControlButton(call, painterResource(MR.images.ic_videocam_off), MR.strings.icon_descr_video_on, toggleVideo)
          }
        }
      }
      CallMediaType.Audio -> {
        Spacer(Modifier.fillMaxHeight().weight(1f))
        Column(
          Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          ProfileImage(size = 192.dp, image = call.contact.profile.image)
          CallInfoView(call, alignment = Alignment.CenterHorizontally)
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight().weight(1f), contentAlignment = Alignment.BottomCenter) {
          DisabledBackgroundCallsButton()
        }
        Box(Modifier.fillMaxWidth().padding(bottom = DEFAULT_BOTTOM_PADDING), contentAlignment = Alignment.CenterStart) {
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = dismiss) {
              Icon(painterResource(MR.images.ic_call_end_filled), stringResource(MR.strings.icon_descr_hang_up), tint = Color.Red, modifier = Modifier.size(64.dp))
            }
          }
          Box(Modifier.padding(start = 32.dp)) {
            ToggleAudioButton(call, toggleAudio)
          }
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.padding(end = 32.dp)) {
              ToggleSoundButton(call, speakerCanBeEnabled, toggleSound)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ControlButton(call: Call, icon: Painter, iconText: StringResource, action: () -> Unit, enabled: Boolean = true) {
  if (call.hasMedia) {
    IconButton(onClick = action, enabled = enabled) {
      Icon(icon, stringResource(iconText), tint = if (enabled) Color(0xFFFFFFD8) else MaterialTheme.colors.secondary, modifier = Modifier.size(40.dp))
    }
  } else {
    Spacer(Modifier.size(40.dp))
  }
}

@Composable
private fun ToggleAudioButton(call: Call, toggleAudio: () -> Unit) {
  if (call.audioEnabled) {
    ControlButton(call, painterResource(MR.images.ic_mic), MR.strings.icon_descr_audio_off, toggleAudio)
  } else {
    ControlButton(call, painterResource(MR.images.ic_mic_off), MR.strings.icon_descr_audio_on, toggleAudio)
  }
}

@Composable
private fun ToggleSoundButton(call: Call, enabled: Boolean, toggleSound: () -> Unit) {
  if (call.soundSpeaker) {
    ControlButton(call, painterResource(MR.images.ic_volume_up), MR.strings.icon_descr_speaker_off, toggleSound, enabled)
  } else {
    ControlButton(call, painterResource(MR.images.ic_volume_down), MR.strings.icon_descr_speaker_on, toggleSound, enabled)
  }
}

@Composable
fun CallInfoView(call: Call, alignment: Alignment.Horizontal) {
  @Composable fun InfoText(text: String, style: TextStyle = MaterialTheme.typography.body2) =
    Text(text, color = Color(0xFFFFFFD8), style = style)
  Column(horizontalAlignment = alignment) {
    InfoText(call.contact.chatViewName, style = MaterialTheme.typography.h2)
    InfoText(call.callState.text)

    val connInfo = call.connectionInfo
    //    val connInfoText = if (connInfo == null) ""  else " (${connInfo.text}, ${connInfo.protocolText})"
    val connInfoText = if (connInfo == null) ""  else " (${connInfo.text})"
    InfoText(call.encryptionStatus + connInfoText)
  }
}

@Composable
private fun DisabledBackgroundCallsButton() {
  var show by remember { mutableStateOf(!platform.androidIsBackgroundCallAllowed()) }
  if (show) {
    Row(
      Modifier
        .padding(bottom = 24.dp)
        .clickable {
          withBGApi {
            show = !platform.androidAskToAllowBackgroundCalls()
          }
        }
        .background(WarningOrange.copy(0.3f), RoundedCornerShape(50))
        .padding(start = 14.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(stringResource(MR.strings.system_restricted_background_in_call_title), color = WarningOrange)
      Spacer(Modifier.width(8.dp))
      IconButton(onClick = { show = false }, Modifier.size(24.dp)) {
        Icon(painterResource(MR.images.ic_close), null, tint = WarningOrange)
      }
    }
  }
}

//@Composable
//fun CallViewDebug(close: () -> Unit) {
//  val callCommand = remember { mutableStateOf<WCallCommand?>(null)}
//  val commandText = remember { mutableStateOf("{\"command\": {\"type\": \"start\", \"media\": \"video\", \"aesKey\": \"FwW+t6UbnwHoapYOfN4mUBUuqR7UtvYWxW16iBqM29U=\"}}") }
//  val clipboard = ContextCompat.getSystemService(LocalContext.current, ClipboardManager::class.java)
//
//  BackHandler(onBack = close)
//  Column(
//    horizontalAlignment = Alignment.CenterHorizontally,
//    verticalArrangement = Arrangement.spacedBy(12.dp),
//    modifier = Modifier
//      .themedBackground()
//      .fillMaxSize()
//  ) {
//    WebRTCView(callCommand) { apiMsg ->
//      // for debugging
//      // commandText.value = apiMsg
//      commandText.value = json.encodeToString(apiMsg)
//    }
//
//    TextEditor(Modifier.height(180.dp), text = commandText)
//
//    Row(
//      Modifier
//        .fillMaxWidth()
//        .padding(bottom = 6.dp),
//      horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//      Button(onClick = {
//        val clip: ClipData = ClipData.newPlainText("js command", commandText.value)
//        clipboard?.setPrimaryClip(clip)
//      }) { Text("Copy") }
//      Button(onClick = {
//        try {
//          val apiCall: WVAPICall = json.decodeFromString(commandText.value)
//          commandText.value = ""
//          println("sending: ${commandText.value}")
//          callCommand.value = apiCall.command
//        } catch(e: Error) {
//          println("error parsing command: ${commandText.value}")
//          println(e)
//        }
//      }) { Text("Send") }
//      Button(onClick = {
//        commandText.value = ""
//      }) { Text("Clear") }
//    }
//  }
//}

@Composable
fun WebRTCView(callCommand: SnapshotStateList<WCallCommand>, onResponse: (WVAPIMessage) -> Unit) {
  val scope = rememberCoroutineScope()
  val webView = remember { mutableStateOf<WebView?>(null) }
  val permissionsState = rememberMultiplePermissionsState(
    permissions = listOf(
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO,
      Manifest.permission.MODIFY_AUDIO_SETTINGS,
      Manifest.permission.INTERNET
    )
  )
  fun processCommand(wv: WebView, cmd: WCallCommand) {
    val apiCall = WVAPICall(command = cmd)
    wv.evaluateJavascript("processCommand(${json.encodeToString(apiCall)})", null)
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
        permissionsState.launchMultiplePermissionRequest()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      val wv = webView.value
      if (wv != null) processCommand(wv, WCallCommand.End)
      lifecycleOwner.lifecycle.removeObserver(observer)
      webView.value?.destroy()
      webView.value = null
    }
  }
  val wv = webView.value
  if (wv != null) {
    LaunchedEffect(Unit) {
      snapshotFlow { callCommand.firstOrNull() }
        .distinctUntilChanged()
        .filterNotNull()
        .collect {
          while (callCommand.isNotEmpty()) {
            val cmd = callCommand.removeFirst()
            Log.d(TAG, "WebRTCView LaunchedEffect executing $cmd")
            processCommand(wv, cmd)
          }
        }
    }
  }
  val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/www/", WebViewAssetLoader.AssetsPathHandler(LocalContext.current))
    .build()

  if (permissionsState.allPermissionsGranted) {
    Box(Modifier.fillMaxSize()) {
      AndroidView(
        factory = { AndroidViewContext ->
          WebView(AndroidViewContext).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
            this.webChromeClient = object: WebChromeClient() {
              override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin.toString().startsWith("file:/")) {
                  request.grant(request.resources)
                } else {
                  Log.d(TAG, "Permission request from webview denied.")
                  request.deny()
                }
              }
            }
            this.webViewClient = LocalContentWebViewClient(webView, assetLoader)
            this.clearHistory()
            this.clearCache(true)
            this.addJavascriptInterface(WebRTCInterface(onResponse), "WebRTCInterface")
            val webViewSettings = this.settings
            webViewSettings.allowFileAccess = true
            webViewSettings.allowContentAccess = true
            webViewSettings.javaScriptEnabled = true
            webViewSettings.mediaPlaybackRequiresUserGesture = false
            webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
            this.loadUrl("file:android_asset/www/android/call.html")
          }
        }
      ) { /* WebView */ }
    }
  }
}

// for debugging
// class WebRTCInterface(private val onResponse: (String) -> Unit) {
class WebRTCInterface(private val onResponse: (WVAPIMessage) -> Unit) {
  @JavascriptInterface
  fun postMessage(message: String) {
    Log.d(TAG, "WebRTCInterface.postMessage")
    try {
      // for debugging
      // onResponse(message)
      onResponse(json.decodeFromString(message))
    } catch (e: Exception) {
      Log.e(TAG, "failed parsing WebView message: $message")
    }
  }
}

private class LocalContentWebViewClient(val webView: MutableState<WebView?>, private val assetLoader: WebViewAssetLoader) : WebViewClientCompat() {
  override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest
  ): WebResourceResponse? {
    return assetLoader.shouldInterceptRequest(request.url)
  }

  override fun onPageFinished(view: WebView, url: String) {
    super.onPageFinished(view, url)
    view.evaluateJavascript("sendMessageToNative = (msg) => WebRTCInterface.postMessage(JSON.stringify(msg))", null)
    webView.value = view
    Log.d(TAG, "WebRTCView: webview ready")
    // for debugging
    // view.evaluateJavascript("sendMessageToNative = ({resp}) => WebRTCInterface.postMessage(JSON.stringify({command: resp}))", null)
  }
}

@Preview
@Composable
fun PreviewActiveCallOverlayVideo() {
  SimpleXTheme {
    ActiveCallOverlayLayout(
      call = Call(
        contact = Contact.sampleData,
        callState = CallState.Negotiated,
        localMedia = CallMediaType.Video,
        peerMedia = CallMediaType.Video,
        connectionInfo = ConnectionInfo(
          RTCIceCandidate(RTCIceCandidateType.Host, "tcp", null),
          RTCIceCandidate(RTCIceCandidateType.Host, "tcp", null)
        )
      ),
      speakerCanBeEnabled = true,
      dismiss = {},
      toggleAudio = {},
      toggleVideo = {},
      toggleSound = {},
      flipCamera = {}
    )
  }
}

@Preview
@Composable
fun PreviewActiveCallOverlayAudio() {
  SimpleXTheme {
    ActiveCallOverlayLayout(
      call = Call(
        contact = Contact.sampleData,
        callState = CallState.Negotiated,
        localMedia = CallMediaType.Audio,
        peerMedia = CallMediaType.Audio,
        connectionInfo = ConnectionInfo(
          RTCIceCandidate(RTCIceCandidateType.Host, "udp", null),
          RTCIceCandidate(RTCIceCandidateType.Host, "udp", null)
        )
      ),
      speakerCanBeEnabled = true,
      dismiss = {},
      toggleAudio = {},
      toggleVideo = {},
      toggleSound = {},
      flipCamera = {}
    )
  }
}
