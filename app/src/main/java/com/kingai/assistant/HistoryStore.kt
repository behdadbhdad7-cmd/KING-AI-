package com.kingai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val time: Long, val command: String, val result: String)

/** تاریخچه‌ی فرمان‌ها؛ فقط محلی، حداکثر ۱۰۰ مورد، جدیدترین اول. */
object HistoryStore {
    private const val KEY = "history_json"
    private const val MAX = 100

    private fun readArray(ctx: Context): JSONArray =
        try {
            JSONArray(Prefs.sp(ctx).getString(KEY, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

    fun add(ctx: Context, command: String, result: String) {
        if (!Prefs.historyEnabled(ctx)) return
        try {
            val old = readArray(ctx)
            val obj = JSONObject()
            obj.put("t", System.currentTimeMillis())
            obj.put("c", command)
            obj.put("r", result)
            val out = JSONArray()
            out.put(obj)
            val keep = minOf(old.length(), MAX - 1)
            for (i in 0 until keep) {
                out.put(old.get(i))
            }
            Prefs.sp(ctx).edit().putString(KEY, out.toString()).apply()
        } catch (e: Exception) {
            // ذخیره‌ی تاریخچه نباید هرگز باعث خرابی برنامه شود
        }
    }

    fun list(ctx: Context): List<HistoryEntry> {
        val arr = readArray(ctx)
        val result = ArrayList<HistoryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            result.add(
                HistoryEntry(
                    o.optLong("t", 0L),
                    o.optString("c", ""),
                    o.optString("r", "")
                )
            )
        }
        return result
    }

    fun count(ctx: Context): Int = readArray(ctx).length()

    fun clear(ctx: Context) {
        Prefs.sp(ctx).edit().remove(KEY).apply()
    }
}
