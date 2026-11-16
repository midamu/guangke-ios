import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    // 只在 macOS 上声明 framework：Windows/Linux 上跑 :shared:jvmTest 时，
    // 这个模块的 iOS target 本来就是 disabled，没必要再去配置产物
    val hostIsMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    targets.withType<KotlinNativeTarget>().configureEach {
        if (hostIsMac) {
            binaries.framework {
                baseName = "ComposeApp"
                // 静态框架：把 shared 一起打进去，Swift 侧只嵌一个
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

            // Miuix 官方支持 iOS（miuix-ui-iosarm64 已在 Maven Central）。
            // 第一版界面先用 Compose foundation 手写，避免主题/取色在 iOS 上的差异；
            // 想换回 Miuix 时把下面两行取消注释即可（API 与 Android 侧一致）。
            // implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
            // implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
        }
    }
}
