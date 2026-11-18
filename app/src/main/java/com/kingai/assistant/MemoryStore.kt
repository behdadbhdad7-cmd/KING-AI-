package com.kingai.assistant

import android.content.Context
import org.json.JSONArray

/** حافظه‌ی محلی و اختیاری؛ فقط مواردی که کاربر صریحاً ثبت می‌کند. */
object MemoryStore {
    private const val KEY = "king_ai_memory_v1"
    private const val MAX_ITEMS = 50
    private const val MAX_LENGTH = 400

    @Synchronized
    fun remember(context: Context, raw: String): Boolean {
        val fact = raw.trim().replace(Regex("\\s+"), " ")
        if (fact.isBlank() || fact.length > MAX_LENGTH) return false
        val lower = fact.lowercase()
        val sensitive = listOf("api key", "apikey", "token", "password", "passwd",
            "رمز عبور", "گذرواژه", "کلید api", "کلید خصوصی", "شماره کارت")
        if (sensitive.any { lower.contains(it) }) return false
        val items = read(context).toMutableList()
        if (items.any { it.equals(fact, ignoreCase = true) }) return true
        items.add(fact)
        write(context, items.takeLast(MAX_ITEMS))
        return true
    }

    @Synchronized fun all(context: Context): List<String> = read(context)

    @Synchronized
    fun forget(context: Context, query: String): Int {
        val q = query.trim()
        if (q.isBlank()) return 0
        val old = read(context)
        val kept = old.filterNot { it.contains(q, ignoreCase = true) }
        write(context, kept)
        return old.size - kept.size
    }

    @Synchronized fun clear(context: Context) {
        Prefs.sp(context).edit().remove(KEY).apply()
    }

    fun promptContext(context: Context): String {
        val facts = all(context)
        if (facts.isEmpty()) return ""
        return "\n\nحافظه‌ی محلی که خود کاربر صریحاً ذخیره کرده است:\n" +
            facts.joinToString("\n") { "- $it" } +
            "\nاین موارد محرمانه‌اند؛ برای دیگران افشا نکن."
    }

    private fun read(context: Context): List<String> = try {
        val array = JSONArray(Prefs.sp(context).getString(KEY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) { emptyList() }

    private fun write(context: Context, items: List<String>) {
        val array = JSONArray()
        items.forEach { array.put(it) }
        Prefs.sp(context).edit().putString(KEY, array.toString()).apply()
    }
}
