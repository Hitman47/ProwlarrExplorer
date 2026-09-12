package dev.mkdev.prowlarrexplorer.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.mkdev.prowlarrexplorer.MainActivity
import dev.mkdev.prowlarrexplorer.R
import dev.mkdev.prowlarrexplorer.data.CompletionTracker
import dev.mkdev.prowlarrexplorer.data.QbitClient
import dev.mkdev.prowlarrexplorer.data.SettingsStore
import dev.mkdev.prowlarrexplorer.domain.ReleaseTitle
import dev.mkdev.prowlarrexplorer.domain.Torrent
import dev.mkdev.prowlarrexplorer.domain.humanSize
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Toutes les 15 min (minimum WorkManager), interroge qBittorrent et notifie les téléchargements
 * terminés depuis le dernier passage. Ne tourne que si qBittorrent est configuré et l'option active.
 */
class DownloadWatcher(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val settings = store.settings.first()
        if (!settings.qbit.configured || !store.notifyDone.first()) return Result.success()

        val torrents = runCatching { QbitClient { settings.qbit }.torrents() }.getOrElse { return Result.retry() }
        val fresh = CompletionTracker(applicationContext).record(torrents)
        fresh.forEach { notify(applicationContext, it) }
        return Result.success()
    }

    companion object {
        private const val WORK = "download-watcher"
        const val CHANNEL = "downloads"
        const val EXTRA_TAB = "tab"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DownloadWatcher>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK)

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Téléchargements terminés", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Un torrent qBittorrent vient de se terminer"
                },
            )
        }

        fun canNotify(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun notify(ctx: Context, t: Torrent) {
            if (!canNotify(ctx)) return
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(
                ctx, t.hash.hashCode(),
                Intent(ctx, MainActivity::class.java).putExtra(EXTRA_TAB, 1).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val parsed = ReleaseTitle.parse(t.name)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Téléchargement terminé")
                .setContentText(parsed.heading)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${parsed.heading}\n${t.size.humanSize()}" + t.category.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup(CHANNEL)
                .build()
            NotificationManagerCompat.from(ctx).notify(t.hash.hashCode(), n)
        }
    }
}
