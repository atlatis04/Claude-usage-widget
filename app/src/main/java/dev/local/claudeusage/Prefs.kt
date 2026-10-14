package dev.local.claudeusage

import android.content.Context

/** 세션 키와 마지막 조회 결과를 앱 전용 저장소에 보관 */
object Prefs {
    private const val FILE = "claude_usage"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun sessionKey(ctx: Context): String? = sp(ctx).getString("session_key", null)
    fun setSessionKey(ctx: Context, v: String?) =
        sp(ctx).edit().putString("session_key", v).remove("org_id").apply()

    fun orgId(ctx: Context): String? = sp(ctx).getString("org_id", null)
    fun setOrgId(ctx: Context, v: String) = sp(ctx).edit().putString("org_id", v).apply()

    fun saveSnapshot(ctx: Context, s: UsageSnapshot) = sp(ctx).edit()
        .putFloat("five_pct", s.fivePct ?: -1f)
        .putString("five_reset", s.fiveReset)
        .putFloat("week_pct", s.weekPct ?: -1f)
        .putString("week_reset", s.weekReset)
        .putLong("fetched_at", s.fetchedAt)
        .putString("error", s.error)
        .apply()

    fun loadSnapshot(ctx: Context): UsageSnapshot {
        val p = sp(ctx)
        fun f(k: String) = p.getFloat(k, -1f).takeIf { it >= 0f }
        return UsageSnapshot(
            fivePct = f("five_pct"),
            fiveReset = p.getString("five_reset", null),
            weekPct = f("week_pct"),
            weekReset = p.getString("week_reset", null),
            fetchedAt = p.getLong("fetched_at", 0L),
            error = p.getString("error", null),
        )
    }

    fun clear(ctx: Context) = sp(ctx).edit().clear().apply()
}
