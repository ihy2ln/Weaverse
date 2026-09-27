package com.ihy2ln.weaverse.feature.roleplay.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ihy2ln.weaverse.MainActivity
import com.ihy2ln.weaverse.R

/** Visible Android foreground service for user-started chapter AI work. */
class MangaAiForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notification(this, MangaAiTaskState(action =
            intent?.getStringExtra(EXTRA_ACTION).orEmpty(), running = true))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "manga_ai_background"
        private const val NOTIFICATION_ID = 73042
        private const val EXTRA_ACTION = "action"

        fun start(context: Context, action: String) {
            ensureChannel(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, MangaAiForegroundService::class.java).putExtra(EXTRA_ACTION, action),
            )
        }

        fun update(context: Context, state: MangaAiTaskState) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification(context, state))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MangaAiForegroundService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                "Manga AI processing",
                NotificationManager.IMPORTANCE_LOW,
            ))
        }

        private fun notification(context: Context, state: MangaAiTaskState): Notification {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = state.status.ifBlank { state.action.ifBlank { "Processing manga pages" } }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Weaverse manga AI")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(state.total, state.current, state.total <= 0)
                .build()
        }
    }
}
