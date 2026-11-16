package com.jianke.schedule.shared.platform

/**
 * 自定义背景的宿主。
 *
 * iOS 侧由 Swift 实现：`PHPickerViewController` 必须从真实的 UIViewController 弹出来，
 * Compose 拿不到那个引用，所以「选图 + 存盘 + 显示」全放在 UIKit 那侧，
 * Kotlin 只负责发起和读状态。
 *
 * 约定：图片存成 `Documents/wallpaper.jpg`（固定文件名），所以「是否有壁纸」
 * 就是「文件在不在」，不需要额外持久化状态。
 */
interface WallpaperHost {

    /** 选图。成功回传文件名（如 `wallpaper.jpg`），用户取消回传 null。 */
    fun pick(onDone: (String?) -> Unit)

    /** 恢复默认背景（删除文件 + 清空显示）。 */
    fun clear()

    /** 当前是否有自定义背景。 */
    fun isActive(): Boolean
}
