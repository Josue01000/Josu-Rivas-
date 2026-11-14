package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AppConnectors {

    fun openWhatsAppWithText(context: Context, message: String, phoneDigits: String = ""): Boolean {
        val cleanPhone = phoneDigits.filter { it.isDigit() }
        if (cleanPhone.isNotBlank()) {
            val uri = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return launchSafe(context, intent)
        }

        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in whatsappPackages) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(pkg)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchSafe(context, intent)) return true
        }

        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=${Uri.encode(message)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchSafe(context, fallback)
    }

    fun openGmailCompose(
        context: Context,
        toEmail: String = "",
        subject: String,
        body: String
    ): Boolean {
        val mailtoUri = Uri.parse(
            "mailto:${Uri.encode(toEmail)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        val gmailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            setPackage("com.google.android.gm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchSafe(context, gmailIntent)) return true

        val genericMail = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchSafe(context, genericMail)
    }

    fun insertCalendarEvent(
        context: Context,
        title: String,
        description: String,
        location: String = "",
        isoDateTime: String = ""
    ): Boolean {
        val startMillis = parseIsoDateTimeMillis(isoDateTime)
        val endMillis = startMillis + 60 * 60 * 1000L

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "Evento Nexo AI" })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            if (location.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchSafe(context, intent)
    }

    fun openGeminiApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launchSafe(context, launchIntent)
        }
        val webFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchSafe(context, webFallback)
    }

    fun openNotificationListenerSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchSafe(context, intent)
    }

    fun shareText(context: Context, title: String, content: String): Boolean {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchSafe(context, chooser)
    }

    private fun parseIsoDateTimeMillis(iso: String): Long {
        if (iso.isNotBlank()) {
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
            )
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    val parsed = sdf.parse(iso.trim().take(16))
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
        }
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun launchSafe(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
