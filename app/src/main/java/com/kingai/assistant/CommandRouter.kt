package com.kingai.assistant

import android.content.Context

data class CommandResult(val message: String, val success: Boolean = true)

/**
 * تشخیص فرمان‌های فارسی و هدایت آن‌ها به عمل مناسب.
 * فقط از Context برنامه استفاده می‌کند، پس نشت Activity ندارد.
 */
class CommandRouter(context: Context) {

    private val ctx: Context = context.applicationContext

    fun handle(raw: String): CommandResult {
        val text = raw.trim()
        if (text.isEmpty()) return CommandResult("اول یک فرمان بنویس.", false)

        val n = FaText.normalize(text)

        return when {
            has(n, "راهنما", "دستورات", "help") -> help()

            has(n, "اسکرین") -> CommandResult(
                "گرفتن اسکرین‌شات پشتیبانی نمی‌شود. «شات بگیر» از دوربین عکس می‌گیرد.",
                false
            )

            has(n, "چراغقوه", "چراغ", "فلش", "فلاش", "flash", "torch") -> torch(n)

            has(n, "وایفای", "wifi") -> SystemActions.openWifi(ctx)

            has(n, "بلوتوث", "بلوتوس", "bluetooth") -> SystemActions.openBluetooth(ctx)

            has(n, "گالری", "آلبوم", "عکسها", "تصاویر", "gallery") &&
                !has(n, "بگیر", "بردار") -> SystemActions.openGallery(ctx)

            isCamera(n) -> SystemActions.openCamera(ctx)

            isOpenApp(n) -> openApp(text)

            else -> CommandResult(
                "فرمان «$text» را نمی‌شناسم. «راهنما» را بنویس تا فرمان‌های موجود را ببینی. " +
                    "(مغز هوشمند هنوز به API متصل نیست.)",
                false
            )
        }
    }

    private fun has(n: String, vararg words: String): Boolean = words.any { n.contains(it) }

    private fun isCamera(n: String): Boolean =
        has(n, "دوربین", "camera") ||
            (has(n, "عکس", "شات", "تصویر", "سلفی") && has(n, "بگیر", "بردار", "بزن"))

    private fun isOpenApp(n: String): Boolean =
        (n.contains("باز") && (n.contains("کن") || n.contains("کردن"))) ||
            n.contains("اجرا") ||
            n.startsWith("open") ||
            n.startsWith("launch") ||
            n.startsWith("برنامه") ||
            n.startsWith("اپ")

    private fun torch(n: String): CommandResult {
        val off = has(n, "خاموش", "غیرفعال", "قطع")
        val on = !off && has(n, "روشن", "فعال", "وصل")
        val want = when {
            off -> false
            on -> true
            else -> !TorchController.isOn
        }
        return TorchController.set(ctx, want)
    }

    private fun openApp(text: String): CommandResult {
        val name = FaText.extractAppName(text)
        if (name.isBlank()) {
            return CommandResult("کدام برنامه را باز کنم؟ مثال: «باز کن واتساپ»", false)
        }
        return AppLauncher.open(ctx, name)
    }

    private fun help(): CommandResult = CommandResult(
        "فرمان‌های فعلی:\n" +
            "• شات بگیر\n" +
            "• چراغ\u200cقوه روشن / خاموش\n" +
            "• گالری\n" +
            "• وای\u200cفای / بلوتوث\n" +
            "• باز کن «نام برنامه»\n\n" +
            "پاسخ‌گویی هوشمند با API در مرحله‌ی بعد فعال می‌شود."
    )
}
