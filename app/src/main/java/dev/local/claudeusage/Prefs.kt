package dev.local.claudeusage

import android.content.Context

enum class AuthState { NONE, OK, EXPIRED }

/** 세션 키와 마지막 조회 결과를 앱 전용 저장소에 보관 */
object Prefs {
    private const val FILE = "claude_usage"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun sessionKey(ctx: Context): String? = sp(ctx).getString("session_key", null)
    fun setSessionKey(ctx: Context, v: String?) = sp(ctx).edit()
        .putString("session_key", v).remove("org_id").remove("account")
        .putString("auth", if (v == null) AuthState.NONE.name else AuthState.OK.name)
        .apply()

    fun orgId(ctx: Context): String? = sp(ctx).getString("org_id", null)
    fun setOrgId(ctx: Context, v: String) = sp(ctx).edit().putString("org_id", v).apply()

    /** 로그인된 계정(조직) 이름 */
    fun account(ctx: Context): String? = sp(ctx).getString("account", null)
    fun setAccount(ctx: Context, v: String?) = sp(ctx).edit().putString("account", v).apply()

    fun authState(ctx: Context): AuthState =
        if (sessionKey(ctx) == null) AuthState.NONE
        else runCatching { AuthState.valueOf(sp(ctx).getString("auth", "OK")!!) }.getOrDefault(AuthState.OK)
    fun setAuthState(ctx: Context, s: AuthState) = sp(ctx).edit().putString("auth", s.name).apply()

    fun refreshing(ctx: Context) = sp(ctx).getBoolean("refreshing", false)
    fun setRefreshing(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("refreshing", v).apply()

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
