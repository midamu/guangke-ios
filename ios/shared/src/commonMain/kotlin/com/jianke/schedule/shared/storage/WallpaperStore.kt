package com.jianke.schedule.shared.storage

import com.jianke.schedule.shared.platform.documentsDir
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * 自定义背景图的文件读写。
 *
 * 照片由 Swift 侧（PHPicker）写进 `Documents/wallpaper.jpg`，**显示**交给 Compose：
 * 读字节 → skiko 解码成 ImageBitmap → 画在内容下面。
 *
 * 为什么不让 UIKit 的 UIImageView 垫在 Compose 下面：Compose 的宿主视图在 iOS 上是
 * 不透明的，透明背景盖不住它，照片永远看不见（真机实测）。自己画就没有这个分层问题，
 * 而且桌面预览也能看到壁纸。
 */
object WallpaperStore {

    const val FILE_NAME = "wallpaper.jpg"

    private fun path() = (documentsDir() + "/" + FILE_NAME).toPath()

    fun exists(): Boolean = runCatching { FileSystem.SYSTEM.exists(path()) }.getOrDefault(false)

    fun readBytes(): ByteArray? = runCatching {
        FileSystem.SYSTEM.read(path()) { readByteArray() }
    }.getOrNull()

    fun delete() {
        runCatching { FileSystem.SYSTEM.delete(path(), mustExist = false) }
    }
}
