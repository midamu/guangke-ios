package com.jianke.schedule.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies

/**
 * JVM 引擎：只为在桌面跑单测（`./gradlew :shared:jvmTest`）。
 * 这个 target 不会进 iOS 包。
 */
actual fun newHttpClient(): HttpClient = HttpClient(CIO) {
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 25_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 25_000
    }
    followRedirects = true
}
