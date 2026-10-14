package dev.local.claudeusage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UsageSnapshot(
    val fivePct: Float? = null,
    val fiveReset: String? = null,   // ISO-8601
    val weekPct: Float? = null,
    val weekReset: String? = null,
    val fetchedAt: Long = 0L,
    val error: String? = null,
)

class AuthException(msg: String) : Exception(msg)

/**
 * claude.ai 웹이 설정 > 사용량 화면에서 쓰는 비공개 엔드포인트를 호출한다.
 * 공식 API가 아니므로 응답 형식이 바뀌면 parseUsage()만 고치면 된다.
 */
object UsageApi {
    private const val BASE = "https://claude.ai"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

    private fun get(path: String, sessionKey: String): String {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Cookie", "sessionKey=$sessionKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", UA)
            setRequestProperty("anthropic-client-platform", "web_claude_ai")
        }
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) throw AuthException("로그인이 만료됐어요 (HTTP $code)")
            if (code !in 200..299) throw Exception("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 채팅이 가능한 조직(개인 구독 조직)을 고른다 */
    private fun findOrgId(sessionKey: String): String {
        val arr = JSONArray(get("/api/organizations", sessionKey))
        if (arr.length() == 0) throw Exception("조직 정보를 찾지 못했어요")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val caps = o.optJSONArray("capabilities")?.toString() ?: ""
            if (caps.contains("chat")) return o.getString("uuid")
        }
        return arr.getJSONObject(0).getString("uuid")
    }

    fun fetch(ctx: Context): UsageSnapshot {
        val key = Prefs.sessionKey(ctx) ?: throw AuthException("로그인이 필요해요")
        val org = Prefs.orgId(ctx) ?: findOrgId(key).also { Prefs.setOrgId(ctx, it) }
        val body = try {
            get("/api/organizations/$org/usage", key)
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            // 조직이 바뀌었을 수 있으니 한 번 다시 찾는다
            val fresh = findOrgId(key).also { Prefs.setOrgId(ctx, it) }
            get("/api/organizations/$fresh/usage", key)
        }
        return parseUsage(JSONObject(body))
    }

    internal fun parseUsage(json: JSONObject): UsageSnapshot {
        fun block(name: String): Pair<Float?, String?> {
            val b = json.optJSONObject(name) ?: return null to null
            // utilization은 0~100 퍼센트 값
            val pct = if (b.isNull("utilization")) null else b.optDouble("utilization").toFloat()
            val reset = if (b.isNull("resets_at")) null else b.optString("resets_at")
            return pct to reset
        }
        val (fp, fr) = block("five_hour")
        val (wp, wr) = block("seven_day")
        return UsageSnapshot(fp, fr, wp, wr, System.currentTimeMillis(), null)
    }
}
