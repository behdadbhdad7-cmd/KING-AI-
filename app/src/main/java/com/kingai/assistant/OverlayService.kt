package com.kingai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        createNotification()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ابتدا مجوز پنجره شناور را فعال کن.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        showPanel()
    }

    private fun createNotification() {
        val channelId = "king_ai_running"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "KING AI", NotificationManager.IMPORTANCE_LOW
            ))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("KING AI فعال است")
            .setContentText("پنجره شناور آماده است")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(7, notification)
    }

    private fun showPanel() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val input = EditText(this).apply { hint = "مثلاً: برو سایت موبایل پیدا کن" }
        val status = TextView(this).apply {
            text = "سلام! من KING AI هستم. نسخه آزمایشی ۰.۱"
            textSize = 15f
        }
        val close = Button(this).apply {
            text = "بستن"
            setOnClickListener { stopSelf() }
        }
        val send = Button(this).apply {
            text = "ارسال فرمان"
            setOnClickListener {
                val command = input.text.toString().trim()
                status.text = if (command.isEmpty()) "اول یک فرمان بنویس."
                else "فرمان دریافت شد: $command\nمغز هوشمند و کنترل دستگاه در مرحله بعد متصل می‌شوند."
            }
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF17251F.toInt())
            addView(TextView(this@OverlayService).apply {
                text = "👑 KING AI"
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(status)
            addView(input)
            addView(send)
            addView(close)
        }
        panel?.let { view ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = 100
            }
            windowManager?.addView(view, params)
        }
    }

    override fun onDestroy() {
        panel?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
