package com.jianke.schedule.net

import java.time.LocalDate

/**
 * 正方教务（jw.gzist.edu.cn）侧的取数。
 *
 * 只做两件事：进课表页把 Cookie 走顺，然后调 `xskbcx_cxXsKb.html` 拿 kbList。
 * 解析交给现有的 [com.jianke.schedule.data.ZfsoftParser]，不重复造轮子。
 */
class JwClient(private val http: HttpLite) {

    companion object {
        const val JW = "http://jw.gzist.edu.cn"
        const val TABLE_PAGE =
            "$JW/jwglxt/kbcx/xskbcxMobile_cxTimeTableIndex.html?gnmkdm=Y253511&hasHeader=true"

        /** 接口候选，顺序与页面里的探测脚本一致。 */
        private val API_TARGETS = listOf(
            "$JW/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N253508",
            "$JW/jwglxt/kbcx/xskbcx_cxXsKb.html",
            "$JW/jwglxt/kbcx/xskbcxMobile_cxXsKb.html?gnmkdm=Y253511",
        )
    }

    /** 先打开课表页：个别学期要页面初始化才会把 Cookie 写齐。 */
    fun warmUp() {
        runCatching { http.get(TABLE_PAGE) }
    }

    /**
     * @return 接口原始 JSON；全部候选都失败返回 null
     */
    fun fetchKbList(xnm: String, xqm: String): String? {
        val params = mapOf("xnm" to xnm, "xqm" to xqm)
        // 正方对 Referer / X-Requested-With 敏感：少了会 403（与页面里 fetch 的头保持一致）
        val headers = mapOf(
            "Referer" to TABLE_PAGE,
            "X-Requested-With" to "XMLHttpRequest",
        )
        for (url in API_TARGETS) {
            val resp = runCatching { http.postForm(url, params, headers) }.getOrNull() ?: continue
            val body = resp.body.trim()
            if (!resp.ok || body.isEmpty()) continue
            // 正方形成功时返回 {"kbList":[...]}；被踢回登录页会是一段 HTML
            if (body.startsWith("{") && (body.contains("kbList") || body.contains("kcb"))) {
                return body
            }
        }
        return null
    }

    /** 学年/学期推算：8 月起算新学年，第一学期 xqm=3、第二学期 xqm=12。 */
    fun academicTerm(today: LocalDate = LocalDate.now()): Pair<String, String> {
        val year = today.year
        val month = today.monthValue
        return when {
            month >= 8 -> year.toString() to "3"
            month == 1 -> (year - 1).toString() to "3"
            else -> (year - 1).toString() to "12"
        }
    }
}
