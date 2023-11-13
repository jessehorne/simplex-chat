package chat.simplex.common.platform

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import chat.simplex.common.model.*
import chat.simplex.common.views.helpers.getAppFileUri
import chat.simplex.common.views.helpers.withApi
import java.io.File
import java.net.URI
import java.net.URLEncoder
import chat.simplex.res.MR

actual fun UriHandler.sendEmail(subject: String, body: CharSequence) {
  val subjectEncoded = URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
  val bodyEncoded = URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
  openUri("mailto:?subject=$subjectEncoded&body=$bodyEncoded")
}

actual fun ClipboardManager.shareText(text: String) {
  setText(AnnotatedString(text))
  showToast(MR.strings.copied.localized())
}

actual fun shareFile(text: String, fileSource: CryptoFile) {
  withApi {
    FileChooserLauncher(false) { to: URI? ->
      if (to != null) {
        if (fileSource.cryptoArgs != null) {
          try {
            decryptCryptoFile(getAppFilePath(fileSource.filePath), fileSource.cryptoArgs, to.path)
          } catch (e: Exception) {
            Log.e(TAG, "Unable to decrypt crypto file: " + e.stackTraceToString())
          }
        } else {
          copyFileToFile(File(fileSource.filePath), to) {}
        }
      }
    }.launch(fileSource.filePath)
  }
}
