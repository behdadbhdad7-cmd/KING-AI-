package com.kingai.assistant

/** نرمال‌سازی متن فارسی برای تشخیص فرمان (حروف عربی، نیم‌فاصله، فاصله، اعراب و ارقام). */
object FaText {

    fun normalize(s: String, keepSpaces: Boolean = false): String {
        val sb = StringBuilder()
        for (raw in s.lowercase()) {
            val ch = when (raw) {
                '\u064A', '\u0649' -> '\u06CC' // ي ى -> ی
                '\u0643' -> '\u06A9'           // ك -> ک
                '\u0623', '\u0625', '\u0671' -> '\u0627' // أ إ ٱ -> ا
                else -> raw
            }
            when {
                ch == '\u200C' || ch == ' ' || ch == '\u00A0' -> {
                    if (keepSpaces) sb.append(' ')
                }
                ch == '-' || ch == '\u200F' || ch == '\u200E' || ch == '\u0640' -> {
                }
                ch in '\u064B'..'\u065F' -> {
                }
                ch in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                ch in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                else -> sb.append(ch)
            }
        }
        val out = sb.toString()
        return if (keepSpaces) out.replace(Regex(" +"), " ").trim() else out.trim()
    }

    private val openWords = setOf(
        "باز", "کن", "کردن", "بکن", "بکنید", "کنید", "بازکن", "بازکردن",
        "اجرا", "اجرای", "اجراکن", "برنامه", "اپ", "اپلیکیشن", "را", "رو",
        "لطفا", "بزن", "برو", "به", "ببر", "من", "برای", "ی",
        "open", "launch", "start", "app"
    )

    /** نام برنامه را از جمله‌ای مثل «واتساپ را باز کن» بیرون می‌کشد. */
    fun extractAppName(text: String): String =
        normalize(text, true)
            .split(' ')
            .filter { it.isNotBlank() && it !in openWords }
            .joinToString(" ")
}
