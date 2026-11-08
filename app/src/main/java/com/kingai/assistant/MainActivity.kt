package com.kingai.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        layout.addView(TextView(this).apply {
            text = "KING AI 0.1\n\nاین صفحه فقط برای راه‌اندازی اولیه است. مجوز پنجره شناور را فعال کن؛ بعد از آن از دکمه KING AI در پنل بالای گوشی استفاده کن."
            textSize = 18f
        })
        layout.addView(Button(this).apply {
            text = "فعال‌سازی پنجره شناور"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            // The user has returned from the overlay-permission screen.
            // The Quick Settings tile can now launch the floating panel.
        }
    }
}
