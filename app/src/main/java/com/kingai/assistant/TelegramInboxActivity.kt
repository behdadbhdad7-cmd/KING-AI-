package com.kingai.assistant

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

class TelegramInboxActivity : ComponentActivity() {

    private lateinit var list: LinearLayout
    private lateinit var manager: TelegramAccountManager

    private val screenScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = TelegramAccountManager.get(this)

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
            text = "پیام‌های خصوصی دریافتی از حساب‌های متصل"
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

        val refresh = NeonButton(
            this,
            "به‌روزرسانی پیام‌ها",
            true
        )

        refresh.setOnClickListener {
            renderMessages(manager.messages.value)
        }

        root.addView(refresh)

        setContentView(root)

        screenScope.launch {
            manager.messages.collect { messages ->
                renderMessages(messages)
            }
        }
    }

    private fun renderMessages(
        allMessages: Map<String, List<TelegramAccountManager.IncomingMessage>>
    ) {
        list.removeAllViews()

        val messages = allMessages.values
            .flatten()
            .sortedByDescending { it.date }

        if (messages.isEmpty()) {
            addText("هنوز پیام جدیدی دریافت نشده است.")
            return
        }

        messages.forEach { item ->

            val accountName = when (item.accountId) {
                TelegramAccountManager.ACCOUNT_ONE -> "حساب اول"
                TelegramAccountManager.ACCOUNT_TWO -> "حساب دوم"
                else -> "حساب تلگرام"
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
                )
                setBackgroundColor(Color.rgb(25, 35, 30))
            }

            val account = TextView(this).apply {
                text = accountName
                textSize = 12f
                setTextColor(Neon.GREEN)
            }

            val sender = TextView(this).apply {
                text = item.sender
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, dp(8), 0, dp(8))
            }

            val message = TextView(this).apply {
                text = item.message
                textSize = 15f
                setTextColor(Color.LTGRAY)
            }

            card.addView(account)
            card.addView(sender)
            card.addView(message)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(10)

            list.addView(card, params)
        }
    }

    private fun addText(value: String) {
        val text = TextView(this).apply {
            this.text = value
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(dp(14), dp(24), dp(14), dp(24))
        }

        list.addView(
            text,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    override fun onDestroy() {
        screenScope.cancel()
        super.onDestroy()
    }
}
