package com.jianke.schedule.shared.model

/**
 * 课程配色。手机版页面会给每门课配颜色，但**桌面版接口（kbList）不带颜色**，
 * 所以按课程名从调色板里稳定取一个——同名课永远同色。
 *
 * 这里手写了 Java 风格的字符串哈希，而不是用 `String.hashCode()`：
 * Kotlin/Native 与 JVM 的哈希实现虽然一致，但显式写死才能保证**两端颜色绝对相同**。
 */
object CourseColors {

    private val PALETTE = listOf(
        0xFFE76C6C, // 红
        0xFFF6A354, // 橙
        0xFF6F8AF5, // 蓝紫
        0xFFEE7BF6, // 品红
        0xFFEEB60D, // 金
        0xFF42D57F, // 绿
        0xFF0080FF, // 蓝
        0xFF86DCF4, // 天蓝
        0xFFFAD354, // 黄
        0xFFA1DEE0, // 青
        0xFFFDB78E, // 杏
        0xFFFE98A7, // 粉
        0xFF78B4C8, // 灰蓝
        0xFFC8A078, // 棕
    ).map { it.toInt() }

    fun of(name: String): Int {
        if (name.isBlank()) return 0
        var h = 0
        for (ch in name) h = 31 * h + ch.code
        // 手写 floorMod：h 可能是负数，直接取模会得到负索引
        var index = h % PALETTE.size
        if (index < 0) index += PALETTE.size
        return PALETTE[index]
    }
}
