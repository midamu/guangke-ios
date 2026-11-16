package com.jianke.schedule.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.net.HeadlessImport
import com.jianke.schedule.shared.net.Http
import com.jianke.schedule.shared.net.TtshituSolver
import com.jianke.schedule.shared.net.newHttpClient
import com.jianke.schedule.shared.platform.WallpaperHost
import com.jianke.schedule.shared.platform.todayIso
import com.jianke.schedule.shared.storage.CredentialVault
import com.jianke.schedule.shared.storage.Credentials
import com.jianke.schedule.shared.storage.ScheduleRepository
import com.jianke.schedule.shared.util.IsoDate
import kotlinx.coroutines.launch

private const val TAB_WEEK = 0
private const val TAB_TODAY = 1
private const val TAB_SETTINGS = 2

/**
 * iOS 版根界面。
 *
 * 与 Android 版的差别只有一处：**没有 WebView 导入**。更新走无头引擎
 * （登录 → 换票 → 拉 kbList），所以整屏不会因为「打开登录页」被顶掉。
 */
@Composable
fun App(vault: CredentialVault, wallpaper: WallpaperHost) {
    // 壁纸本身由 Swift 那侧的 UIImageView 画在最底下，Compose 只负责把背景让出来
    var wallpaperActive by remember { mutableStateOf(wallpaper.isActive()) }

    GxkTheme(wallpaperActive = wallpaperActive) {
        val palette = LocalPalette.current
        val credentials = remember { Credentials(vault) }
        val repo = remember { ScheduleRepository() }
        val http = remember { Http(newHttpClient()) }
        val scope = rememberCoroutineScope()

        var schedule by remember { mutableStateOf(runCatching { repo.load() }.getOrNull()) }
        var tab by remember { mutableIntStateOf(TAB_WEEK) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }

        fun update() {
            if (busy) return
            busy = true
            message = null
            scope.launch {
                val today = IsoDate.parse(todayIso()) ?: Triple(2026, 9, 1)
                val solver = credentials.solver()?.let { TtshituSolver(it.first, it.second, http) }
                val result = runCatching {
                    HeadlessImport(http, repo, credentials, solver)
                        .run(today.first, today.second, today.third)
                }.getOrElse { HeadlessImport.Result.Failed(it.message ?: "自动更新失败") }

                when (result) {
                    is HeadlessImport.Result.Ok -> {
                        schedule = result.schedule
                        message = "更新成功：${result.courseCount} 门课（${result.via}）"
                    }
                    // 失败不动旧课表：界面上还是原来那份
                    is HeadlessImport.Result.Fallback -> message = "没更新成功：${result.reason}"
                    is HeadlessImport.Result.Failed -> message = result.reason
                }
                busy = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (wallpaperActive) Color.Transparent else palette.background),
        ) {
            TopBar(busy = busy)
            Box(modifier = Modifier.weight(1f)) {
                val current = schedule
                when {
                    current == null && tab != TAB_SETTINGS -> EmptyState(onGoSettings = { tab = TAB_SETTINGS })
                    tab == TAB_WEEK && current != null -> WeekScreen(schedule = current)
                    tab == TAB_TODAY && current != null -> TodayScreen(schedule = current)
                    else -> SettingsScreen(
                        credentials = credentials,
                        busy = busy,
                        schedule = schedule,
                        wallpaper = wallpaper,
                        wallpaperActive = wallpaperActive,
                        onWallpaperActiveChanged = { wallpaperActive = it },
                        onUpdate = { update() },
                    )
                }
            }
            BottomBar(tab = tab, onSelect = { tab = it })
        }

        message?.let { text ->
            OverlayMessage(text = text, onDismiss = { message = null })
        }
    }
}

@Composable
private fun TopBar(busy: Boolean) {
    val palette = LocalPalette.current
    val surface = if (LocalWallpaperActive.current) palette.surface.copy(alpha = 0.9f) else palette.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(text = "广小课", style = Type.title)
        Text(
            text = if (busy) "正在更新课表…" else "天天开心",
            style = Type.caption,
            color = palette.subText,
        )
    }
    Divider()
}

@Composable
private fun EmptyState(onGoSettings: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "还没有课表", style = Type.title, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "在「设置」里填学号和密码，点一次更新就会把整学期课表拉到本机。",
            style = Type.body,
            color = palette.subText,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "去设置", onClick = onGoSettings)
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    val palette = LocalPalette.current
    val surface = if (LocalWallpaperActive.current) palette.surface.copy(alpha = 0.9f) else palette.surface
    Divider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TabItem("课表", tab == TAB_WEEK) { onSelect(TAB_WEEK) }
        TabItem("今日", tab == TAB_TODAY) { onSelect(TAB_TODAY) }
        TabItem("设置", tab == TAB_SETTINGS) { onSelect(TAB_SETTINGS) }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = Type.body,
            color = if (selected) palette.primary else palette.subText,
        )
    }
}

@Composable
private fun OverlayMessage(text: String, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(28.dp)) {
            Text(text = text, style = Type.body)
            Spacer(Modifier.height(14.dp))
            PrimaryButton(text = "知道了", onClick = onDismiss)
        }
    }
}
