package com.jianke.schedule.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies

/**
 * iOS 引擎：Darwin（底层 NSURLSession）。
 * `HttpCookies` 负责跨跳保存 Set-Cookie，这是 CAS 换票后教务域会话能继续用的关键。
 */
actual fun newHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpCookies)
    install(HttpTimeout) {
        // 学校站点在移动网络下偏慢；25 秒容易在 CAS 跳转链上误判超时
        requestTimeoutMillis = 40_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 40_000
    }
    followRedirects = true
}
