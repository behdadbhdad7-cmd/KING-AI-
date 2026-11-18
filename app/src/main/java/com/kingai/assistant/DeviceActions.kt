package com.kingai.assistant

import android.content.Context
import android.content.Intent

/** فرمان‌های گوشی با فهرست محدود و از مسیر رسمی اندروید. */
object DeviceActions {
    fun setTimer(context: Context, raw: String): CommandResult {
        val normalized = FaText.normalize(raw)
            .replace('۰', '0').replace('۱', '1').replace('۲', '2')
            .replace('۳', '3').replace('۴', '4').replace('۵', '5')
            .replace('۶', '6').replace('۷', '7').replace('۸', '8')
            .replace('۹', '9')
        val amount = Regex("\\d+").find(normalized)?.value?.toIntOrNull()
            ?: return CommandResult("مدت تایمر را مشخص کن؛ مثلاً «تایمر ۵ دقیقه».", false)
        val seconds = when {
            normalized.contains("ساعت") -> amount * 3600
            normalized.contains("ثانیه") -> amount
            else -> amount * 60
        }.coerceIn(1, 86400)
        return try {
            val intent = Intent("android.intent.action.SET_TIMER").apply {
                putExtra("android.intent.extra.alarm.LENGTH", seconds)
                putExtra("android.intent.extra.alarm.MESSAGE", "تایمر KING AI")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult("صفحه‌ی تنظیم تایمر اندروید باز شد؛ زمان را بررسی و شروع کن.")
        } catch (_: Exception) {
            CommandResult("برنامه‌ی ساعت برای تنظیم تایمر پیدا نشد.", false)
        }
    }
}
