package com.jianke.schedule.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay

/** 浏览器 UA：学校网关对裸客户端会 502，必须伪装成手机浏览器。 */
const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/**
 * 平台相关的引擎：
 * - iOS → Darwin（NSURLSession）
 * - JVM  → CIO（只为在桌面跑单测，不进 App）
 *
 * 超时与 Cookie 在各自的 actual 里配置（两个引擎的差异只在这一处）。
 */
expect fun newHttpClient(): HttpClient

data class TextResponse(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/**
 * 薄薄一层 HTTP 包装。
 *
 * Cookie 和 302 都交给 Ktor：
 * - 装了 `HttpCookies` 后，各跳的 `Set-Cookie` 会自动保存并回传
 *   （CAS 换票后教务域的 JSESSIONID 就靠它）
 * - `followRedirects` 默认开启，`/sso/lyiotlogin?ticket=ST` 的 302 会被跟到底
 *
 * 这两点正是 Android 版里 `HttpLite` 手写两百行的原因——Ktor 直接内建。
 */
class Http(private val client: HttpClient) {

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): TextResponse =
        send(url, headers, body = null)

    suspend fun postForm(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): TextResponse = send(url, headers, body, ContentType.Application.FormUrlEncoded)

    suspend fun postJson(url: String, body: String): TextResponse =
        send(url, emptyMap(), body, ContentType.Application.Json)

    private suspend fun send(
        url: String,
        headers: Map<String, String>,
        body: String?,
        bodyType: ContentType? = null,
    ): TextResponse {
        // 学校域名在国内移动网络下偶发连不上（ids 还有 AAAA，v6 不通时会先超时）。
        // 重试一次 + 把异常翻译成人话，比把 Ktor 的原文抛给用户有用。
        var lastError: Throwable? = null
        repeat(MAX_TRIES) { attempt ->
            try {
                val response = if (body == null) {
                    client.get(url) { applyHeaders(headers) }
                } else {
                    client.post(url) {
                        applyHeaders(headers)
                        setBody(body)
                        bodyType?.let { contentType(it) }
                    }
                }
                return TextResponse(response.status.value, response.bodyAsText())
            } catch (t: Throwable) {
                lastError = t
                if (attempt < MAX_TRIES - 1) delay(1_200L)
            }
        }
        throw NetworkFailure(url, lastError ?: IllegalStateException("unknown"))
    }

    private fun HttpRequestBuilder.applyHeaders(headers: Map<String, String>) {
        header("User-Agent", USER_AGENT)
        header("Accept", "*/*")
        headers.forEach { (k, v) -> header(k, v) }
    }

    fun close() = client.close()
}

/**
 * 表单体：`a=1&b=2`。
 *
 * 只对 ASCII 做安全字符判断——**非 ASCII 字节必须百分号编码**（学号/密码可能是中文）。
 */
fun formBody(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (k, v) -> encode(k) + "=" + encode(v) }

/** 单独编码一个值（拼 URL 参数用）。 */
fun urlEncode(value: String): String = encode(value)

/** 每个请求最多试几次（含首次）。 */
private const val MAX_TRIES = 2

/**
 * 网络层失败，带上出错的具体 URL。
 *
 * Ktor 的原始异常是 `Request timeout has expired [url=..., request_timeout=...]`，
 * 直接抛给用户完全看不懂；这里翻译成「连不上哪台机器」。
 */
class NetworkFailure(val url: String, cause: Throwable) :
    Exception("连不上 ${hostOf(url)}（${describe(cause)}），请检查网络后重试", cause) {

    companion object {
        private fun hostOf(url: String): String =
            url.substringAfter("://").substringBefore('/').substringBefore('?')

        private fun describe(cause: Throwable): String {
            val name = cause::class.simpleName.orEmpty()
            val msg = cause.message.orEmpty()
            return when {
                name.contains("Timeout", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ->
                    "请求超时"
                msg.contains("App Transport Security", ignoreCase = true) ->
                    "明文 HTTP 被系统拦截"
                msg.contains("Could not connect", ignoreCase = true) ||
                    msg.contains("Connection", ignoreCase = true) ->
                    "连接被拒绝或中断"
                else -> "网络错误 ${cause::class.simpleName}"
            }
        }
    }
}

private fun encode(s: String): String {
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val safe = (c in 0x41..0x5A) || (c in 0x61..0x7A) || (c in 0x30..0x39) ||
            c == 0x2D || c == 0x5F || c == 0x2E || c == 0x7E
        if (safe) {
            sb.append(c.toChar())
        } else {
            sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
    return sb.toString()
}

private const val HEX = "0123456789ABCDEF"
