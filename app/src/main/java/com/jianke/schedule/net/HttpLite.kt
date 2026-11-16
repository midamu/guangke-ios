package com.jianke.schedule.net

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 极简 HTTP 客户端：不引第三方依赖（项目原本连 OkHttp 都没有）。
 *
 * 需要自己管两件事，所以没用 HttpURLConnection 的默认行为：
 * 1. **Cookie**：CAS 登录域无 Cookie，但教务域靠 `Set-Cookie: JSESSIONID` 维持会话；
 *    HttpURLConnection 不保存 Cookie，必须每跳自己收、自己带。
 * 2. **重定向**：`/sso/lyiotlogin?ticket=ST` 是 302，票在 Location 与 Set-Cookie 里，
 *    必须逐跳处理，不能让它自动跟完（否则拿不到中间那跳的 Cookie）。
 */
class HttpLite(private val userAgent: String = UA) {

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val MAX_REDIRECT = 6
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 20_000
    }

    data class Response(val code: Int, val body: String, val url: String) {
        val ok: Boolean get() = code in 200..299
    }

    // host -> (name -> value)
    private val cookies = HashMap<String, MutableMap<String, String>>()

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response =
        request("GET", url, headers, null)

    fun postForm(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): Response = request(
        "POST",
        url,
        headers + ("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8"),
        encodeForm(params),
    )

    fun postJson(url: String, json: String): Response = request(
        "POST",
        url,
        mapOf("Content-Type" to "application/json;charset=UTF-8"),
        json,
    )

    /** 清掉所有 Cookie（换账号 / 会话彻底失效时用）。 */
    fun clearCookies() = synchronized(cookies) { cookies.clear() }

    private fun request(
        method: String,
        rawUrl: String,
        headers: Map<String, String>,
        body: String?,
    ): Response {
        var url = rawUrl
        var currentMethod = method
        var currentBody = body
        var hops = 0
        while (true) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = currentMethod
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "*/*")
                cookieHeader(url)?.let { setRequestProperty("Cookie", it) }
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (currentBody != null) {
                    doOutput = true
                }
            }
            try {
                if (currentBody != null) {
                    conn.outputStream.use { it.write(currentBody.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                // 每一跳都要收 Cookie：302 上的 JSESSIONID 才是后面请求要用的
                storeCookies(url, conn.headerFields["Set-Cookie"].orEmpty())

                if (code in 300..399 && hops < MAX_REDIRECT) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        hops++
                        url = resolve(url, location)
                        // 303 一律转 GET；301/302 在浏览器里对 POST 也多半转 GET，
                        // 这里保持与 WebView 一致，307/308 才保留方法与 body
                        if (code == 307 || code == 308) {
                            // keep method/body
                        } else {
                            currentMethod = "GET"
                            currentBody = null
                        }
                        conn.disconnect()
                        continue
                    }
                }

                val text = try {
                    val stream = if (code >= 400) conn.errorStream else conn.inputStream
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                } catch (t: Throwable) {
                    ""
                }
                return Response(code, text, url)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun cookieHeader(url: String): String? = synchronized(cookies) {
        val host = URL(url).host ?: return null
        val map = cookies[host] ?: return null
        if (map.isEmpty()) null else map.entries.joinToString("; ") { it.key + "=" + it.value }
    }

    private fun storeCookies(url: String, setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val host = runCatching { URL(url).host }.getOrNull() ?: return
        synchronized(cookies) {
            val map = cookies.getOrPut(host) { HashMap() }
            for (raw in setCookies) {
                val first = raw.substringBefore(';').trim()
                val eq = first.indexOf('=')
                if (eq <= 0) continue
                val name = first.substring(0, eq).trim()
                val value = first.substring(eq + 1).trim()
                if (value.isEmpty() || value.equals("deleted", ignoreCase = true)) {
                    map.remove(name)
                } else {
                    map[name] = value
                }
            }
        }
    }

    private fun encodeForm(params: Map<String, String>): String =
        params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }

    private fun resolve(base: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) return location
        val b = URL(base)
        return if (location.startsWith("/")) {
            b.protocol + "://" + b.host + (if (b.port > 0 && b.port != b.defaultPort) ":" + b.port else "") + location
        } else {
            base.substringBeforeLast('/') + "/" + location
        }
    }
}
