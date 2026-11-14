package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.model.CapturedNotificationEvent
import java.util.UUID

class NexoNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        val appLabel = when (pkg) {
            WHATSAPP_PKG, WHATSAPP_BUSINESS_PKG -> "WhatsApp"
            GMAIL_PKG -> "Gmail"
            GEMINI_PKG -> "Gemini App"
            else -> return
        }

        val extras = notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val message = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()

        if (sender.isBlank() && message.isBlank()) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty() && action.actionIntent != null
        }

        val handle = if (replyAction != null && replyAction.remoteInputs != null) {
            ReplyActionHandle(
                actionIntent = replyAction.actionIntent,
                remoteInputs = replyAction.remoteInputs
            )
        } else {
            null
        }

        AgentEventBus.publishCapturedNotification(
            event = CapturedNotificationEvent(
                id = "${sbn.key}_${UUID.randomUUID().toString().take(6)}",
                packageName = pkg,
                appLabel = appLabel,
                sender = sender.ifBlank { appLabel },
                message = message,
                timestampMillis = sbn.postTime,
                hasInlineReply = handle != null
            ),
            replyHandle = handle
        )
    }

    companion object {
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
        const val GMAIL_PKG = "com.google.android.gm"
        const val GEMINI_PKG = "com.google.android.apps.bard"
    }
}
