
package com.kingai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var status: TextView? = null
    private var input: EditText? = null
    private var content: LinearLayout? = null
    private var minimized = false

    private val router by lazy { CommandRouter(this) }

    private val green = 0xFF39FF14.toInt()
    private val dark = 0xFF06140B.toInt()
    private val white = Color.WHITE
    private val dim = 0xFFB8D6C0.toInt()

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
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channelId = "king_ai_running"

        val manager =
            getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "KING AI",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        val notification = builder
            .setContentTitle("KING AI فعال است")
            .setContentText("پنل شناور آماده استفاده است")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(7, notification)
    }

    private fun panelBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(dark)
            setStroke(dp(1), green)
        }
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun makeButton(
        value: String,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(white)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFF12351B.toInt())
                setStroke(dp(1), green)
            }
            isClickable = true
            setOnClickListener { action() }
        }
    }

    private fun showPanel(): Boolean {
        return try {
            windowManager =
                getSystemService(WINDOW_SERVICE) as WindowManager

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(12))
                background = panelBackground()
                elevation = dp(12).toFloat()
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val title = makeText(
                "👑 KING AI",
                19f,
                green
            ).apply {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            header.addView(
                title,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val minimize = makeButton("−") {
                toggleMinimize()
            }

            header.addView(
                minimize,
                LinearLayout.LayoutParams(dp(44), dp(42))
            )

            val close = makeButton("×") {
                stopSelf()
            }

            header.addView(
                close,
                LinearLayout.LayoutParams(dp(44), dp(42)).apply {
                    marginStart = dp(6)
                }
            )

            root.addView(header)

            // نوار عنوان برای جابه‌جایی پنل
            header.setOnTouchListener(
                createDragListener()
            )

            content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            status = makeText(
                "سلام! من KING AI هستم.\nمنتظر فرمان تو هستم.",
                14f,
                dim
            )

            content?.addView(status)

            input = EditText(this).apply {
                hint = "مثلاً: تلگرام را باز کن"
                textSize = 14f
                setTextColor(white)
                setHintTextColor(dim)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setSingleLine(true)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0xFF020904.toInt())
                    setStroke(dp(1), green)
                }
            }

            content?.addView(
                input,
                LinearLayout.LayoutParams(
                    -1,
                    dp(50)
                ).apply {
                    topMargin = dp(8)
                }
            )

            val send = makeButton("ارسال فرمان") {
                executeCommand()
            }

            content?.addView(
                send,
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )

            val quick = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            quick.addView(
                makeButton("گالری") {
                    executeQuick("گالری")
                },
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            quick.addView(
                makeButton("چراغ") {
                    executeQuick("چراغ قوه")
                },
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(6)
                }
            )

            quick.addView(
                makeButton("دوربین") {
                    executeQuick("دوربین")
                },
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(6)
                }
            )

            content?.addView(
                quick,
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(10)
                }
            )

            root.addView(content)

            val windowParams = WindowManager.LayoutParams(
                minOf(
                    resources.displayMetrics.widthPixels - dp(24),
                    dp(380)
                ),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(100)
            }

            windowManager?.addView(root, windowParams)

            panel = root
            params = windowParams

            true
        } catch (_: Exception) {
            panel = null
            false
        }
    }

    private fun createDragListener(): View.OnTouchListener {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        return View.OnTouchListener { _, event ->
            val p = params ?: return@OnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    p.x = startX +
                        (event.rawX - touchX).toInt()

                    p.y = startY +
                        (event.rawY - touchY).toInt()

                    try {
                        windowManager?.updateViewLayout(
                            panel,
                            p
                        )
                    } catch (_: Exception) {
                    }

                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }
    }

    private fun toggleMinimize() {
        val body = content ?: return

        minimized = !minimized

        body.visibility =
            if (minimized) View.GONE else View.VISIBLE

        panel?.let {
            it.requestLayout()
        }
    }

    private fun executeCommand() {
        val command = input?.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (command.isEmpty()) {
            status?.text = "اول یک فرمان بنویس."
            return
        }

        executeQuick(command)
        input?.setText("")
    }

    private fun executeQuick(command: String) {
        try {
            status?.text = "در حال اجرای فرمان..."

            val result = router.handle(command)

            status?.text = result.message

            status?.setTextColor(
                if (result.success) green else 0xFFFFC107.toInt()
            )

            HistoryStore.add(
                this,
                command,
                result.message
            )

        } catch (e: Exception) {
            status?.text = "خطا در اجرای فرمان."
            status?.setTextColor(0xFFFF5252.toInt())
        }
    }

    private fun refreshTile() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(
                    this,
                    KingTileService::class.java
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        running = false

        panel?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }

        panel = null
        content = null
        status = null
        input = null
        params = null

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
