package dev.local.claudeusage

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class UsageWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val snap = try {
            UsageApi.fetch(ctx)
        } catch (e: AuthException) {
            Prefs.loadSnapshot(ctx).copy(error = e.message)
        } catch (e: Exception) {
            Prefs.loadSnapshot(ctx).copy(error = "갱신 실패: ${e.message}")
        }
        Prefs.saveSnapshot(ctx, snap)
        UsageWidget.renderAll(ctx)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "claude_usage_periodic"
        private const val NOW = "claude_usage_now"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 15분마다 자동 갱신 (안드로이드 최소 주기) */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UsageWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UsageWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
    }
}
