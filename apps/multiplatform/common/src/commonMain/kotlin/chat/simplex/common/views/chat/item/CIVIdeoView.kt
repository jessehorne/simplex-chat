package chat.simplex.common.views.chat.item

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.unit.*
import chat.simplex.res.MR
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.URI

@Composable
fun CIVideoView(
  image: String,
  duration: Int,
  file: CIFile?,
  imageProvider: () -> ImageGalleryProvider,
  showMenu: MutableState<Boolean>,
  receiveFile: (Long, Boolean) -> Unit
) {
  Box(
    Modifier.layoutId(CHAT_IMAGE_LAYOUT_ID),
    contentAlignment = Alignment.TopEnd
  ) {
    val preview = remember(image) { base64ToBitmap(image) }
    val filePath = remember(file) { mutableStateOf(getLoadedFilePath(file)) }
    LaunchedEffect(Unit) {
      if (chatModel.connectedToRemote()) {
        snapshotFlow { file }
          .distinctUntilChanged()
          .filterNotNull()
          .filter { it.loaded && getLoadedFilePath(it) == null }
          .collect {
            it.loadRemoteFile()
            filePath.value = getLoadedFilePath(it)
          }
      }
    }
    val f = filePath.value
    if (file != null && f != null) {
      val uri = remember(filePath) { getAppFileUri(f.substringAfterLast(File.separator))  }
      val view = LocalMultiplatformView()
      VideoView(uri, file, preview, duration * 1000L, showMenu, onClick = {
        hideKeyboard(view)
        ModalManager.fullscreen.showCustomModal(animated = false) { close ->
          ImageFullScreenView(imageProvider, close)
        }
      })
    } else {
      Box {
        VideoPreviewImageView(preview, onClick = {
          if (file != null) {
            when (file.fileStatus) {
              CIFileStatus.RcvInvitation ->
                receiveFileIfValidSize(file, encrypted = false, receiveFile)
              CIFileStatus.RcvAccepted ->
                when (file.fileProtocol) {
                  FileProtocol.XFTP ->
                    AlertManager.shared.showAlertMsg(
                      generalGetString(MR.strings.waiting_for_video),
                      generalGetString(MR.strings.video_will_be_received_when_contact_completes_uploading)
                    )

                  FileProtocol.SMP ->
                    AlertManager.shared.showAlertMsg(
                      generalGetString(MR.strings.waiting_for_video),
                      generalGetString(MR.strings.video_will_be_received_when_contact_is_online)
                    )
                }
              CIFileStatus.RcvTransfer(rcvProgress = 7, rcvTotal = 10) -> {} // ?
              CIFileStatus.RcvComplete -> {} // ?
              CIFileStatus.RcvCancelled -> {} // TODO
              else -> {}
            }
          }
        },
          onLongClick = {
            showMenu.value = true
          })
        if (file != null) {
          DurationProgress(file, remember { mutableStateOf(false) }, remember { mutableStateOf(duration * 1000L) }, remember { mutableStateOf(0L) }/*, soundEnabled*/)
        }
        if (file?.fileStatus is CIFileStatus.RcvInvitation) {
          PlayButton(error = false, { showMenu.value = true }) { receiveFileIfValidSize(file, encrypted = false, receiveFile) }
        }
      }
    }
    loadingIndicator(file)
  }
}

