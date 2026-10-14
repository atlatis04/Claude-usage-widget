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
private class BlockedException(msg: String) : Exception(msg)

/**
 * claude.ai 웹이 설정 > 사용량 화면에서 쓰는 비공개 엔드포인트를 호출한다.
 * 1차: 일반 HTTP 요청 (빠름)
 * 2차: Cloudflare에 막히면 숨은 WebView로 요청 (브라우저와 동일하게 통과)
 */
object UsageApi {
    private const val BASE = "https://claude.ai"

    private fun httpGet(ctx: Context, path: String, sessionKey: String): String {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            val jar = Browser.cookies()
            val cookie = if (jar != null && jar.contains("sessionKey=")) jar else "sessionKey=$sessionKey"
            setRequestProperty("Cookie", cookie)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", Browser.userAgent(ctx))
            setRequestProperty("Referer", "https://claude.ai/settings/usage")
            setRequestProperty("anthropic-client-platform", "web_claude_ai")
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) return conn.inputStream.bufferedReader().use { it.readText() }
            val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }
                .getOrNull().orEmpty()
            if (looksLikeCloudflare(body, conn)) throw BlockedException("HTTP $code (Cloudflare)")
            if (code == 401 || code == 403) throw AuthException("로그인이 만료됐어요 (HTTP $code)")
            throw Exception("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    private fun looksLikeCloudflare(body: String, conn: HttpURLConnection): Boolean =
        conn.getHeaderField("cf-mitigated") != null ||
            body.contains("Just a moment", true) ||
            body.contains("challenge-platform") ||
            body.contains("cf-chl") ||
            (body.trimStart().startsWith("<") && !body.contains("\"type\""))

    /** HTTP로 시도하고 막히면 WebView로 재시도 */
    private suspend fun get(ctx: Context, path: String, key: String): String {
        return try {
            httpGet(ctx, path, key)
        } catch (e: BlockedException) {
            val text = Browser.fetchText(ctx, BASE + path)
            checkErrorJson(text)
            text
        } catch (e: java.io.IOException) {
            val text = Browser.fetchText(ctx, BASE + path)
            checkErrorJson(text)
            text
        }
    }

    /** {"type":"error","error":{"type":"permission_error",...}} 형태면 로그인 만료로 처리 */
    private fun checkErrorJson(text: String) {
        if (!text.startsWith("{")) return
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (o.optString("type") == "error") {
            val t = o.optJSONObject("error")?.optString("type").orEmpty()
            if (t.contains("auth") || t.contains("permission") || t.contains("session"))
                throw AuthException("로그인이 만료됐어요")
            throw Exception(o.optJSONObject("error")?.optString("message") ?: "알 수 없는 오류")
        }
    }

    /** 채팅이 가능한 조직(개인 구독 조직)을 고르고 이름을 저장한다 */
    private suspend fun findOrg(ctx: Context, key: String): String {
        val text = get(ctx, "/api/organizations", key)
        checkErrorJson(text)
        val arr = JSONArray(text)
        if (arr.length() == 0) throw Exception("조직 정보를 찾지 못했어요")
        var pick = arr.getJSONObject(0)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if ((o.optJSONArray("capabilities")?.toString() ?: "").contains("chat")) { pick = o; break }
        }
        Prefs.setAccount(ctx, pick.optString("name").takeIf { it.isNotBlank() })
        return pick.getString("uuid").also { Prefs.setOrgId(ctx, it) }
    }

    /** 로그인 상태만 확인 (계정 이름 반환) */
    suspend fun checkLogin(ctx: Context): String {
        val key = Prefs.sessionKey(ctx) ?: throw AuthException("로그인이 필요해요")
        findOrg(ctx, key)
        return Prefs.account(ctx) ?: "로그인됨"
    }

    suspend fun fetch(ctx: Context): UsageSnapshot {
        val key = Prefs.sessionKey(ctx) ?: throw AuthException("로그인이 필요해요")
        val org = Prefs.orgId(ctx) ?: findOrg(ctx, key)
        val body = try {
            get(ctx, "/api/organizations/$org/usage", key)
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            // 조직이 바뀌었을 수 있으니 한 번 다시 찾는다
            get(ctx, "/api/organizations/${findOrg(ctx, key)}/usage", key)
        }
        checkErrorJson(body)
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
