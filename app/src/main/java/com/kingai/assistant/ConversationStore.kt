package com.kingai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ConversationMessage(val role: String, val content: String, val time: Long = System.currentTimeMillis())
data class ConversationSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ConversationMessage>
)

/** نگهداری گفتگوهای کامل روی همین گوشی */
object ConversationStore {
    private const val KEY = "conversations_v1"
    private const val MIGRATED = "legacy_history_migrated_v1"

    private fun read(ctx: Context): JSONArray = try {
        JSONArray(Prefs.sp(ctx).getString(KEY, "[]") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    fun create(ctx: Context): String = UUID.randomUUID().toString()

    fun add(ctx: Context, id: String, role: String, content: String) {
        if (content.isBlank()) return
        try {
            val all = read(ctx)
            var found: JSONObject? = null
            for (i in 0 until all.length()) {
                val item = all.optJSONObject(i) ?: continue
                if (item.optString("id") == id) { found = item; break }
            }
            val session = found ?: JSONObject().apply {
                put("id", id)
                put("title", if (role == "user") content.take(52) else "گفتگوی جدید")
                put("messages", JSONArray())
            }
            val messages = session.optJSONArray("messages") ?: JSONArray()
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", content)
                put("time", System.currentTimeMillis())
            })
            session.put("messages", messages)
            session.put("updated", System.currentTimeMillis())
            if (role == "user" && (session.optString("title") == "گفتگوی جدید" || messages.length() == 1)) {
                session.put("title", content.replace("\n", " ").take(52))
            }
            val out = JSONArray().put(session)
            for (i in 0 until all.length()) {
                val item = all.optJSONObject(i) ?: continue
                if (item.optString("id") != id) out.put(item)
            }
            // نگهداری حداکثر 100 گفتگوی اخیر
            val limited = JSONArray()
            for (i in 0 until minOf(out.length(), 100)) limited.put(out.get(i))
            Prefs.sp(ctx).edit().putString(KEY, limited.toString()).apply()
        } catch (_: Exception) { }
    }

    fun list(ctx: Context): List<ConversationSession> {
        migrateOldHistory(ctx)
        return parse(read(ctx))
    }

    private fun migrateOldHistory(ctx: Context) {
        val prefs = Prefs.sp(ctx)
        if (prefs.getBoolean(MIGRATED, false)) return
        try {
            HistoryStore.list(ctx).asReversed().forEach { old ->
                val id = "legacy-" + old.time
                add(ctx, id, "user", old.command)
                add(ctx, id, "assistant", old.result)
            }
        } catch (_: Exception) { }
        prefs.edit().putBoolean(MIGRATED, true).apply()
    }

    fun get(ctx: Context, id: String): ConversationSession? =
        list(ctx).firstOrNull { it.id == id }

    fun clear(ctx: Context) {
        Prefs.sp(ctx).edit().remove(KEY).apply()
    }

    private fun parse(arr: JSONArray): List<ConversationSession> {
        val result = ArrayList<ConversationSession>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val msgs = o.optJSONArray("messages") ?: JSONArray()
            val parsed = ArrayList<ConversationMessage>()
            for (j in 0 until msgs.length()) {
                val m = msgs.optJSONObject(j) ?: continue
                parsed.add(ConversationMessage(m.optString("role"), m.optString("content"), m.optLong("time")))
            }
            result.add(ConversationSession(
                o.optString("id"),
                o.optString("title", "گفتگوی قدیمی"),
                o.optLong("updated"),
                parsed
            ))
        }
        return result
    }
}
