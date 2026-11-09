package com.kingai.assistant

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class TelegramNotificationListener : NotificationListenerService() {

    companion object {
        const val PREFS = "telegram_inbox"
        const val KEY_ENABLED = "monitor_enabled"

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "org.telegram.messenger") return
        if (!isEnabled(this)) return

        val extras = sbn.notification.extras ?: return

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString() ?: "مخاطب تلگرام"

        val message = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString() ?: return

        if (message.isBlank()) return

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        try {
            val array = JSONArray(
                prefs.getString("messages", "[]") ?: "[]"
            )

            val item = JSONObject().apply {
                put("sender", sender)
                put("message", message)
                put("time", System.currentTimeMillis())
                put("notificationId", sbn.id)
            }

            array.put(item)

            while (array.length() > 100) {
                array.remove(0)
            }

            prefs.edit()
                .putString("messages", array.toString())
                .apply()

        } catch (_: Exception) {
        }
    }
}

