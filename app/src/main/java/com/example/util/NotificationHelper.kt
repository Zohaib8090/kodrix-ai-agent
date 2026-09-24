package com.example.util

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
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID = "ai_coding_builds"
    private const val CHANNEL_NAME = "AI Project Builds & Coding"
    private const val CHANNEL_DESC = "Notifications for when background AI coding and builds are completed"
    private var isChannelCreated = false

    fun createNotificationChannel(context: Context) {
        if (isChannelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
            isChannelCreated = true
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyBuildCompleted(
        context: Context,
        projectName: String,
        projectId: String,
        isSuccess: Boolean = true,
        customMessage: String? = null
    ) {
        createNotificationChannel(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_PROJECT_ID", projectId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            projectId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSuccess) "Project Ready: $projectName" else "Build Warning: $projectName"
        val message = customMessage ?: if (isSuccess) {
            "AI finished coding your project! Tap to open workspace & live preview."
        } else {
            "AI finished processing. Tap to view status."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(projectId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Handled safely
        }
    }

    fun notifyAiRefineCompleted(
        context: Context,
        projectName: String,
        projectId: String,
        summary: String
    ) {
        createNotificationChannel(context)

        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_PROJECT_ID", projectId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            projectId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("✨ Code Updated: $projectName")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(projectId.hashCode() + 1, notification)
        } catch (_: SecurityException) {
            // Handled safely
        }
    }
}
