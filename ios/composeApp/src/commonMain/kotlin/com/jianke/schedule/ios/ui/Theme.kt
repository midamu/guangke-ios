package com.jianke.schedule.ios.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * 一套极简配色 + 字号。
 *
 * 为什么第一版不直接用 Miuix：Miuix 官方确实有 iOS 产物（`miuix-ui-iosarm64` 在
 * Maven Central 上），但它的 `ThemeController` 默认走 Monet 动态取色——那是 Android 的能力，
 * iOS 上要么取不到、要么走另一套降级路径。先把界面用 Compose foundation 写稳，
 * 之后再换 Miuix 只影响这一个文件。
 */
data class Palette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val subText: Color,
    val primary: Color,
    val divider: Color,
    val danger: Color,
    val highlight: Color,
)

private val LightPalette = Palette(
    background = Color(0xFFF6F6F8),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1A1A1C),
    subText = Color(0xFF7C7C84),
    primary = Color(0xFF3A7AFE),
    divider = Color(0x14000000),
    danger = Color(0xFFE53935),
    highlight = Color(0x1F3A7AFE),
)

private val DarkPalette = Palette(
    background = Color(0xFF121214),
    surface = Color(0xFF1C1C1F),
    text = Color(0xFFF2F2F4),
    subText = Color(0xFF9A9AA2),
    primary = Color(0xFF6C9CFF),
    divider = Color(0x1FFFFFFF),
    danger = Color(0xFFFF6B6B),
    highlight = Color(0x336C9CFF),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * 是否有自定义背景。有壁纸时根布局透明、卡片半透明，
 * 让 Swift 那边的 UIImageView 透出来。
 */
val LocalWallpaperActive = staticCompositionLocalOf { false }

object Type {
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val tiny = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)

    /** 课表左侧的时间字样：格子宽度有限，只能更小 */
    val micro = TextStyle(fontSize = 9.sp, lineHeight = 11.sp)
}

@Composable
fun GxkTheme(wallpaperActive: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalWallpaperActive provides wallpaperActive,
        content = content,
    )
}
