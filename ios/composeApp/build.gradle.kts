import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // 桌面目标：**只用于 UI 预览**，不进 iOS 包。
    // 同一份 commonMain 界面代码在 Windows 上直接跑，改一行 3 秒看到效果。
    jvm("desktop")

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
            // shared 的 HeadlessImport/Http 公开签名里用到了 Ktor 的 HttpClient，
            // 而 shared 把它声明成 implementation（不对外传递），所以这里必须显式依赖。
            implementation("io.ktor:ktor-client-core:3.6.1")

            // Miuix 官方支持 iOS（miuix-ui-iosarm64 已在 Maven Central）。
            // 第一版界面先用 Compose foundation 手写，避免主题/取色在 iOS 上的差异；
            // 想换回 Miuix 时把下面两行取消注释即可（API 与 Android 侧一致）。
            // implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
            // implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Desktop 上 Dispatchers.Main 需要 Swing 调度器
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.jianke.schedule.ios.DesktopPreviewKt"
        // 按 iPhone 竖屏比例开窗，方便看真实排布
        jvmArgs += listOf("-Dapple.awt.application.appearance=system")
    }
}
