package com.kingai.assistant

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings

/** کارهای سیستمی که با باز کردن صفحه‌های رسمی اندروید انجام می‌شوند. */
object SystemActions {

    private fun launch(ctx: Context, intent: Intent): Boolean =
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }

    /** همان مسیر قبلی و سالم دوربین KING AI (صفحه‌ی زنده + ذخیره در Pictures/KING-AI). */
    fun openCamera(ctx: Context): CommandResult =
        if (launch(ctx, Intent(ctx, CameraCaptureActivity::class.java))) {
            CommandResult("در حال بازکردن دوربین KING AI...")
        } else {
            CommandResult("بازکردن دوربین ناموفق بود.", false)
        }

    fun openGallery(ctx: Context): CommandResult {
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        if (launch(ctx, main)) return CommandResult("گالری باز شد.")

        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        return if (launch(ctx, view)) {
            CommandResult("گالری باز شد.")
        } else {
            CommandResult("برنامه‌ی گالری پیدا نشد.", false)
        }
    }

    fun openWifi(ctx: Context): CommandResult =
        if (launch(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))) {
            CommandResult("تنظیمات Wi-Fi باز شد.")
        } else {
            CommandResult("باز کردن تنظیمات Wi-Fi ناموفق بود.", false)
        }

    fun openBluetooth(ctx: Context): CommandResult =
        if (launch(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
            CommandResult("تنظیمات Bluetooth باز شد.")
        } else {
            CommandResult("باز کردن تنظیمات Bluetooth ناموفق بود.", false)
        }
}
