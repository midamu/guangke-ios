package com.jianke.schedule.ios

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jianke.schedule.ios.ui.App
import com.jianke.schedule.shared.model.Course
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.model.SemesterInfo
import com.jianke.schedule.shared.model.schoolPeriodTimes
import com.jianke.schedule.shared.platform.WallpaperHost
import com.jianke.schedule.shared.platform.nowMillis
import com.jianke.schedule.shared.platform.todayIso
import com.jianke.schedule.shared.storage.CredentialVault
import com.jianke.schedule.shared.storage.ScheduleRepository
import com.jianke.schedule.shared.util.IsoDate

/**
 * 桌面预览入口：**只为快速调 UI**，不参与 iOS 打包。
 *
 * 在 Windows 上：
 *     cd E:\Code\lixiaoguang\ios
 *     $env:JAVA_HOME='E:\Apps\jdk17'
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

/**
 * 首次运行塞一份演示课表：否则界面只有空状态，调不了格子布局。
 * 学期起点设成「本周一往前两周」，这样当前周正好是第 3 周。
 */
private fun seedDemoIfEmpty(repo: ScheduleRepository) {
    if (repo.load() != null) return

    val today = todayIso()
    val monday = IsoDate.mondayOf(today) ?: today
    val startDate = IsoDate.plusDays(monday, -14).orEmpty()

    fun c(name: String, room: String, day: Int, from: Int, to: Int, color: Long) = Course(
        name = name,
        teacher = "",
        room = room,
        dayOfWeek = day,
        startPeriod = from,
        endPeriod = to,
        weeks = (1..18).toList(),
        weeksText = "1-18周",
        color = color.toInt(),
    )

    val courses = listOf(
        c("高等数学", "A101", 1, 1, 2, 0xFF6F8AF5),
        c("大学英语", "B203", 1, 3, 4, 0xFF42D57F),
        c("数据结构", "C305", 2, 1, 2, 0xFFF6A354),
        c("线性代数", "A102", 2, 5, 6, 0xFFEE7BF6),
        c("体育", "操场", 3, 7, 8, 0xFF42D57F),
        c("计算机网络", "D401", 3, 1, 2, 0xFF0080FF),
        c("操作系统", "D402", 4, 3, 4, 0xFFE76C6C),
        c("编译原理", "D403", 4, 9, 10, 0xFF78B4C8),
        c("毛概", "E101", 5, 1, 2, 0xFFFAD354),
        c("算法设计", "C301", 5, 5, 6, 0xFF6F8AF5),
    )

    repo.save(
        Schedule(
            semester = SemesterInfo(label = "第 3 周", startDate = startDate, totalWeeks = 18),
            periodTimes = schoolPeriodTimes,
            courses = courses,
            updatedAt = nowMillis(),
        ),
    )
}

fun main() {
    val repo = ScheduleRepository()
    runCatching { seedDemoIfEmpty(repo) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "理小广 · 桌面预览",
            state = rememberWindowState(size = DpSize(390.dp, 844.dp)),
        ) {
            App(vault = DesktopVault(), wallpaper = DesktopWallpaper)
        }
    }
}
