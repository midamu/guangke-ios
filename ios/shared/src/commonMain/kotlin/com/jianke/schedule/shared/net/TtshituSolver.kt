package com.jianke.schedule.shared.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.parseToJsonElement
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 验证码识别。失败返回 null，调用方负责降级（人工输入 / 网页登录）。 */
interface CaptchaSolver {
    suspend fun solve(png: ByteArray): String?
}

/**
 * 图鉴（ttshitu.com）在线打码。
 *
 * `typeid=11` 是「计算题」——学校的验证码是算术题（`9+5=`），不是普通字符题。
 * 只上传验证码图片本身（100×25 PNG，约 700 字节），不传账号、不传页面。
 */
class TtshituSolver(
    private val username: String,
    private val password: String,
    private val http: Http,
) : CaptchaSolver {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun solve(png: ByteArray): String? {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
            put("typeid", "11")
            put("image", Base64.encode(png))
        }.toString()

        val resp = runCatching { http.postJson("$BASE/predict", body) }.getOrNull() ?: return null
        if (!resp.ok) return null
        val root = runCatching { JSON.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return null
        val success = (root["success"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!success) return null
        val result = (root["data"] as? JsonObject)
            ?.let { (it["result"] as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return CaptchaAnswer.normalize(result)
    }

    /** 识别错了可以回传，平台退积分（失败不抛异常）。 */
    suspend fun report(id: String) {
        runCatching {
            http.postJson(
                "$BASE/reporter",
                buildJsonObject {
                    put("username", username)
                    put("password", password)
                    put("id", id)
                }.toString(),
            )
        }
    }

    companion object {
        private const val BASE = "https://api.ttshitu.com"
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
