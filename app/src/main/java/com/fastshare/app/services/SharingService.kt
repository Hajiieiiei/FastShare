package com.fastshare.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fastshare.app.R
import com.fastshare.app.presentation.MainActivity
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

const val CHANNEL_TRANSFER = "channel_transfer"
const val CHANNEL_DISCOVERY = "channel_discovery"
const val NOTIF_ID_TRANSFER = 1001
const val NOTIF_ID_DISCOVERY = 1002

/**
 * Foreground service keeping transfers and discovery alive in the background.
 * Android 14+ mandates `dataSync` foregroundServiceType for this use case.
 */
@AndroidEntryPoint
class SharingService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        startForeground(NOTIF_ID_DISCOVERY, discoveryNotification("FastShare is discoverable"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun updateTransferProgress(peerName: String, percent: Int) {
        val text = "Sending file to $peerName · $percent%"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID_TRANSFER, transferNotification(text, percent))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun transferNotification(text: String, percent: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_TRANSFER)
            .setContentTitle("FastShare Transfer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, percent, percent < 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    private fun discoveryNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_DISCOVERY)
            .setContentTitle("FastShare")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel(CHANNEL_TRANSFER, "Transfers", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DISCOVERY, "Discovery", NotificationManager.IMPORTANCE_MIN),
            ).forEach(manager::createNotificationChannel)
        }

        fun start(context: Context) {
            val intent = Intent(context, SharingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SharingService::class.java))
        }
    }
}
