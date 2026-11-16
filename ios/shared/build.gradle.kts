plugins {
    kotlin("multiplatform")
}

kotlin {
    // 只为跑单测：向量测试在 JVM 上跑最快，不用开模拟器
    jvm()

    // iOS 真机 + 模拟器（Apple Silicon）
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            // 跨平台文件读写（iOS / JVM 同一套 API，避开 Foundation 互操作）
            implementation("com.squareup.okio:okio:3.18.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        iosMain.dependencies {
            // NSURLSession 引擎
            implementation("io.ktor:ktor-client-darwin:3.6.1")
        }
        jvmMain.dependencies {
            // 只用于桌面单测，不会进 iOS 包
            implementation("io.ktor:ktor-client-cio:3.6.1")
        }
    }
}
