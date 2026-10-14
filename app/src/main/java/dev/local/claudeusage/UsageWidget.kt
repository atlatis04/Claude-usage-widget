package dev.local.claudeusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class UsageWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        UsageWorker.schedule(context)
        if (Prefs.sessionKey(context) != null) UsageWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        UsageWorker.cancel(context)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx)
        UsageWorker.schedule(ctx)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            UsageWorker.refreshNow(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "dev.local.claudeusage.REFRESH"

        fun renderAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, UsageWidget::class.java))
            if (ids.isEmpty()) return
            val v = build(ctx)
            ids.forEach { mgr.updateAppWidget(it, v) }
        }

        private fun build(ctx: Context): RemoteViews {
            val s = Prefs.loadSnapshot(ctx)
            val auth = Prefs.authState(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_usage)

            fun pct(p: Float?) = p?.let { "${it.toInt()}%" } ?: "—"
            v.setTextViewText(R.id.five_pct, pct(s.fivePct))
            v.setProgressBar(R.id.five_bar, 100, (s.fivePct ?: 0f).toInt().coerceIn(0, 100), false)
            v.setTextViewText(R.id.five_reset, resetLabel(s.fiveReset, short = true))

            v.setTextViewText(R.id.week_pct, pct(s.weekPct))
            v.setProgressBar(R.id.week_bar, 100, (s.weekPct ?: 0f).toInt().coerceIn(0, 100), false)
            v.setTextViewText(R.id.week_reset, resetLabel(s.weekReset, short = false))

            // 로그인 상태 표시
            val (statusText, statusColor) = when (auth) {
                AuthState.NONE -> "● 로그인 필요" to "#9E9E9E"
                AuthState.EXPIRED -> "● 로그인 만료" to "#FF7A6B"
                AuthState.OK -> "● 로그인됨" to "#8FD18F"
            }
            v.setTextViewText(R.id.status, statusText)
            v.setTextColor(R.id.status, Color.parseColor(statusColor))

            val footer = when {
                Prefs.refreshing(ctx) -> "새로고침 중…"
                auth == AuthState.NONE -> "탭해서 로그인"
                auth == AuthState.EXPIRED -> "탭해서 다시 로그인"
                s.error != null -> s.error
                s.fetchedAt > 0 -> "갱신 " + Instant.ofEpochMilli(s.fetchedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                else -> "불러오는 중…"
            }
            v.setTextViewText(R.id.footer, footer)
            v.setTextViewText(R.id.refresh_btn, if (Prefs.refreshing(ctx)) "…" else "⟳")

            // 위젯 본문 탭 → 앱 열기 (로그인 상태 확인/다시 로그인)
            v.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            // ⟳ 버튼 → 즉시 새로고침
            v.setOnClickPendingIntent(
                R.id.refresh_btn,
                PendingIntent.getBroadcast(
                    ctx, 1,
                    Intent(ctx, UsageWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return v
        }

        /** "2시간 13분 후 리셋" / "10/18(토) 14:00 리셋" */
        fun resetLabel(iso: String?, short: Boolean): String {
            if (iso.isNullOrBlank()) return ""
            val t = try { OffsetDateTime.parse(iso).toInstant() } catch (e: Exception) { return "" }
            val d = Duration.between(Instant.now(), t)
            if (d.isNegative) return "리셋됨"
            return if (short || d.toHours() < 24) {
                val h = d.toHours(); val m = d.toMinutes() % 60
                if (h > 0) "${h}시간 ${m}분 후 리셋" else "${m}분 후 리셋"
            } else {
                t.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN)) + " 리셋"
            }
        }
    }
}