@Composable
private fun VideoView(uri: URI, file: CIFile, defaultPreview: ImageBitmap, defaultDuration: Long, showMenu: MutableState<Boolean>, onClick: () -> Unit) {
  val player = remember(uri) { VideoPlayerHolder.getOrCreate(uri, false, defaultPreview, defaultDuration, true) }
  val videoPlaying = remember(uri.path) { player.videoPlaying }
  val progress = remember(uri.path) { player.progress }
  val duration = remember(uri.path) { player.duration }
  val preview by remember { player.preview }
  //  val soundEnabled by rememberSaveable(uri.path) { player.soundEnabled }
  val brokenVideo by rememberSaveable(uri.path) { player.brokenVideo }
  val play = {
    player.enableSound(true)
    player.play(true)
  }
  val stop = {
    player.enableSound(false)
    player.stop()
  }
  val showPreview = remember { derivedStateOf { !videoPlaying.value || progress.value == 0L } }
  DisposableEffect(Unit) {
    onDispose {
      stop()
    }
  }
  val onLongClick = { showMenu.value = true }
  Box {
    val windowWidth = LocalWindowWidth()
    val width = remember(preview) { if (preview.width * 0.97 <= preview.height) videoViewFullWidth(windowWidth) * 0.75f else DEFAULT_MAX_IMAGE_WIDTH }
    PlayerView(
      player,
      width,
      onClick = onClick,
      onLongClick = onLongClick,
      stop
    )
    if (showPreview.value) {
      VideoPreviewImageView(preview, onClick, onLongClick)
      PlayButton(brokenVideo, onLongClick = onLongClick, play)
    }
    DurationProgress(file, videoPlaying, duration, progress/*, soundEnabled*/)
  }
}

@Composable
expect fun PlayerView(player: VideoPlayer, width: Dp, onClick: () -> Unit, onLongClick: () -> Unit, stop: () -> Unit)

@Composable
private fun BoxScope.PlayButton(error: Boolean = false, onLongClick: () -> Unit, onClick: () -> Unit) {
  Surface(
    Modifier.align(Alignment.Center),
    color = Color.Black.copy(alpha = 0.25f),
    shape = RoundedCornerShape(percent = 50)
  ) {
    Box(
      Modifier
        .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .onRightClick { onLongClick.invoke() },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        painterResource(MR.images.ic_play_arrow_filled),
        contentDescription = null,
        tint = if (error) WarningOrange else Color.White
      )
    }
  }
}

@Composable
private fun DurationProgress(file: CIFile, playing: MutableState<Boolean>, duration: MutableState<Long>, progress: MutableState<Long>/*, soundEnabled: MutableState<Boolean>*/) {
  if (duration.value > 0L || progress.value > 0) {
    Row {
      Box(
        Modifier
          .padding(DEFAULT_PADDING_HALF)
          .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
          .padding(vertical = 2.dp, horizontal = 4.dp)
      ) {
        val time = if (progress.value > 0) progress.value else duration.value
        val timeStr = durationText((time / 1000).toInt())
        val width = if (timeStr.length <= 5) 44 else 50
        Text(
          timeStr,
          Modifier.widthIn(min = with(LocalDensity.current) { width.sp.toDp() }).padding(horizontal = 4.dp),
          fontSize = 13.sp,
          color = Color.White
        )
        /*if (!soundEnabled.value) {
        Icon(painterResource(MR.images.ic_volume_off_filled), null,
          Modifier.padding(start = 5.dp).size(10.dp),
          tint = Color.White
        )
      }*/
      }
      if (!playing.value) {
        Box(
          Modifier
            .padding(top = DEFAULT_PADDING_HALF)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
            .padding(vertical = 2.dp, horizontal = 4.dp)
        ) {
          Text(
            formatBytes(file.fileSize),
            Modifier.padding(horizontal = 4.dp),
            fontSize = 13.sp,
            color = Color.White
          )
        }
      }
    }
  }
}

@Composable
fun VideoPreviewImageView(preview: ImageBitmap, onClick: () -> Unit, onLongClick: () -> Unit) {
  val windowWidth = LocalWindowWidth()
  val width = remember(preview) { if (preview.width * 0.97 <= preview.height) videoViewFullWidth(windowWidth) * 0.75f else DEFAULT_MAX_IMAGE_WIDTH }
  Image(
    preview,
    contentDescription = stringResource(MR.strings.video_descr),
    modifier = Modifier
      .width(width)
      .combinedClickable(
        onLongClick = onLongClick,
        onClick = onClick
      )
      .onRightClick(onLongClick),
    contentScale = ContentScale.FillWidth,
  )
}

