package com.jianke.schedule.shared.platform

import okio.Path
import okio.Path.Companion.toPath

/**
 * 课表数据的存放目录。
 * - iOS：`NSHomeDirectory()/Documents`（随 App 卸载一起删除，iCloud 备份默认包含）
 * - JVM：临时目录下，只为跑单测
 */
expect fun documentsDir(): String

fun defaultScheduleDir(): Path = documentsDir().toPath()
