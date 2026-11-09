package com.kingai.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.NotificationManager

private class TileSpec(
    val icon: KIcon,
    val title: String,
    val command: String?
)

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val router by lazy { CommandRouter(this) }

    private lateinit var input: EditText
    private lateinit var resultView: TextView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var statusHost: LinearLayout
    private lateinit var historyHost: LinearLayout
    private lateinit var saveFileButton: NeonButton

    private var pendingFileContent: String? = null
    private var torchTile: QuickTile? = null
    private var built = false
    private var aiBusy = false

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) {
            toast("ذخیره فایل لغو شد.")
        } else {
            val content = pendingFileContent
            if (content.isNullOrBlank()) {
                toast("پاسخی برای ذخیره وجود ندارد.")
            } else {
                val saved = FileSaver.save(this, uri, content)
                toast(if (saved) "فایل ذخیره شد." else "ذخیره ناموفق بود.")
            }
        }
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = VoiceInput.extract(res.data)
            if (spoken.isNullOrBlank()) {
                toast("چیزی تشخیص داده نشد.")
            } else {
                input.setText(spoken)
                execute(spoken)
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (built) updateClock()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNeonWindow()

        try {
            setContentView(buildScreen())
            built = true
        } catch (e: Exception) {
            val tv = TextView(this)
            tv.setBackgroundColor(Neon.BLACK)
            tv.setTextColor(Neon.GREEN)
            tv.setPadding(dp(20), dp(60), dp(20), dp(20))
            tv.text = "خطا در ساخت صفحه:\n${e.javaClass.simpleName}: ${e.message}"
            setContentView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!built) return
        refreshStatus()
        refreshHistory()
        refreshTorchTile()
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun hRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun buildScreen(): android.view.View {
        val (root, content) = neonScreen(this)

        val header = hRow().apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val crown = CrownView(this)
        header.addView(crown, LinearLayout.LayoutParams(dp(74), dp(68)))
        crown.cameraDistance = resources.displayMetrics.density * 9000f
        crown.alpha = 0f
        crown.rotationY = -90f
        crown.scaleX = 0.4f
        crown.scaleY = 0.4f
        crown.animate().alpha(1f).rotationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(900)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = label(this, "KING AI", 28f, Neon.GREEN, true)
        title.letterSpacing = 0.1f
        title.setShadowLayer(dp(12).toFloat(), 0f, 0f, Neon.GREEN)
        titles.addView(title)
        titles.addView(label(this, "دستیار هوشمند اندرویدی", 13f, Neon.TEXT_DIM))
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
            marginStart = dp(10)
        })

        val histBtn = GlassIconButton(this, KIcon.HISTORY, false, "تاریخچه")
        histBtn.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        header.addView(histBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val setBtn = GlassIconButton(this, KIcon.SETTINGS, false, "تنظیمات")
        setBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        header.addView(setBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginStart = dp(8)
        })
        content.addView(header, vlp(0))

        val clockCard = neonCard(this)
        val clockRow = hRow().apply { gravity = Gravity.CENTER_VERTICAL }
        timeView = label(this, "", 36f, Color.WHITE, true)
        timeView.setShadowLayer(dp(10).toFloat(), 0f, 0f, Neon.GREEN)
        clockRow.addView(timeView)
        dateView = label(this, "", 14f, Neon.GREEN_SOFT, true)
        clockRow.addView(dateView, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
            marginStart = dp(16)
        })
        clockCard.addView(clockRow)
        content.addView(clockCard, vlp(18))
        clockCard.enter(150)

        val statusCard = neonCard(this)
        statusCard.addView(label(this, "وضعیت KING AI", 16f, Neon.GREEN, true))
        statusHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusCard.addView(statusHost, vlp(0))
        content.addView(statusCard, vlp(14))
        statusCard.enter(260)

        val cmdCard = neonCard(this)
        cmdCard.addView(label(this, "فرمان بده", 16f, Neon.GREEN, true))
        val row = hRow().apply { gravity = Gravity.CENTER_VERTICAL }

        input = neonInput(this, "مثلاً: شات بگیر")
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else false
        }
        row.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))

        val mic = GlassIconButton(this, KIcon.MIC, false, "فرمان صوتی")
        mic.setOnClickListener { startVoice() }
        row.addView(mic, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(8)
        })

        val send = GlassIconButton(this, KIcon.SEND, true, "ارسال فرمان")
        send.setOnClickListener { submit() }
        row.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(8)
        })

        cmdCard.addView(row, vlp(12))
        resultView = label(
            this,
            "آماده دریافت فرمان. «راهنما» را بنویس.",
            14f,
            Neon.TEXT_DIM
        )
        cmdCard.addView(resultView, vlp(12))

        saveFileButton = NeonButton(this, "ذخیره پاسخ در فایل", true)
        saveFileButton.isEnabled = false
        saveFileButton.setOnClickListener {
            if (pendingFileContent.isNullOrBlank()) {
                toast("هنوز پاسخی برای ذخیره وجود ندارد.")
            } else {
                createFileLauncher.launch("KING-AI-Response.txt")
            }
        }
        cmdCard.addView(saveFileButton, vlp(12))
        content.addView(cmdCard, vlp(14))
        cmdCard.enter(370)

        val quickCard = neonCard(this)
        quickCard.addView(label(this, "دسترسی سریع", 16f, Neon.GREEN, true))
        val specs = listOf(
            TileSpec(KIcon.TORCH, "چراغ‌قوه", "چراغ قوه"),
            TileSpec(KIcon.CAMERA, "شات بگیر", "شات بگیر"),
            TileSpec(KIcon.GALLERY, "گالری", "گالری"),
            TileSpec(KIcon.WIFI, "Wi-Fi", "وای فای"),
            TileSpec(KIcon.BLUETOOTH, "Bluetooth", "بلوتوث"),
            TileSpec(KIcon.HISTORY, "تاریخچه", null)
        )
        val row1 = hRow()
        val row2 = hRow()

        for ((i, spec) in specs.withIndex()) {
            val tile = QuickTile(this, spec.icon, spec.title)
            tile.setOnClickListener {
                if (spec.command == null) {
                    startActivity(Intent(this, HistoryActivity::class.java))
                } else execute(spec.command)
            }
            if (spec.icon == KIcon.TORCH) torchTile = tile
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
            if (i < 3) row1.addView(tile, lp) else row2.addView(tile, lp)
        }
        quickCard.addView(row1, vlp(12))
        quickCard.addView(row2, vlp(10))
        content.addView(quickCard, vlp(14))
        quickCard.enter(480)

        // مرکز تلگرام
        val telegramCard = neonCard(this)
        telegramCard.addView(
            label(this, "مرکز تلگرام", 18f, Neon.GREEN, true)
        )
        telegramCard.addView(
            label(
                this,
                "مشاهده اعلان‌های تلگرام در داخل KING AI",
                13f,
                Neon.TEXT_DIM
            ),
            vlp(8)
        )

        val accessButton = NeonButton(this, "فعال‌سازی دسترسی اعلان‌ها", true)
        accessButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                toast("باز کردن تنظیمات اعلان‌ها ناموفق بود.")
            }
        }
        telegramCard.addView(accessButton, vlp(12))

        val monitorButton = NeonButton(this, "روشن کردن نظارت تلگرام")
        monitorButton.setOnClickListener {
            if (!hasTelegramNotificationAccess()) {
                toast("ابتدا دسترسی اعلان‌های KING AI را فعال کن.")
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                TelegramNotificationListener.setEnabled(this, true)
                toast("نظارت اعلان‌های تلگرام روشن شد.")
                refreshStatus()
            }
        }
        telegramCard.addView(monitorButton, vlp(10))

        val offButton = NeonButton(this, "خاموش کردن نظارت", true)
        offButton.setOnClickListener {
            TelegramNotificationListener.setEnabled(this, false)
            toast("نظارت تلگرام خاموش شد.")
            refreshStatus()
        }
        telegramCard.addView(offButton, vlp(10))

        val inboxButton = NeonButton(this, "مشاهده پیام‌های تلگرام", true)
        inboxButton.setOnClickListener {
            startActivity(Intent(this, TelegramInboxActivity::class.java))
        }
        telegramCard.addView(inboxButton, vlp(10))

        content.addView(telegramCard, vlp(14))
        telegramCard.enter(510)

        val securityCard = neonCard(this)
        securityCard.addView(label(this, "🛡 مرکز امنیت", 18f, Neon.GREEN, true))
        securityCard.addView(
            label(this, "مدیریت مجوزها و دسترسی‌های KING AI", 13f, Neon.TEXT_DIM),
            vlp(8)
        )
        val securityButton = NeonButton(this, "باز کردن تنظیمات امنیتی", true)
        securityButton.setOnClickListener {
            try {
                startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                ))
            } catch (_: Exception) {
                toast("باز کردن تنظیمات ناموفق بود.")
            }
        }
        securityCard.addView(securityButton, vlp(12))
        content.addView(securityCard, vlp(14))
        securityCard.enter(520)

        val funCard = neonCard(this)
        funCard.addView(label(this, "سرگرمی", 18f, Neon.GREEN, true))
        funCard.addView(
            label(this, "بازی آفلاین KING RUN", 14f, Neon.TEXT_DIM),
            vlp(8)
        )
        val gameButton = NeonButton(this, "▶  شروع بازی KING RUN", true)
        gameButton.setOnClickListener {
            try {
                startActivity(Intent(this, KingRunActivity::class.java))
            } catch (_: Exception) {
                toast("باز کردن بازی ناموفق بود.")
            }
        }
        funCard.addView(gameButton, vlp(12))
        content.addView(funCard, vlp(14))
        funCard.enter(540)

        val histCard = neonCard(this)
        histCard.addView(label(this, "آخرین فرمان‌ها", 16f, Neon.GREEN, true))
        historyHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        histCard.addView(historyHost, vlp(8))
        val allBtn = NeonButton(this, "نمایش همه و پاک‌کردن", true)
        allBtn.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        histCard.addView(allBtn, vlp(14))
        content.addView(histCard, vlp(14))
        histCard.enter(590)

        val actions = neonCard(this)
        actions.addView(label(this, "کنترل پنل شناور", 16f, Neon.GREEN, true))
        val startBtn = NeonButton(this, "فعال‌سازی پنجره شناور")
        startBtn.setOnClickListener { startPanel() }
        actions.addView(startBtn, vlp(12))
        val stopBtn = NeonButton(this, "بستن پنل شناور", true)
        stopBtn.setOnClickListener { stopPanel() }
        actions.addView(stopBtn, vlp(14))
        content.addView(actions, vlp(14))
        actions.enter(700)

        val footer = label(
            this,
            "بعد از فعال‌سازی، کاشی KING AI را از پنل اعلان‌ها بزن تا پنل روی برنامه باز شود.",
            12f,
            Neon.TEXT_DIM
        )
        footer.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        content.addView(footer, vlp(18))

        return root
    }

    private fun hasTelegramNotificationAccess(): Boolean {
    val enabled = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    return enabled.contains(packageName)
    }

    private fun statusRow(
        title: String,
        chipText: String,
        kind: ChipKind
    ): LinearLayout {
        val row = hRow().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(this, title, 14f, Color.WHITE),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(chip(this, chipText, kind))
        return row
    }

    private fun isApiConfigured(): Boolean {
        return ApiConfig.hasToken(this) &&
            ApiConfig.getBaseUrl(this).isNotBlank() &&
            ApiConfig.getModel(this).isNotBlank()
    }

    private fun refreshStatus() {
        statusHost.removeAllViews()
        val overlayOk = Settings.canDrawOverlays(this)
        val panelOpen = OverlayService.running
        val aiReady = isApiConfigured()
        val telegramAccess = hasTelegramNotificationAccess()
        val telegramEnabled = TelegramNotificationListener.isEnabled(this)

        statusHost.addView(
            statusRow("فرمان‌های محلی (آفلاین)", "فعال", ChipKind.ON),
            vlp(12)
        )
        statusHost.addView(
            statusRow("مغز هوشمند (API)",
                if (aiReady) "آماده" else "غیرفعال",
                if (aiReady) ChipKind.ON else ChipKind.OFF),
            vlp(10)
        )
        statusHost.addView(
            statusRow("پنل شناور",
                if (panelOpen) "باز" else "بسته",
                if (panelOpen) ChipKind.ON else ChipKind.INFO),
            vlp(10)
        )
        statusHost.addView(
            statusRow("نمایش روی سایر برنامه‌ها",
                if (overlayOk) "فعال" else "غیرفعال",
                if (overlayOk) ChipKind.ON else ChipKind.OFF),
            vlp(10)
        )
        statusHost.addView(
            statusRow("دسترسی اعلان تلگرام",
                if (telegramAccess) "فعال" else "غیرفعال",
                if (telegramAccess) ChipKind.ON else ChipKind.OFF),
            vlp(10)
        )
        statusHost.addView(
            statusRow("نظارت تلگرام",
                if (telegramEnabled) "روشن" else "خاموش",
                if (telegramEnabled) ChipKind.ON else ChipKind.INFO),
            vlp(10)
        )
        statusHost.addView(
            label(this,
                if (aiReady) "اتصال API تنظیم شده است."
                else "برای فعال‌سازی مغز هوشمند، تنظیمات API را کامل کن.",
                12f, Neon.TEXT_DIM),
            vlp(12)
        )
        if (!overlayOk) {
            val b = NeonButton(this, "فعال‌سازی مجوز ضروری", true)
            b.setOnClickListener { openOverlaySettings() }
            statusHost.addView(b, vlp(14))
        }
    }

    private fun refreshHistory() {
        historyHost.removeAllViews()
        val entries = HistoryStore.list(this).take(3)
        if (entries.isEmpty()) {
            val msg = if (Prefs.historyEnabled(this))
                "هنوز فرمانی اجرا نشده است."
            else "ذخیره تاریخچه در تنظیمات خاموش است."
            historyHost.addView(label(this, msg, 13f, Neon.TEXT_DIM))
            return
        }
        for ((i, e) in entries.withIndex()) {
            val cmd = label(this, e.command, 14f, Color.WHITE, true)
            cmd.maxLines = 1
            cmd.ellipsize = TextUtils.TruncateAt.END
            historyHost.addView(cmd, vlp(if (i == 0) 0 else 10))
            val res = label(this, e.result, 12f, Neon.TEXT_DIM)
            res.maxLines = 2
            res.ellipsize = TextUtils.TruncateAt.END
            historyHost.addView(res, vlp(2))
        }
    }

    private fun refreshTorchTile() {
        val tile = torchTile ?: return
        tile.setActive(TorchController.isOn)
        tile.setTitle(if (TorchController.isOn) "چراغ‌قوه روشن" else "چراغ‌قوه")
    }

    private fun updateClock() {
        val now = System.currentTimeMillis()
        timeView.text = clockTime(now)
        dateView.text = persianDate(now, true)
    }

    private fun submit() {
        if (aiBusy) {
            toast("هوش مصنوعی هنوز در حال پاسخ‌گویی است.")
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            resultView.setTextColor(Neon.AMBER)
            resultView.text = "اول یک فرمان بنویس."
            return
        }
        execute(text)
        input.setText("")
    }

    private fun isUnknownCommand(message: String): Boolean {
        return message.startsWith("فرمان «") &&
            message.contains("را نمی‌شناسم")
    }

    private fun execute(text: String) {
        val res = router.handle(text)
        if (res.success || !isUnknownCommand(res.message)) {
            resultView.setTextColor(if (res.success) Neon.GREEN_SOFT else Neon.AMBER)
            resultView.text = res.message
            HistoryStore.add(this, text, res.message)
            refreshHistory()
            refreshTorchTile()
            hideKeyboard()
            return
        }
        if (!isApiConfigured()) {
            resultView.setTextColor(Neon.AMBER)
            resultView.text = "مغز هوشمند تنظیم نشده است. تنظیمات API را بررسی کن."
            return
        }
        askAi(text)
    }

    private fun askAi(question: String) {
        if (aiBusy) return
        aiBusy = true
        pendingFileContent = null
        saveFileButton.isEnabled = false
        resultView.setTextColor(Neon.GREEN)
        resultView.text
                resultView.text = "در حال فکر کردن..."

        hideKeyboard()

        ApiClient.sendMessage(this, question) { response ->
            if (isFinishing || isDestroyed) return@sendMessage

            aiBusy = false

            resultView.setTextColor(
                if (response.success) Neon.GREEN_SOFT
                else Neon.AMBER
            )

            resultView.text = response.message

            pendingFileContent =
                if (response.success) response.message else null

            saveFileButton.isEnabled = response.success

            HistoryStore.add(this, question, response.message)
            refreshHistory()
            refreshStatus()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE)
            as InputMethodManager

        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun startVoice() {
        try {
            voiceLauncher.launch(VoiceInput.createIntent())
        } catch (_: Exception) {
            toast("تشخیص گفتار در دسترس نیست.")
        }
    }

    private fun startPanel() {
        if (!Settings.canDrawOverlays(this)) {
            toast("ابتدا مجوز پنل شناور را فعال کن.")
            openOverlaySettings()
            return
        }

        startService(Intent(this, OverlayService::class.java))
        toast("پنل شناور فعال شد.")
        refreshStatus()
    }

    private fun stopPanel() {
        stopService(Intent(this, OverlayService::class.java))
        toast("پنل شناور بسته شد.")
        refreshStatus()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            toast("باز کردن تنظیمات ناموفق بود.")
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(
            this,
            message,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