@Composable
expect fun LocalWindowWidth(): Dp

@Composable
private fun progressIndicator() {
  CircularProgressIndicator(
    Modifier.size(16.dp),
    color = Color.White,
    strokeWidth = 2.dp
  )
}

@Composable
private fun fileIcon(icon: Painter, stringId: StringResource) {
  Icon(
    icon,
    stringResource(stringId),
    Modifier.fillMaxSize(),
    tint = Color.White
  )
}

@Composable
private fun progressCircle(progress: Long, total: Long) {
  val angle = 360f * (progress.toDouble() / total.toDouble()).toFloat()
  val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
  val strokeColor = Color.White
  Surface(
    Modifier.drawRingModifier(angle, strokeColor, strokeWidth),
    color = Color.Transparent,
    shape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))
  ) {
    Box(Modifier.size(16.dp))
  }
}

@Composable
private fun loadingIndicator(file: CIFile?) {
  if (file != null) {
    Box(
      Modifier
        .padding(8.dp)
        .size(20.dp),
      contentAlignment = Alignment.Center
    ) {
      when (file.fileStatus) {
        is CIFileStatus.SndStored ->
          when (file.fileProtocol) {
            FileProtocol.XFTP -> progressIndicator()
            FileProtocol.SMP -> {}
          }
        is CIFileStatus.SndTransfer ->
          when (file.fileProtocol) {
            FileProtocol.XFTP -> progressCircle(file.fileStatus.sndProgress, file.fileStatus.sndTotal)
            FileProtocol.SMP -> progressIndicator()
          }
        is CIFileStatus.SndComplete -> fileIcon(painterResource(MR.images.ic_check_filled), MR.strings.icon_descr_video_snd_complete)
        is CIFileStatus.SndCancelled -> fileIcon(painterResource(MR.images.ic_close), MR.strings.icon_descr_file)
        is CIFileStatus.SndError -> fileIcon(painterResource(MR.images.ic_close), MR.strings.icon_descr_file)
        is CIFileStatus.RcvInvitation -> fileIcon(painterResource(MR.images.ic_arrow_downward), MR.strings.icon_descr_video_asked_to_receive)
        is CIFileStatus.RcvAccepted -> fileIcon(painterResource(MR.images.ic_more_horiz), MR.strings.icon_descr_waiting_for_video)
        is CIFileStatus.RcvTransfer ->
          if (file.fileProtocol == FileProtocol.XFTP && file.fileStatus.rcvProgress < file.fileStatus.rcvTotal) {
            progressCircle(file.fileStatus.rcvProgress, file.fileStatus.rcvTotal)
          } else {
            progressIndicator()
          }
        is CIFileStatus.RcvCancelled -> fileIcon(painterResource(MR.images.ic_close), MR.strings.icon_descr_file)
        is CIFileStatus.RcvError -> fileIcon(painterResource(MR.images.ic_close), MR.strings.icon_descr_file)
        is CIFileStatus.Invalid -> fileIcon(painterResource(MR.images.ic_question_mark), MR.strings.icon_descr_file)
        else -> {}
      }
    }
  }
}

private fun fileSizeValid(file: CIFile?): Boolean {
  if (file != null) {
    return file.fileSize <= getMaxFileSize(file.fileProtocol)
  }
  return false
}

private fun receiveFileIfValidSize(file: CIFile, encrypted: Boolean, receiveFile: (Long, Boolean) -> Unit) {
  if (fileSizeValid(file)) {
    receiveFile(file.fileId, encrypted)
  } else {
    AlertManager.shared.showAlertMsg(
      generalGetString(MR.strings.large_file),
      String.format(generalGetString(MR.strings.contact_sent_large_file), formatBytes(getMaxFileSize(file.fileProtocol)))
    )
  }
}

private fun videoViewFullWidth(windowWidth: Dp): Dp {
  val approximatePadding = 100.dp
  return minOf(DEFAULT_MAX_IMAGE_WIDTH, windowWidth - approximatePadding)
}
