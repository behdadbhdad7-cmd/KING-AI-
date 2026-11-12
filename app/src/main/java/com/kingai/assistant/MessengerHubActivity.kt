
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
    private val backgroundColor = Color.rgb(5, 13, 10)
    private val cardColor = Color.rgb(13, 27, 21)
    private val muted = Color.rgb(160, 180, 168)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(green)
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }

        header.addView(back, LinearLayout.LayoutParams(dp(45), dp(48)))

        val heading = TextView(this).apply {
            text = "مرکز پیام‌رسان‌ها"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        root.addView(header)

        val description = TextView(this).apply {
            text = "حساب‌های پیام‌رسانت را از این بخش مدیریت کن"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(22))
        }

        root.addView(description)

        val scroll = ScrollView(this)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        addMessenger(
            list,
            "تلگرام",
            "مدیریت و افزودن حساب‌های تلگرام",
            "ورود به حساب‌های تلگرام",
            true
        )

        addMessenger(
            list,
            "روبیکا",
            "اتصال مستقیم هنوز راه‌اندازی نشده",
            "به‌زودی",
            false
        )

        addMessenger(
            list,
            "سروش",
            "اتصال مستقیم هنوز راه‌اندازی نشده",
            "به‌زودی",
            false
        )

        addMessenger(
            list,
            "آی‌گپ",
            "اتصال مستقیم هنوز راه‌اندازی نشده",
            "به‌زودی",
            false
        )

        addMessenger(
            list,
            "ایتا",
            "اتصال مستقیم هنوز راه‌اندازی نشده",
            "به‌زودی",
            false
        )

        scroll.addView(list)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun addMessenger(
        parent: LinearLayout,
        name: String,
        description: String,
        actionText: String,
        isTelegram: Boolean
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(cardColor, green, 18)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameView = TextView(this).apply {
            text = name
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        top.addView(
            nameView,
            LinearLayout.LayoutParams(0, dp(35), 1f)
        )

        val status = TextView(this).apply {
            text = if (isTelegram) "قابل اتصال" else "به‌زودی"
            textSize = 11f
            setTextColor(if (isTelegram) green else muted)
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = roundedBackground(
                Color.rgb(20, 45, 32),
                if (isTelegram) green else muted,
                30
            )
        }

        top.addView(status)
        card.addView(top)

        val info = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(12))
        }

        card.addView(info)

        val action = TextView(this).apply {
            text = actionText
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isTelegram) green else muted)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(
                Color.rgb(8, 22, 15),
                if (isTelegram) green else muted,
                12
            )

            setOnClickListener {
                if (isTelegram) {
                    startActivity(
                        Intent(
                            this@MessengerHubActivity,
                            TelegramAccountsActivity::class.java
                        )
                    )
                } else {
                    Toast.makeText(
                        this@MessengerHubActivity,
                        "اتصال این پیام‌رسان هنوز پیاده‌سازی نشده است",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        card.addView(
            action,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = dp(13)
        parent.addView(card, params)
    }

    private fun roundedBackground(
        color: Int,
        borderColor: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), borderColor)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
