package com.jianke.schedule.shared.platform

/** 只给桌面单测用：每次跑测试都在临时目录里，不碰真实数据。 */
actual fun documentsDir(): String =
    System.getProperty("java.io.tmpdir").orEmpty() + "/lixiaoguang-test"
