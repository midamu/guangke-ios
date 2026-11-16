package com.jianke.schedule.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.jianke.schedule.ios.ui.App
import com.jianke.schedule.shared.platform.WallpaperHost
import com.jianke.schedule.shared.storage.CredentialVault
import platform.UIKit.UIViewController

/**
 * iOS 入口。两个平台能力由 Swift 注入：
 * - [CredentialVault]：Keychain 存账号
 * - [WallpaperHost]：PHPicker 选背景（照片由 Swift 侧画在 Compose 下面）
 */
fun MainViewController(vault: CredentialVault, wallpaper: WallpaperHost): UIViewController =
    ComposeUIViewController {
        App(vault, wallpaper)
    }
