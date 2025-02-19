package chat.simplex.common.platform

import java.io.File
import java.util.*

private val home = System.getProperty("user.home")
private val unixConfigPath = (System.getenv("XDG_CONFIG_HOME") ?: "$home/.config") + "/simplex"
private val unixDataPath = (System.getenv("XDG_DATA_HOME") ?: "$home/.local/share") + "/simplex"
val desktopPlatform = detectDesktopPlatform()

enum class DesktopPlatform(val libPath: String, val libExtension: String, val configPath: String, val dataPath: String) {
  LINUX_X86_64("/libs/linux-x86_64", "so", unixConfigPath, unixDataPath),
  LINUX_AARCH64("/libs/aarch64", "so", unixConfigPath, unixDataPath),
  WINDOWS_X86_64("/libs/windows-x86_64", "dll", System.getenv("AppData") + File.separator + "SimpleX", System.getenv("AppData") + File.separator + "SimpleX"),
  MAC_X86_64("/libs/mac-x86_64", "dylib", unixConfigPath, unixDataPath),
  MAC_AARCH64("/libs/mac-aarch64", "dylib", unixConfigPath, unixDataPath);

  fun isLinux() = this == LINUX_X86_64 || this == LINUX_AARCH64
  fun isMac() = this == MAC_X86_64 || this == MAC_AARCH64
}

private fun detectDesktopPlatform(): DesktopPlatform {
  val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
  val arch = System.getProperty("os.arch")
  return when {
    os == "linux" && (arch.contains("x86") || arch == "amd64") -> DesktopPlatform.LINUX_X86_64
    os == "linux" && arch == "aarch64" -> DesktopPlatform.LINUX_AARCH64
    os.contains("windows") && (arch.contains("x86") || arch == "amd64") -> DesktopPlatform.WINDOWS_X86_64
    os.contains("mac") && arch.contains("x86") -> DesktopPlatform.MAC_X86_64
    os.contains("mac") && arch.contains("aarch64") -> DesktopPlatform.MAC_AARCH64
    else -> TODO("Currently, your processor's architecture ($arch) or os ($os) are unsupported. Please, contact us")
  }
}
