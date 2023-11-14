package chat.simplex.common.views.helpers

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@Composable
fun ModalView(
  close: () -> Unit,
  showClose: Boolean = true,
  background: Color = MaterialTheme.colors.background,
  modifier: Modifier = Modifier,
  endButtons: @Composable RowScope.() -> Unit = {},
  content: @Composable () -> Unit,
) {
  if (showClose) {
    BackHandler(onBack = close)
  }
  Surface(Modifier.fillMaxSize()) {
    Column(if (background != MaterialTheme.colors.background) Modifier.background(background) else Modifier.themedBackground()) {
      CloseSheetBar(close, showClose, endButtons)
      Box(modifier) { content() }
    }
  }
}

enum class ModalPlacement {
  START, CENTER, END, FULLSCREEN
}

class ModalManager(private val placement: ModalPlacement? = null) {
  private val modalViews = arrayListOf<Pair<Boolean, (@Composable (close: () -> Unit) -> Unit)>>()
  private val modalCount = mutableStateOf(0)
  private val toRemove = mutableSetOf<Int>()
  private var oldViewChanging = AtomicBoolean(false)
  private var passcodeView: MutableState<(@Composable (close: () -> Unit) -> Unit)?> = mutableStateOf(null)

  fun showModal(settings: Boolean = false, showClose: Boolean = true, endButtons: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    showCustomModal { close ->
      ModalView(close, showClose = showClose, endButtons = endButtons, content = content)
    }
  }

  fun showModalCloseable(settings: Boolean = false, showClose: Boolean = true, content: @Composable (close: () -> Unit) -> Unit) {
    showCustomModal { close ->
      ModalView(close, showClose = showClose, content = { content(close) })
    }
  }

  fun showCustomModal(animated: Boolean = true, modal: @Composable (close: () -> Unit) -> Unit) {
    Log.d(TAG, "ModalManager.showCustomModal")
    // Means, animation is in progress or not started yet. Do not wait until animation finishes, just remove all from screen.
    // This is useful when invoking close() and ShowCustomModal one after another without delay. Otherwise, screen will hold prev view
    if (toRemove.isNotEmpty()) {
      runAtomically { toRemove.removeIf { elem -> modalViews.removeAt(elem); true } }
    }
    // Make animated appearance only on Android (everytime) and on Desktop (when it's on the start part of the screen or modals > 0)
    // to prevent unneeded animation on different situations
    val anim = if (appPlatform.isAndroid) animated else animated && (modalCount.value > 0 || placement == ModalPlacement.START)
    modalViews.add(anim to modal)
    modalCount.value = modalViews.size - toRemove.size

    if (placement == ModalPlacement.CENTER) {
      ChatModel.chatId.value = null
    } else if (placement == ModalPlacement.END) {
      desktopExpandWindowToWidth(DEFAULT_START_MODAL_WIDTH + DEFAULT_MIN_CENTER_MODAL_WIDTH + DEFAULT_END_MODAL_WIDTH)
    }
  }

  fun showPasscodeCustomModal(modal: @Composable (close: () -> Unit) -> Unit) {
    Log.d(TAG, "ModalManager.showPasscodeCustomModal")
    passcodeView.value = modal
  }

  fun hasModalsOpen() = modalCount.value > 0

  fun closeModal() {
    if (modalViews.isNotEmpty()) {
      if (modalViews.lastOrNull()?.first == false) modalViews.removeAt(modalViews.lastIndex)
      else runAtomically { toRemove.add(modalViews.lastIndex - min(toRemove.size, modalViews.lastIndex)) }
    }
    modalCount.value = modalViews.size - toRemove.size
  }

  fun closeModals() {
    modalViews.clear()
    toRemove.clear()
    modalCount.value = 0
  }

  fun closeModalsExceptFirst() {
    while (modalCount.value > 1) {
      closeModal()
    }
  }

  @OptIn(ExperimentalAnimationApi::class)
  @Composable
  fun showInView() {
    // Without animation
    if (modalCount.value > 0 && modalViews.lastOrNull()?.first == false) {
      modalViews.lastOrNull()?.second?.invoke(::closeModal)
      return
    }
    AnimatedContent(targetState = modalCount.value,
      transitionSpec = {
        if (targetState > initialState) {
          fromEndToStartTransition()
        } else {
          fromStartToEndTransition()
        }.using(SizeTransform(clip = false))
      }
    ) {
      modalViews.getOrNull(it - 1)?.second?.invoke(::closeModal)
      // This is needed because if we delete from modalViews immediately on request, animation will be bad
      if (toRemove.isNotEmpty() && it == modalCount.value && transition.currentState == EnterExitState.Visible && !transition.isRunning) {
        runAtomically { toRemove.removeIf { elem -> modalViews.removeAt(elem); true } }
      }
    }
  }

  @Composable
  fun showPasscodeInView() {
    remember { passcodeView }.value?.invoke { passcodeView.value = null }
  }

  /**
  * Allows to modify a list without getting [ConcurrentModificationException]
  * */
  private fun runAtomically(atomicBoolean: AtomicBoolean = oldViewChanging, block: () -> Unit) {
    while (!atomicBoolean.compareAndSet(false, true)) {
      Thread.sleep(10)
    }
    block()
    atomicBoolean.set(false)
  }

  @OptIn(ExperimentalAnimationApi::class)
  private fun fromStartToEndTransition() =
    slideInHorizontally(
      initialOffsetX = { fullWidth -> -fullWidth },
      animationSpec = animationSpec()
    ) with slideOutHorizontally(
      targetOffsetX = { fullWidth -> fullWidth },
      animationSpec = animationSpec()
    )

  @OptIn(ExperimentalAnimationApi::class)
  private fun fromEndToStartTransition() =
    slideInHorizontally(
      initialOffsetX = { fullWidth -> fullWidth },
      animationSpec = animationSpec()
    ) with slideOutHorizontally(
      targetOffsetX = { fullWidth -> -fullWidth },
      animationSpec = animationSpec()
    )

private fun <T> animationSpec() = tween<T>(durationMillis = 250, easing = FastOutSlowInEasing)
//  private fun <T> animationSpecFromStart() = tween<T>(durationMillis = 150, easing = FastOutLinearInEasing)
//  private fun <T> animationSpecFromEnd() = tween<T>(durationMillis = 100, easing = FastOutSlowInEasing)

  companion object {
    private val shared = ModalManager()
    val start = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.START)
    val center = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.CENTER)
    val end = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.END)
    val fullscreen = if (appPlatform.isAndroid) shared else ModalManager(ModalPlacement.FULLSCREEN)

    fun closeAllModalsEverywhere() {
      start.closeModals()
      center.closeModals()
      end.closeModals()
      fullscreen.closeModals()
    }
  }
}
