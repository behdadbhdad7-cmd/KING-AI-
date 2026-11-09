package com.kingai.assistant

import android.content.Context
import android.content.Intent

/** پیدا کردن و باز کردن برنامه‌های نصب‌شده بر اساس نام (فارسی یا انگلیسی). */
object AppLauncher {

    data class AppInfo(val label: String, val pkg: String)

    // نام فارسی رایج -> بخش‌هایی از نام یا پکیج برنامه
    private val aliases: Map<String, List<String>> = mapOf(
        "واتساپ" to listOf("whatsapp"),
        "واتسپ" to listOf("whatsapp"),
        "واتساب" to listOf("whatsapp"),
        "تلگرام" to listOf("telegram"),
        "اینستاگرام" to listOf("instagram"),
        "اینستا" to listOf("instagram"),
        "کروم" to listOf("chrome"),
        "یوتیوب" to listOf("youtube"),
        "گوگل" to listOf("google"),
        "جیمیل" to listOf("gmail"),
        "نقشه" to listOf("maps"),
        "ماشینحساب" to listOf("calculator", "calc"),
        "ساعت" to listOf("clock"),
        "تنظیمات" to listOf("settings"),
        "مخاطبین" to listOf("contacts"),
        "پیامک" to listOf("messag", "sms"),
        "پیامها" to listOf("messag", "sms"),
        "تماس" to listOf("dialer", "phone"),
        "شمارهگیر" to listOf("dialer", "phone"),
        "فروشگاهگوگل" to listOf("vending", "playstore"),
        "پلیاستور" to listOf("vending", "playstore"),
        "تقویم" to listOf("calendar"),
        "فایل" to listOf("files", "filemanager"),
        "فایلها" to listOf("files", "filemanager"),
        "موزیک" to listOf("music"),
        "اسپاتیفای" to listOf("spotify"),
        "اسنپ" to listOf("snapp"),
        "دیوار" to listOf("divar"),
        "ایتا" to listOf("eitaa")
    ).mapKeys { FaText.normalize(it.key) }

    @Suppress("DEPRECATION")
    fun launchableApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    private fun aliasTokens(q: String): List<String> {
        val out = ArrayList<String>()
        for ((key, tokens) in aliases) {
            if (q == key || q.contains(key)) out.addAll(tokens)
        }
        return out
    }

    private fun find(apps: List<AppInfo>, query: String): AppInfo? {
        val q = FaText.normalize(query)
        if (q.isEmpty()) return null
        val tokens = aliasTokens(q)

        var best: AppInfo? = null
        var bestScore = 0
        for (a in apps) {
            val l = FaText.normalize(a.label)
            val p = a.pkg.lowercase()
            var score = 0
            if (l == q) score = 100
            else if (l.startsWith(q)) score = 80
            else if (l.contains(q)) score = 60
            else if (l.length >= 3 && q.length >= 3 && q.contains(l)) score = 40

            for (t in tokens) {
                if (l == t) score = maxOf(score, 90)
                else if (l.contains(t)) score = maxOf(score, 70)
                else if (p.contains(t)) score = maxOf(score, 50)
            }

            val current = best
            if (score > bestScore ||
                (score > 0 && score == bestScore && current != null && a.label.length < current.label.length)
            ) {
                best = a
                bestScore = score
            }
        }
        return best
    }

    fun open(ctx: Context, query: String): CommandResult {
        val app = find(launchableApps(ctx), query)
            ?: return CommandResult("برنامه‌ای با نام «$query» پیدا نشد.", false)
        val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
            ?: return CommandResult("«${app.label}» قابل باز شدن نیست.", false)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            CommandResult("در حال باز کردن ${app.label}...")
        } catch (e: Exception) {
            CommandResult("باز کردن «${app.label}» ناموفق بود.", false)
        }
    }
}
