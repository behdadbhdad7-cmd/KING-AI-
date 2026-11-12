package com.kingai.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MessengerHubActivity : Activity() {
    private val green = Color.rgb(0, 255, 130)
    private val bg = Color.rgb(5, 13, 10)
    private val cardColor = Color.rgb(13, 27, 21)
    private val muted = Color.rgb(160, 180, 168)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val title = TextView(this).apply {
            text = "‹     مرکز پیام‌رسان‌ها"
            textSize = 22f
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finish() }
        }
        root.addView(title)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addMessenger(list, "تلگرام", "مدیریت و افزودن حساب‌های تلگرام", true)
        addMessenger(list, "روبیکا", "به‌زودی", false)
        addMessenger(list, "سروش", "به‌زودی", false)
        addMessenger(list, "آی‌گپ", "به‌زودی", false)
        addMessenger(list, "ایتا", "به‌زودی", false)

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addMessenger(
        parent: LinearLayout,
        name: String,
        description: String,
        enabled: Boolean
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(cardColor, green)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = name
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(title)

        val info = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(12))
        }
        card.addView(info)

        val button = TextView(this).apply {
            text = if (enabled) "ورود به حساب‌های تلگرام" else "به‌زودی"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(if (enabled) green else muted)
            background = rounded(bg, if (enabled) green else muted)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setOnClickListener {
                if (enabled) {
                    startActivity(
                        Intent(
                            this@MessengerHubActivity,
                            TelegramAccountsActivity::class.java
                        )
                    )
                } else {
                    Toast.makeText(
                        this@MessengerHubActivity,
                        "به‌زودی",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        card.addView(button)

        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(14)
        parent.addView(card, params)
    }

    private fun rounded(color: Int, border: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), border)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
