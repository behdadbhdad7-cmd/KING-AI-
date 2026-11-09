package com.kingai.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * صفحه‌ی نامرئی و گذرا که KingTileService باز می‌کند.
 * چون این صفحه برای لحظه‌ای در پیش‌زمینه است، اندروید اجازه‌ی شروع سرویس پنل را می‌دهد
 * و باز کردن آن با startActivityAndCollapse پرده‌ی اعلان‌ها را هم می‌بندد.
 */
class LaunchPanelActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
