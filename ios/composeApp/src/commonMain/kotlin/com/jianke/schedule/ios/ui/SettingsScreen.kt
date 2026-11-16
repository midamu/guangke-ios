package com.jianke.schedule.ios.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jianke.schedule.shared.model.Schedule
import com.jianke.schedule.shared.platform.WallpaperHost
import com.jianke.schedule.shared.platform.nowMillis
import com.jianke.schedule.shared.storage.Credentials

/** 设置页：账号、打码平台、外观、更新入口。 */
@Composable
fun SettingsScreen(
    credentials: Credentials,
    busy: Boolean,
    schedule: Schedule?,
    wallpaper: WallpaperHost,
    wallpaperActive: Boolean,
    onWallpaperActiveChanged: (Boolean) -> Unit,
    onUpdate: () -> Unit,
) {
    val palette = LocalPalette.current
    val saved = remember { credentials.account() }
    val savedSolver = remember { credentials.solver() }

    var username by remember { mutableStateOf(saved?.first.orEmpty()) }
    var password by remember { mutableStateOf(saved?.second.orEmpty()) }
    var solverUser by remember { mutableStateOf(savedSolver?.first.orEmpty()) }
    var solverPass by remember { mutableStateOf(savedSolver?.second.orEmpty()) }
    var hint by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionTitle("学校账号")
        Card {
            Text(
                text = "只存在本机 Keychain 里，用于直接连学校接口拉课表。",
                style = Type.caption,
                color = palette.subText,
            )
            Spacer(Modifier.height(12.dp))
            Field(label = "学号", value = username, placeholder = "学号") { username = it }
            Spacer(Modifier.height(10.dp))
            Field(label = "统一身份认证密码", value = password, secret = true, placeholder = "密码") { password = it }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "保存账号",
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        hint = "学号和密码不能为空"
                    } else {
                        credentials.saveAccount(username, password)
                        hint = "已保存到 Keychain"
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = "清除已保存的账号",
                danger = true,
                onClick = {
                    credentials.clearAll()
                    username = ""
                    password = ""
                    solverUser = ""
                    solverPass = ""
                    hint = "已清除"
                },
            )
        }

        SectionTitle("打码平台（图鉴，可选）")
        Card {
            Text(
                text = "不填也能用：拿到 TGT 之后靠它续期，不需要验证码；只有 TGT 过期时才需要打码。",
                style = Type.caption,
                color = palette.subText,
            )
            Spacer(Modifier.height(12.dp))
            Field(label = "图鉴用户名", value = solverUser, placeholder = "用户名") { solverUser = it }
            Spacer(Modifier.height(10.dp))
            Field(label = "图鉴密码", value = solverPass, secret = true, placeholder = "密码") { solverPass = it }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "保存打码账号",
                onClick = {
                    if (solverUser.isBlank() || solverPass.isBlank()) {
                        hint = "图鉴账号要填全"
                    } else {
                        credentials.saveSolver(solverUser, solverPass)
                        hint = "已保存"
                    }
                },
            )
        }

        SectionTitle("外观")
        Card {
            Text(
                text = "选一张照片铺在最底下；开启后卡片会半透明，能透出一点背景。",
                style = Type.caption,
                color = palette.subText,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (wallpaperActive) "更换背景" else "选择背景",
                onClick = {
                    // 真正的选图在 Swift 侧：PHPicker 需要一个真实的 UIViewController
                    wallpaper.pick { name ->
                        if (name != null) onWallpaperActiveChanged(true)
                    }
                },
            )
            if (wallpaperActive) {
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "恢复默认背景",
                    danger = true,
                    onClick = {
                        wallpaper.clear()
                        onWallpaperActiveChanged(false)
                    },
                )
            }
        }

        SectionTitle("更新")
        Card {
            InfoRow(title = "上次更新", value = lastUpdateText(schedule))
            InfoRow(title = "课程数", value = schedule?.courses?.size?.toString() ?: "—")
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (busy) "正在更新…" else "立即更新课表",
                enabled = !busy,
                onClick = onUpdate,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "直连学校接口，几秒完成；需要验证码时自动识别。拉不到就保留现在这份，不会变成空白。",
                style = Type.tiny,
                color = palette.subText,
            )
        }

        if (hint.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(text = hint, style = Type.caption, color = palette.primary)
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun lastUpdateText(schedule: Schedule?): String {
    val updatedAt = schedule?.updatedAt ?: 0L
    if (updatedAt <= 0L) return "从未更新"
    val days = ((nowMillis() - updatedAt) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今天"
        days == 1 -> "昨天"
        else -> "$days 天前"
    }
}
