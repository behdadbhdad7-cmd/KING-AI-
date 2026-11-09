package com.kingai.assistant

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

/** تاریخچه‌ی فرمان‌ها (فقط محلی) با امکان پاک‌کردن. */
class HistoryActivity : ComponentActivity() {

    private lateinit var listHost: LinearLayout
    private var built = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNeonWindow()

        val (root, content) = neonScreen(this)
        content.addView(topBar(this, "تاریخچه فرمان‌ها"), vlp(0))

        val clearBtn = NeonButton(this, "پاک‌کردن تاریخچه", true)
        clearBtn.setOnClickListener {
            if (HistoryStore.count(this) == 0) {
                toast("تاریخچه از قبل خالی است.")
            } else {
                confirmClearHistory { render() }
            }
        }
        content.addView(clearBtn, vlp(18))

        listHost = LinearLayout(this)
        listHost.orientation = LinearLayout.VERTICAL
        content.addView(listHost, vlp(6))

        setContentView(root)
        built = true
        render()
    }

    override fun onResume() {
        super.onResume()
        if (built) render()
    }

    private fun render() {
        listHost.removeAllViews()

        if (!Prefs.historyEnabled(this)) {
            val note = neonCard(this)
            note.addView(
                label(
                    this,
                    "ذخیره‌ی تاریخچه در تنظیمات خاموش است؛ فرمان‌های جدید ثبت نمی‌شوند.",
                    13f, Neon.AMBER
                )
            )
            listHost.addView(note, vlp(12))
        }

        val entries = HistoryStore.list(this)
        if (entries.isEmpty()) {
            val empty = neonCard(this)
            empty.addView(label(this, "تاریخچه خالی است.", 14f, Neon.TEXT_DIM))
            listHost.addView(empty, vlp(12))
            return
        }

        for (e in entries) {
            val card = neonCard(this)
            card.addView(label(this, e.command, 15f, Color.WHITE, true))
            card.addView(label(this, e.result, 13f, Neon.TEXT_DIM), vlp(6))
            card.addView(
                label(
                    this,
                    persianDate(e.time, false) + "  " + clockTime(e.time),
                    11f, Neon.GREEN_SOFT
                ),
                vlp(8)
            )
            listHost.addView(card, vlp(12))
        }
    }
}
