package com.kingai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
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
            Toast.makeText(
                this,
                "ابتدا مجوز پنجره شناور را فعال کن.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        if (!showPanel()) {
            Toast.makeText(
                this,
                "پنل باز نشد.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        running = true
        refreshTile()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channelId = "king_ai_running"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "KING AI",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("KING AI فعال است")
            .setContentText("پنجره شناور آماده است")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(7, notification)
    }

    private fun showPanel(): Boolean {
        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        val input = EditText(this).apply {
            hint = "مثلاً: شات بگیر"
            textSize = 16f
        }

        val status = TextView(this).apply {
            text = "سلام! من KING AI هستم 👑"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val close = Button(this).apply {
            text = "بستن"
            setOnClickListener {
                stopSelf()
            }
        }

        val send = Button(this).apply {
            text = "ارسال فرمان"

            setOnClickListener {
                val command = input.text.toString().trim()

                if (command.isEmpty()) {
                    status.text = "اول یک فرمان بنویس."
                    return@setOnClickListener
                }

                handleCommand(command, status)
            }
        }

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF17251F.toInt())

            addView(
                TextView(this@OverlayService).apply {
                    text = "👑 KING AI"
                    textSize = 20f
                    setTextColor(0xFFFFFFFF.toInt())
                }
            )

            addView(status)
            addView(input)
            addView(send)
            addView(close)
        }

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

        return try {
            windowManager?.addView(view, params)
            panel = view
            true
        } catch (e: Exception) {
            panel = null
            false
        }
    }

    private fun handleCommand(
        command: String,
        status: TextView
    ) {
        val normalized = command
            .replace("‌", "")
            .replace(" ", "")
            .lowercase()

        if (
            normalized.contains("شاتبگیر") ||
            normalized.contains("عکس‌بگیر") ||
            normalized.contains("عکس بگیر") ||
            normalized == "دوربین"
        ) {
            openCamera(status)
            return
        }

        status.text =
            "فرمان دریافت شد: $command\nاین فرمان هنوز به قابلیت مربوطه وصل نشده است."
    }

    private fun openCamera(status: TextView) {
        try {
            status.text = "در حال بازکردن دوربین KING AI..."

            val cameraIntent = Intent(
                this,
                CameraCaptureActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(cameraIntent)

        } catch (e: Exception) {
            status.text = "بازکردن دوربین ناموفق بود: ${e.message}"

            Toast.makeText(
                this,
                "خطا در بازکردن دوربین",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun refreshTile() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, KingTileService::class.java)
            )
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        running = false

        panel?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }

        panel = null
        refreshTile()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var running: Boolean = false
            private set
    }
}
