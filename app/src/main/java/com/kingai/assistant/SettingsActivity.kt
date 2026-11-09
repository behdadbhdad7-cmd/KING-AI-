package com.kingai.assistant

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** تنظیمات KING AI */
class SettingsActivity : ComponentActivity() {

    private lateinit var permHost: LinearLayout
    private lateinit var progress: TextView
    private lateinit var historyInfo: TextView
    private lateinit var historyToggle: NeonButton
    private var built = false

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            toast("برای فعال‌سازی اعلان‌ها، از تنظیمات اندروید اجازه بده.")
            openNotificationSettings()
        }
        if (built) refresh()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        ) {
            toast("برای فعال‌سازی دوربین، از تنظیمات برنامه اجازه بده.")
            openAppDetailsSettings()
        }
        if (built) refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNeonWindow()

        val (root, content) = neonScreen(this)
        content.addView(topBar(this, "تنظیمات KING AI"), vlp(0))

        val header = neonCard(this)
        header.addView(
            label(this, "راه‌اندازی و وضعیت دسترسی‌ها", 18f, Neon.GREEN, true)
        )

        progress = label(this, "", 13f, Neon.GREEN_SOFT, true)
        header.addView(progress, vlp(8))

        header.addView(
            label(
                this,
                "هر مجوز فقط برای یک کار مشخص است. هیچ مجوزی مخفیانه فعال نمی‌شود و هر کدام را خودت از تنظیمات اندروید می‌دهی یا برمی‌داری.",
                13f,
                Neon.TEXT_DIM
            ),
            vlp(6)
        )

        content.addView(header, vlp(18))

        permHost = LinearLayout(this)
        permHost.orientation = LinearLayout.VERTICAL
        content.addView(permHost, vlp(0))

        // تاریخچه
        val historyCard = neonCard(this)
        historyCard.addView(
            label(this, "تاریخچه‌ی فرمان‌ها", 16f, Neon.GREEN, true)
        )

        historyCard.addView(
            label(
                this,
                "فرمان‌ها فقط روی همین گوشی ذخیره می‌شوند و به هیچ جا ارسال نمی‌شوند. حداکثر ۱۰۰ فرمان نگه داشته می‌شود.",
                13f,
                Neon.TEXT_DIM
            ),
            vlp(8)
        )

        historyInfo = label(this, "", 13f, Neon.GREEN_SOFT, true)
        historyCard.addView(historyInfo, vlp(8))

        historyToggle = NeonButton(this, "", true)
        historyToggle.setOnClickListener {
            Prefs.setHistoryEnabled(
                this,
                !Prefs.historyEnabled(this)
            )
            refreshHistoryCard()
        }
        historyCard.addView(historyToggle, vlp(14))

        val clearBtn = NeonButton(this, "پاک‌کردن تاریخچه", true)
        clearBtn.setOnClickListener {
            if (HistoryStore.count(this) == 0) {
                toast("تاریخچه از قبل خالی است.")
            } else {
                confirmClearHistory {
                    refreshHistoryCard()
                }
            }
        }

        historyCard.addView(clearBtn, vlp(14))
        content.addView(historyCard, vlp(14))

        // مغز هوشمند و تنظیمات API
        val brain = neonCard(this)

        val configured = ApiConfig.hasToken(this) &&
            ApiConfig.getBaseUrl(this).isNotBlank() &&
            ApiConfig.getModel(this).isNotBlank()

        val brainRow = LinearLayout(this)
        brainRow.orientation = LinearLayout.HORIZONTAL
        brainRow.gravity = android.view.Gravity.CENTER_VERTICAL

        brainRow.addView(
            label(
                this,
                "مغز هوشمند (API)",
                16f,
                0xFFFFFFFF.toInt(),
                true
            ),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )

        brainRow.addView(
            chip(
                this,
                if (configured) "تنظیم شده" else "تنظیم نشده",
                if (configured) ChipKind.ON else ChipKind.OFF
            )
        )

        brain.addView(brainRow)

        brain.addView(
            label(
                this,
                if (configured)
                    "اطلاعات اتصال API ذخیره شده است. برای آزمایش اتصال وارد تنظیمات شو."
                else
                    "برای فعال‌کردن پاسخ‌گویی هوشمند، آدرس سرویس، نام مدل و کلید API خودت را وارد کن.",
                13f,
                Neon.TEXT_DIM
            ),
            vlp(8)
        )

        val apiButton = NeonButton(this, "تنظیم و اتصال هوش مصنوعی")
        apiButton.setOnClickListener {
            startActivity(
                Intent(this, ApiSettingsActivity::class.java)
            )
        }

        brain.addView(apiButton, vlp(14))
        content.addView(brain, vlp(14))

        // درباره
        val about = neonCard(this)
        about.addView(
            label(this, "درباره", 16f, Neon.GREEN, true)
        )

        about.addView(
            label(
                this,
                "KING AI  •  نسخه‌ی ${versionName()}",
                13f,
                Neon.TEXT_DIM
            ),
            vlp(8)
        )

        content.addView(about, vlp(14))

        setContentView(root)
        built = true

        refresh()
        refreshHistoryCard()
    }

    override fun onResume() {
        super.onResume()

        if (built) {
            refresh()
            refreshHistoryCard()
        }
    }

    private fun notifGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun versionName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0)
                .versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

    private fun refreshHistoryCard() {
        val enabled = Prefs.historyEnabled(this)

        historyInfo.text =
            "تعداد فرمان‌های ثبت‌شده: " +
                HistoryStore.count(this).toString().fa()

        historyToggle.setLabel(
            if (enabled)
                "ذخیره‌ی تاریخچه: روشن"
            else
                "ذخیره‌ی تاریخچه: خاموش"
        )
    }

    private fun refresh() {
        permHost.removeAllViews()

        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = notifGranted()
        val camOk = cameraGranted()
        val voiceOk = VoiceInput.isAvailable(this)

        progress.text = if (overlayOk) {
            "مجوز ضروری فعال است؛ KING AI آماده‌ی نمایش پنل است."
        } else {
            "هنوز مجوز ضروری (نمایش روی سایر برنامه‌ها) فعال نشده است."
        }

        val cards = ArrayList<android.view.View>()

        cards.add(
            permCard(
                this,
                "نمایش روی سایر برنامه‌ها (ضروری)",
                "برای نمایش پنل شناور KING AI روی برنامه‌ای که باز است.",
                if (overlayOk) "فعال" else "غیرفعال",
                if (overlayOk) ChipKind.ON else ChipKind.OFF,
                if (overlayOk) "مدیریت مجوز" else "فعال‌سازی مجوز"
            ) {
                openOverlaySettings()
            }
        )

        cards.add(
            permCard(
                this,
                "دوربین",
                "فقط برای دستور «شات بگیر». دوربین تنها بعد از فرمان خودت باز می‌شود.",
                if (camOk) "فعال" else "غیرفعال",
                if (camOk) ChipKind.ON else ChipKind.OFF,
                if (camOk) "تنظیمات برنامه" else "درخواست مجوز"
            ) {
                if (camOk) {
                    openAppDetailsSettings()
                } else {
                    cameraLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )

        cards.add(
            permCard(
                this,
                "اعلان‌ها (اختیاری)",
                "برای نمایش اعلان وضعیت KING AI.",
                if (notifOk) "فعال" else "غیرفعال",
                if (notifOk) ChipKind.ON else ChipKind.OFF,
                if (notifOk) "تنظیمات اعلان" else "درخواست مجوز"
            ) {
                onNotifClick()
            }
        )

        cards.add(
            permCard(
                this,
                "ورودی صوتی فارسی",
                "با لمس میکروفون، پنجره تشخیص گفتار اندروید باز می‌شود.",
                if (voiceOk) "در دسترس" else "در دسترس نیست",
                if (voiceOk) ChipKind.ON else ChipKind.OFF,
                null,
                null
            )
        )

        cards.add(
            permCard(
                this,
                "کاشی پنل اعلان‌ها",
                "برای بازکردن پنل KING AI از پنل سریع اندروید.",
                "اضافه‌کردن با تأیید تو",
                ChipKind.INFO,
                "افزودن کاشی به پنل سریع"
            ) {
                requestTile()
            }
        )

        cards.add(
            permCard(
                this,
                "دسترسی‌پذیری (Accessibility)",
                "فعلاً هیچ قابلیتی به این دسترسی نیاز ندارد.",
                "درخواست نمی‌شود",
                ChipKind.INFO,
                null,
                null
            )
        )

        for (card in cards) {
            permHost.addView(card, vlp(14))
        }
    }

    private fun onNotifClick() {
        if (Build.VERSION.SDK_INT >= 33 && !notifGranted()) {
            notifLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            openNotificationSettings()
        }
    }

    private fun requestTile() {
        val manual =
            "پنل اعلان‌ها را پایین بکش، آیکن مداد را بزن و KING AI را اضافه کن."

        if (Build.VERSION.SDK_INT < 33) {
            toast(manual)
            return
        }

        try {
            val sbm = getSystemService(StatusBarManager::class.java)

            if (sbm == null) {
                toast(manual)
                return
            }

            sbm.requestAddTileService(
                ComponentName(this, KingTileService::class.java),
                "KING AI",
                Icon.createWithResource(
                    this,
                    R.drawable.ic_king_ai
                ),
                mainExecutor
            ) { result ->
                runOnUiThread {
                    if (result == 1 || result == 2) {
                        toast("کاشی KING AI در پنل سریع قرار گرفت.")
                    } else {
                        toast(manual)
                    }
                }
            }
        } catch (_: Exception) {
            toast(manual)
        }
    }
}
