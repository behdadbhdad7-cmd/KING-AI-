package com.kingai.assistant

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var setupHost: LinearLayout
    private lateinit var progress: TextView
    private var firstShow = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Neon.BLACK))
        window.statusBarColor = Neon.BLACK
        window.navigationBarColor = Neon.BLACK
        try {
            setContentView(buildScreen())
        } catch (e: Exception) {
            // اگر ساخت صفحه خطا بدهد، به‌جای صفحه سفید، متن خطا نشان داده می‌شود.
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
        // بعد از برگشت از تنظیمات اندروید، وضعیت مجوزها دوباره بررسی می‌شود.
        if (::setupHost.isInitialized) refresh()
    }

    // ---------------------------------------------------------------- UI

    private fun lp(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun buildScreen(): View {
        val root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Neon.BLACK)
        root.addView(GridBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipChildren = false
        scroll.clipToPadding = false
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.clipChildren = false
        content.setPadding(dp(20), dp(44), dp(20), dp(36))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))

        // لوگوی تاج با ورود سینمایی (چرخش سه‌بعدی + بزرگ شدن)
        val crown = CrownView(this)
        content.addView(crown, LinearLayout.LayoutParams(dp(150), dp(135)))
        crown.cameraDistance = resources.displayMetrics.density * 9000f
        crown.alpha = 0f
        crown.rotationY = -90f
        crown.scaleX = 0.4f
        crown.scaleY = 0.4f
        crown.animate()
            .alpha(1f)
            .rotationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1000)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        val title = label(this, "KING AI", 38f, Neon.GREEN, true)
        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
        title.letterSpacing = 0.12f
        title.setShadowLayer(dp(14).toFloat(), 0f, 0f, Neon.GREEN)
        content.addView(title, lp(8))
        title.enter(300)

        val subtitle = label(this, "دستیار هوشمند اندرویدی", 14f, Neon.TEXT_DIM)
        subtitle.textAlignment = View.TEXT_ALIGNMENT_CENTER
        content.addView(subtitle, lp(2))
        subtitle.enter(420)

        // کارت معرفی بخش راه‌اندازی
        val header = neonCard(this)
        header.addView(label(this, "راه‌اندازی و فعال‌سازی KING AI", 18f, Neon.GREEN, true))
        progress = label(this, "", 13f, Neon.GREEN_SOFT, true)
        header.addView(progress, lp(8))
        header.addView(
            label(
                this,
                "هر مجوز فقط برای یک کار مشخص است. هیچ مجوزی مخفیانه فعال نمی‌شود و هر کدام را خودت از تنظیمات اندروید می‌دهی یا برمی‌داری.",
                13f, Neon.TEXT_DIM
            ), lp(6)
        )
        content.addView(header, lp(22))
        header.enter(540)

        // کارت‌های مجوزها در refresh() ساخته می‌شوند
        setupHost = LinearLayout(this)
        setupHost.orientation = LinearLayout.VERTICAL
        setupHost.clipChildren = false
        content.addView(setupHost, lp(0))

        // کنترل پنل
        val actions = neonCard(this)
        actions.addView(label(this, "کنترل پنل شناور", 16f, Neon.GREEN, true))
        val startBtn = NeonButton(this, "فعال‌سازی پنجره شناور")
        startBtn.setOnClickListener { startPanel() }
        actions.addView(startBtn, lp(12))
        val stopBtn = NeonButton(this, "بستن پنل شناور", true)
        stopBtn.setOnClickListener { stopPanel() }
        actions.addView(stopBtn, lp(14))
        content.addView(actions, lp(14))
        actions.enter(1100)

        val footer = label(
            this,
            "بعد از فعال‌سازی، کاشی KING AI را از پنل اعلان‌ها بزن تا پنل روی برنامه‌ی باز ظاهر شود.",
            12f, Neon.TEXT_DIM
        )
        footer.textAlignment = View.TEXT_ALIGNMENT_CENTER
        content.addView(footer, lp(18))
        footer.enter(1250)

        return root
    }

    private fun permCard(
        title: String,
        purpose: String,
        chipText: String,
        kind: ChipKind,
        buttonText: String?,
        onClick: (() -> Unit)?
    ): LinearLayout {
        val card = neonCard(this)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(label(this, title, 16f, 0xFFFFFFFF.toInt(), true), LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(chip(this, chipText, kind))
        card.addView(row)
        card.addView(label(this, purpose, 13f, Neon.TEXT_DIM), lp(8))
        if (buttonText != null && onClick != null) {
            val b = NeonButton(this, buttonText, true)
            b.setOnClickListener { onClick() }
            card.addView(b, lp(14))
        }
        return card
    }

    private fun refresh() {
        setupHost.removeAllViews()
        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = notifGranted()

        progress.text = if (overlayOk) {
            "مجوز ضروری فعال است؛ KING AI آماده‌ی نمایش پنل است."
        } else {
            "هنوز مجوز ضروری (نمایش روی سایر برنامه‌ها) فعال نشده است."
        }

        val cards = ArrayList<View>()

        cards.add(
            permCard(
                "نمایش روی سایر برنامه‌ها (ضروری)",
                "برای نمایش پنل شناور KING AI روی برنامه‌ای که باز است. بدون این مجوز پنل نمی‌تواند باز شود.",
                if (overlayOk) "فعال" else "غیرفعال",
                if (overlayOk) ChipKind.ON else ChipKind.OFF,
                if (overlayOk) "مدیریت مجوز" else "فعال‌سازی مجوز"
            ) { openOverlaySettings() }
        )

        cards.add(
            permCard(
                "اعلان‌ها (اختیاری)",
                "وقتی پنل فعال است، اندروید یک اعلان کوچک «KING AI فعال است» نشان می‌دهد. این مجوز فقط برای دیدن همان اعلان است؛ اگر ندهی هم پنل کار می‌کند.",
                if (notifOk) "فعال" else "غیرفعال",
                if (notifOk) ChipKind.ON else ChipKind.OFF,
                if (notifOk) "تنظیمات اعلان" else "درخواست مجوز"
            ) { onNotifClick() }
        )

        cards.add(
            permCard(
                "کاشی پنل اعلان‌ها",
                "برای باز کردن پنل با یک لمس از نوار اعلان‌ها. در اندروید ۱۳ می‌توانی با تأیید خودت کاشی را اضافه کنی؛ وگرنه آن را از ویرایش پنل سریع اضافه کن. اندروید به برنامه نمی‌گوید کاشی اضافه شده یا نه.",
                "اضافه‌کردن با تأیید تو",
                ChipKind.INFO,
                "افزودن کاشی به پنل سریع"
            ) { requestTile() }
        )

        cards.add(
            permCard(
                "دسترسی‌پذیری (Accessibility)",
                "فعلاً هیچ قابلیتی در KING AI به آن نیاز ندارد، پس درخواست نمی‌شود. اگر روزی قابلیتی مثل «بررسی صفحه» اضافه شود، اول توضیح کامل می‌دهیم و فقط با تأیید جداگانه‌ی تو فعال می‌شود.",
                "درخواست نمی‌شود",
                ChipKind.INFO,
                null,
                null
            )
        )

        for ((i, card) in cards.withIndex()) {
            setupHost.addView(card, lp(14))
            if (firstShow) card.enter(680L + i * 130L)
        }
        firstShow = false
    }

    // ---------------------------------------------------------- Actions

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun startPanel() {
        if (!Settings.canDrawOverlays(this)) {
            toast("اول مجوز «نمایش روی سایر برنامه‌ها» را فعال کن.")
            openOverlaySettings()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        toast("پنل KING AI باز شد.")
    }

    private fun stopPanel() {
        stopService(Intent(this, OverlayService::class.java))
        toast("پنل KING AI بسته شد.")
    }

    private fun notifGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(i)
    }

    private fun onNotifClick() {
        if (Build.VERSION.SDK_INT >= 33 && !notifGranted()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        } else {
            openNotificationSettings()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF && Build.VERSION.SDK_INT >= 33) {
            if (!notifGranted() && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // اندروید دیگر پنجره‌ی درخواست را نشان نمی‌دهد؛ کاربر را به تنظیمات می‌بریم.
                toast("برای فعال‌سازی اعلان‌ها، از تنظیمات اندروید اجازه بده.")
                openNotificationSettings()
            }
            if (::setupHost.isInitialized) refresh()
        }
    }

    private fun requestTile() {
        val manual = "کاشی را دستی اضافه کن: پنل اعلان‌ها را کامل پایین بکش، آیکن مداد (ویرایش) را بزن و KING AI را به پنل بکش."
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
                Icon.createWithResource(this, R.drawable.ic_king_ai),
                mainExecutor
            ) { result ->
                // نتیجه: 2 = اضافه شد، 1 = از قبل اضافه بود، بقیه = اضافه نشد
                runOnUiThread {
                    if (result == 1 || result == 2) {
                        toast("کاشی KING AI در پنل سریع قرار گرفت.")
                    } else {
                        toast(manual)
                    }
                }
            }
        } catch (e: Exception) {
            toast(manual)
        }
    }

    companion object {
        private const val REQ_NOTIF = 101
    }
}
