// 根构建脚本：只声明插件版本，具体配置在各模块。
//
// 版本与 Android 侧对齐（Kotlin 2.4.21）；Compose Multiplatform 用 1.12.2，
// 正好是 Miuix 0.9.4 编译时使用的版本（以后要换 Miuix 时保持这两个匹配）。
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
}
