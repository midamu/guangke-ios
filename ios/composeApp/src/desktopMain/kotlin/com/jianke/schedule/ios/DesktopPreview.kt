package com.jianke.schedule.ios

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jianke.schedule.ios.ui.App
import com.jianke.schedule.shared.platform.WallpaperHost
import com.jianke.schedule.shared.storage.CredentialVault

/**
 * 桌面预览入口：**只为快速调 UI**，不参与 iOS 打包。
 *
 * 在 Windows 上：
 *     cd E:\Code\guangxiaoke\ios
 *     gradlew.bat :composeApp:run
 *
 * 改 commonMain 里任何界面代码，重新运行一次约 3 秒即可看到效果，
 * 比走 GitHub Actions 的 20 分钟快两个数量级。
 *
 * 两个平台能力在桌面上用内存/空实现顶上：
 * - [DesktopVault]：账号存进程内存（关掉就丢，预览够用）
 * - [DesktopWallpaper]：不做壁纸，pick 直接回 null
 */

private class DesktopVault : CredentialVault {
    private val map = HashMap<String, String>()
    override fun read(key: String): String? = map[key]
    override fun save(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() = map.clear()
}

private object DesktopWallpaper : WallpaperHost {
    override fun pick(onDone: (String?) -> Unit) = onDone(null)
    override fun clear() = Unit
    override fun isActive(): Boolean = false
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "广小课 · 桌面预览",
        state = rememberWindowState(size = DpSize(390.dp, 844.dp)),
    ) {
        App(vault = DesktopVault(), wallpaper = DesktopWallpaper)
    }
}
