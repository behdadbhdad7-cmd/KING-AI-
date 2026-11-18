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
            has(n, "چی یادت هست", "حافظه من", "حافظهام", "چه چیزهایی یادت هست") -> {
                val saved = MemoryStore.all(ctx)
                CommandResult(if (saved.isEmpty()) "هنوز چیزی در حافظه ذخیره نشده. برای ذخیره بنویس: «به خاطر بسپار ...»"
                    else "چیزهایی که با اجازه‌ات ذخیره کرده‌ام:\n" + saved.mapIndexed { i, item -> "${i + 1}. $item" }.joinToString("\n"))
            }

            has(n, "پاک کن حافظه", "پاکسازی حافظه", "حافظه را پاک کن") -> {
                MemoryStore.clear(ctx)
                CommandResult("حافظه‌ی محلی پاک شد.")
            }

            n.startsWith("به خاطر بسپار") || n.startsWith("یادت باشه") || n.startsWith("این رو یادت بمونه") -> {
                val fact = when {
                    text.startsWith("به خاطر بسپار") -> text.removePrefix("به خاطر بسپار").trim(' ', ':', '：', '-')
                    text.startsWith("یادت باشه") -> text.removePrefix("یادت باشه").trim(' ', ':', '：', '-')
                    else -> text.removePrefix("این رو یادت بمونه").trim(' ', ':', '：', '-')
                }
                if (MemoryStore.remember(ctx, fact)) CommandResult("باشه، این مورد را در حافظه‌ی همین گوشی ذخیره کردم. هر وقت خواستی می‌توانی بگویی «چی یادت هست؟» یا «فراموش کن ...».")
                else CommandResult("این مورد خالی، خیلی طولانی یا شبیه اطلاعات محرمانه است و ذخیره نشد.", false)
            }

            n.startsWith("فراموش کن") -> {
                val query = text.substringAfter("فراموش کن", "").trim()
                val removed = MemoryStore.forget(ctx, query)
                if (query.isBlank()) CommandResult("بگو کدام مورد را فراموش کنم؛ برای پاک‌کردن همه‌ی موارد بگو «پاک کن حافظه».")
                else CommandResult(if (removed > 0) "${removed} مورد از حافظه پاک شد." else "موردی مطابق این عبارت در حافظه پیدا نکردم.")
            }

            has(n, "تایمر") -> DeviceActions.setTimer(ctx, text)

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
            "• باز کن «نام برنامه»\n" +
            "• تایمر ۵ دقیقه\n" +
            "• به خاطر بسپار ... / چی یادت هست؟ / فراموش کن ...\n\n" +
            "پاسخ‌گویی هوشمند با API در مرحله‌ی بعد فعال می‌شود."
    )
}
