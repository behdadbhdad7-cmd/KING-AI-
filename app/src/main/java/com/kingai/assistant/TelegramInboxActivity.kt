
package com.kingai.assistant

import android.os.Bundle
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.json.JSONArray

class TelegramInboxActivity : ComponentActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Neon.BLACK)
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "مرکز پیام‌های تلگرام"
            textSize = 22f
            setTextColor(Neon.GREEN)
        }

        root.addView(title)

        val info = TextView(this).apply {
            text = "پیام‌های دریافتی از اعلان‌های تلگرام"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(16))
        }

        root.addView(info)

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val refresh = NeonButton(this, "به‌روزرسانی پیام‌ها", true)
        refresh.setOnClickListener { loadMessages() }
        root.addView(refresh)

        setContentView(root)
        loadMessages()
    }

    private fun loadMessages() {
        list.removeAllViews()

        val prefs = getSharedPreferences(
            TelegramNotificationListener.PREFS,
            MODE_PRIVATE
        )

        val raw = prefs.getString("messages", "[]") ?: "[]"

        try {
            val array = JSONArray(raw)

            if (array.length() == 0) {
                addText("هنوز پیام تلگرامی دریافت نشده است.")
                return
            }

            for (i in array.length() - 1 downTo 0) {
                val item = array.getJSONObject(i)

                val sender = item.optString(
                    "sender",
                    "مخاطب تلگرام"
                )

                val message = item.optString("message", "")

                addText("فرستنده: $sender\n\n$message")
            }

        } catch (_: Exception) {
            addText("خطا در خواندن پیام‌ها.")
        }
    }

    private fun addText(value: String) {
        val card = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(25, 35, 30))
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(10)
        list.addView(card, params)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
