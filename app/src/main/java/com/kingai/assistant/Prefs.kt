package com.kingai.assistant

import android.content.Context
import android.content.SharedPreferences

/** تنظیمات ساده‌ی KING AI (فقط روی همین گوشی ذخیره می‌شود). */
object Prefs {
    private const val FILE = "king_ai_prefs"
    private const val KEY_HISTORY_ENABLED = "history_enabled"

    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun historyEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_HISTORY_ENABLED, true)

    fun setHistoryEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HISTORY_ENABLED, enabled).apply()
    }
}
