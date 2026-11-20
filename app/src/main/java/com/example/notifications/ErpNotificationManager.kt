package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.CriticalAlert

private const val CHANNEL_ID = "kumar_foods_critical_erp_alerts"

class ErpNotificationManager(private val context: Context) {

    private val notifiedIds = mutableSetOf<String>()

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun dispatchAlertIfNew(alert: CriticalAlert) {
        if (!hasNotificationPermission()) return
        if (!notifiedIds.add(alert.id)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kumar Foods ERP • ${alert.title}")
            .setContentText(alert.subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.subtitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and notify
        }
    }

    fun resetDispatchedAlerts() {
        notifiedIds.clear()
    }
}
