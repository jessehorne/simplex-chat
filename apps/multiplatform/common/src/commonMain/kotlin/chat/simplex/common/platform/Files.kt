package chat.simplex.common.platform

import androidx.compose.runtime.Composable
import chat.simplex.common.model.CIFile
import chat.simplex.common.model.CryptoFile
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import java.io.*
import java.net.URI

expect val dataDir: File
expect val tmpDir: File
expect val filesDir: File
expect val appFilesDir: File
expect val coreTmpDir: File
expect val dbAbsolutePrefixPath: String

expect val chatDatabaseFileName: String
expect val agentDatabaseFileName: String

/**
* This is used only for temporary storing db archive for export.
* Providing [tmpDir] instead crashes the app. Check db export before moving from this path to something else
* */
expect val databaseExportDir: File

expect fun desktopOpenDatabaseDir()

fun copyFileToFile(from: File, to: URI, finally: () -> Unit) {
  try {
    to.outputStream().use { stream ->
      BufferedOutputStream(stream).use { outputStream ->
        from.inputStream().use { it.copyTo(outputStream) }
      }
    }
    showToast(generalGetString(MR.strings.file_saved))
  } catch (e: Error) {
    showToast(generalGetString(MR.strings.error_saving_file))
    Log.e(TAG, "copyFileToFile error saving file $e")
  } finally {
    finally()
  }
}

fun copyBytesToFile(bytes: ByteArrayInputStream, to: URI, finally: () -> Unit) {
  try {
    to.outputStream().use { stream ->
      BufferedOutputStream(stream).use { outputStream ->
        bytes.use { it.copyTo(outputStream) }
      }
    }
    showToast(generalGetString(MR.strings.file_saved))
  } catch (e: Error) {
    showToast(generalGetString(MR.strings.error_saving_file))
    Log.e(TAG, "copyBytesToFile error saving file $e")
  } finally {
    finally()
  }
}

fun getAppFilePath(fileName: String): String {
  val rh = chatModel.currentRemoteHost.value
  val s = File.separator
  return if (rh == null) {
    appFilesDir.absolutePath + s + fileName
  } else {
    dataDir.absolutePath + s + "remote_hosts" + s + rh.storePath + s + "simplex_v1_files" + s + fileName
  }
}

fun getLoadedFilePath(file: CIFile?): String? {
  val f = file?.fileSource?.filePath
  return if (f != null && file.loaded) {
    val filePath = getAppFilePath(f)
    if (File(filePath).exists()) filePath else null
  } else {
    null
  }
}

fun getLoadedFileSource(file: CIFile?): CryptoFile? {
  val f = file?.fileSource?.filePath
  return if (f != null && file.loaded) {
    val filePath = getAppFilePath(f)
    if (File(filePath).exists()) file.fileSource else null
  } else {
    null
  }
}

/**
* [rememberedValue] is used in `remember(rememberedValue)`. So when the value changes, file saver will update a callback function
* */
@Composable
expect fun rememberFileChooserLauncher(getContent: Boolean, rememberedValue: Any? = null, onResult: (URI?) -> Unit): FileChooserLauncher

expect fun rememberFileChooserMultipleLauncher(onResult: (List<URI>) -> Unit): FileChooserMultipleLauncher

expect class FileChooserLauncher() {
  suspend fun launch(input: String)
}

expect class FileChooserMultipleLauncher() {
  suspend fun launch(input: String)
}

expect fun URI.inputStream(): InputStream?
expect fun URI.outputStream(): OutputStream
