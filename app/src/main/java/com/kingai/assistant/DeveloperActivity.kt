package com.kingai.assistant

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** محیط برنامه‌نویس KING AI؛ فایل فقط با انتخاب و ارسال صریح کاربر خوانده می‌شود. */
class DeveloperActivity : ComponentActivity() {
    private lateinit var task: EditText
    private lateinit var output: TextView
    private lateinit var fileInfo: TextView
    private lateinit var runButton: TextView
    private val modeButtons = mutableMapOf<String, TextView>()
    private val platformButtons = mutableMapOf<String, TextView>()
    private var selectedMode = "رفع خطا و اصلاح کد"
    private var selectedPlatform = "عمومی"
    private var selectedText = ""
    private var selectedName = ""
    private var lastAnswer = ""
    private var lastHtml = ""
    private var projectZip = ByteArray(0)
    private lateinit var saveProjectZipButton: TextView
    private lateinit var previewButton: TextView
    private lateinit var saveHtmlButton: TextView

    private val modes = listOf(
        "رفع خطا و اصلاح کد", "ساخت کد جدید",
        "توضیح و آموزش کد", "بهینه‌سازی کد",
        "بررسی امنیت کد", "طراحی برنامه", "ساخت پروژه اندروید", "طراحی وب‌سایت"
    )
    private val platforms = listOf("عمومی", "Android / Kotlin", "Python", "وب / HTML")

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            fileInfo.text = "فایلی انتخاب نشد."
            return@registerForActivityResult
        }
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val collected = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 100000) break
                    collected.write(buffer, 0, count)
                }
                if (total > 100000) {
                    selectedText = ""
                    selectedName = ""
                    fileInfo.text = "حجم فایل بیشتر از ۱۰۰ کیلوبایت است؛ فایل کوچک‌تری انتخاب کن."
                } else {
                    selectedText = String(collected.toByteArray(), Charsets.UTF_8)
                    selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "فایل انتخابی"
                    fileInfo.text = "فایل آماده‌ی بررسی است\n$selectedName  •  \${total} بایت"
                }
            } ?: run { fileInfo.text = "خواندن فایل ممکن نشد." }
        } catch (_: Exception) {
            selectedText = ""
            selectedName = ""
            fileInfo.text = "خواندن فایل ناموفق بود."
        }
    }

    private val saveProjectZipFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null || projectZip.isEmpty()) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(projectZip) }
            toast("فایل ZIP پروژه ذخیره شد.")
        } catch (_: Exception) { toast("ذخیره‌ی ZIP ناموفق بود.") }
    }

    private val saveHtmlFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri == null || lastHtml.isBlank()) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(lastHtml.toByteArray(Charsets.UTF_8)) }
            toast("فایل HTML ذخیره شد.")
        } catch (_: Exception) { toast("ذخیره‌ی HTML ناموفق بود.") }
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null || lastAnswer.isBlank()) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(lastAnswer.toByteArray(Charsets.UTF_8)) }
            toast("فایل پاسخ ذخیره شد.")
        } catch (_: Exception) { toast("ذخیره‌ی فایل ناموفق بود.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Neon.BLACK
        window.navigationBarColor = Neon.BLACK
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Neon.BLACK)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text("برنامه‌نویس KING", 25f, Neon.GREEN, true))
        titleBlock.addView(text("کارگاه هوشمند کدنویسی", 13f, Neon.TEXT_DIM), top(3))
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(chip("AI • DEV", Neon.GREEN))
        content.addView(header)

        content.addView(text(
            "کد بساز، خطاها را پیدا کن، فایل پروژه را بررسی کن یا از صفر یک برنامه طراحی کن.",
            14f, 0xFFD0E2D5.toInt()
        ), top(10))

        content.addView(sectionTitle("۱  •  نوع عملیات"), top(22))
        val modeGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in modes.indices step 2) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            addChoice(line, modes[i], modeButtons, true)
            if (i + 1 < modes.size) addChoice(line, modes[i + 1], modeButtons, true)
            modeGrid.addView(line, top(8))
        }
        content.addView(modeGrid)
        refreshChoices(modeButtons, selectedMode)

        content.addView(sectionTitle("۲  •  زبان یا پلتفرم"), top(20))
        val platformRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        platforms.forEach { addChoice(platformRow, it, platformButtons, false) }
        content.addView(platformRow, top(8))
        refreshChoices(platformButtons, selectedPlatform)

        content.addView(sectionTitle("۳  •  توضیح کاری که می‌خواهی"), top(20))
        task = EditText(this).apply {
            hint = "مثلاً: این کد را بررسی کن، خطاهایش را پیدا کن و نسخه‌ی اصلاح‌شده‌ی کامل بده..."
            setHintTextColor(0xFF789581.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 4
            maxLines = 7
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = panelBackground()
        }
        content.addView(task, top(9))

        content.addView(sectionTitle("۴  •  فایل پروژه (اختیاری)"), top(20))
        val fileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = panelBackground()
        }
        fileInfo = text("هنوز فایلی انتخاب نشده.\nفقط فایلی که خودت انتخاب کنی، پس از زدن دکمه‌ی اجرا ارسال می‌شود.", 12f, Neon.TEXT_DIM)
        fileCard.addView(fileInfo)
        val fileActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fileActions.addView(actionButton("＋  انتخاب فایل") {
            openFile.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "application/octet-stream"))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        fileActions.addView(actionButton("حذف فایل") {
            selectedText = ""
            selectedName = ""
            fileInfo.text = "فایل انتخاب نشده است."
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        fileCard.addView(fileActions, top(10))
        content.addView(fileCard, top(9))

        content.addView(sectionTitle("۵  •  خروجی KING AI"), top(20))
        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = panelBackground()
        }
        resultCard.addView(chip("نتیجه‌ی پردازش", Neon.GREEN))
        output = text("نتیجه‌ی بررسی یا کد ساخته‌شده اینجا نمایش داده می‌شود.", 14f, 0xFFE7F5EA.toInt())
        output.setPadding(0, dp(12), 0, dp(8))
        output.setTextIsSelectable(true)
        resultCard.addView(output)
        content.addView(resultCard, top(9))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(9), dp(16), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF07140D.toInt(), Neon.BLACK)
            )
        }
        runButton = actionButton("▶  اجرای درخواست با KING AI") { runTask() }
        footer.addView(runButton, LinearLayout.LayoutParams(-1, dp(52)))
        previewButton = actionButton("◉  پیش‌نمایش وب‌سایت") { previewWebsite() }
        previewButton.visibility = View.GONE
        footer.addView(previewButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        saveHtmlButton = actionButton("ذخیره‌ی فایل HTML") {
            if (lastHtml.isBlank()) toast("اول وب‌سایت را تولید کن.")
            else saveHtmlFile.launch("KING-AI-Website.html")
        }
        saveHtmlButton.visibility = View.GONE
        footer.addView(saveHtmlButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
        saveProjectZipButton = actionButton("دانلود فایل ZIP پروژه اندروید") {
            if (projectZip.isEmpty()) toast("فایل‌های پروژه از پاسخ استخراج نشدند؛ از مدل بخواه فایل‌ها را با مسیر مشخص تحویل بدهد.")
            else saveProjectZipFile.launch("KING-AI-Android-Project.zip")
        }
        saveProjectZipButton.visibility = View.GONE
        footer.addView(saveProjectZipButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(actionButton("ذخیره‌ی پاسخ") {
            if (lastAnswer.isBlank()) toast("هنوز پاسخی برای ذخیره نیست.")
            else saveFile.launch("KING-AI-Answer.txt")
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { topMargin = dp(8) })
        bottomRow.addView(actionButton("بازگشت") { finish() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { topMargin = dp(8); marginStart = dp(8) })
        footer.addView(bottomRow)
        root.addView(footer)
        setContentView(root)
    }

    private fun addChoice(row: LinearLayout, value: String, map: MutableMap<String, TextView>, wide: Boolean) {
        val view = TextView(this).apply {
            text = value
            gravity = Gravity.CENTER
            textSize = if (wide) 12f else 11f
            setPadding(dp(7), dp(11), dp(7), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (map === modeButtons) {
                    selectedMode = value
                    val webMode = value == "طراحی وب‌سایت"
                    if (webMode) {
                        selectedPlatform = "وب / HTML"
                        refreshChoices(platformButtons, selectedPlatform)
                    }
                    previewButton.visibility = if (webMode) View.VISIBLE else View.GONE
                    saveHtmlButton.visibility = if (webMode) View.VISIBLE else View.GONE
                    saveProjectZipButton.visibility = if (value == "ساخت پروژه اندروید") View.VISIBLE else View.GONE
                } else selectedPlatform = value
                refreshChoices(map, value)
            }
        }
        map[value] = view
        row.addView(view, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            if (row.childCount > 0) marginStart = dp(7)
        })
    }

    private fun refreshChoices(map: MutableMap<String, TextView>, selected: String) {
        map.forEach { (name, view) ->
            val active = name == selected
            view.setTextColor(if (active) Color.BLACK else 0xFFD5E7D9.toInt())
            view.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            view.background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                if (active) {
                    colors = intArrayOf(0xFFB7FFAA.toInt(), Neon.GREEN, 0xFF16B83A.toInt())
                    setStroke(dp(1), 0xFFE0FFD8.toInt())
                } else {
                    setColor(0xFF0B1A11.toInt())
                    setStroke(dp(1), 0x6646A85A)
                }
            }
        }
    }

    private fun runTask() {
        val request = task.text.toString().trim()
        if (request.isBlank()) { toast("اول توضیح بده چه کاری انجام بدهم."); return }
        if (!ApiConfig.hasToken(this) || ApiConfig.getBaseUrl(this).isBlank() || ApiConfig.getModel(this).isBlank()) {
            toast("اول اتصال API را از تنظیمات فعال کن.")
            return
        }
        val prompt = buildString {
            append("در نقش برنامه‌نویس ارشد و دقیق KING AI عمل کن.\n")
            append("نوع عملیات: ").append(selectedMode).append("\n")
            append("زبان یا پلتفرم هدف: ").append(selectedPlatform).append("\n")
            append("درخواست کاربر: ").append(request)
            if (selectedMode == "طراحی وب‌سایت") {
                append("\nحالت طراح وب‌سایت فعال است. یک وب‌سایت کامل، زیبا، واکنش‌گرا و فارسی‌دوست بساز.")
                append("\nخروجی اصلی باید فقط یک فایل HTML مستقل و کامل باشد که CSS و JavaScript آن داخل همان فایل قرار دارد.")
                append("\nکد نهایی را در یک بلوک ```html قرار بده؛ قبل و بعد آن توضیح طولانی ننویس.")
                append("\nاز فونت‌ها، تصویرها و کتابخانه‌های خارجی استفاده نکن؛ صفحه باید بدون اینترنت هم تا حد ممکن نمایش داده شود.")
                append("\nطراحی موبایل‌اول، دسترس‌پذیر، راست‌چین برای فارسی، دارای حالت واکنش‌گرا و تعاملات واقعی باشد.")
                append("\nهیچ فرم پرداخت، ورود واقعی یا ارسال اطلاعات به سرور را وانمود نکن؛ اگر backend لازم است، شفاف توضیح بده.")
            }
            if (selectedMode == "ساخت پروژه اندروید") {
                append("\nحالت ساخت پروژه کامل اندروید فعال است. مانند یک مهندس ارشد Android عمل کن.")
                append("\nابتدا نیازمندی‌ها را خلاصه کن، سپس ساختار کامل پروژه را با مسیر دقیق هر فایل ارائه بده.")
                append("\nبرای هر فایل، یک بلوک کد مستقل با مسیر فایل در خط اول مانند // FILE: app/src/main/... بساز.")
                append("\nفایل‌های Gradle، Manifest، Activityها، منابع و کدهای لازم را کامل ارائه کن؛ هیچ فایل ضروری را با سه‌نقطه حذف نکن.")
                append("\nاز APIها و وابستگی‌های واقعی و سازگار استفاده کن؛ نسخه‌ها را حدس نزن و ناسازگاری را اعلام کن.")
                append("\nقبل از پایان، فهرست فایل‌ها، دستور ساخت، پیش‌نیازها و تست‌های لازم را بررسی کن.")
                append("\nهرگز ادعا نکن APK ساخته یا تست شده مگر واقعاً ابزار ساخت آن را اجرا کرده باشد.")
            }
            if (selectedText.isNotBlank()) {
                append("\n\nنام فایل انتخاب‌شده: ").append(selectedName)
                append("\nمحتوای فایل که کاربر صریحاً برای بررسی انتخاب کرده است:\n")
                append(selectedText)
            }
            append("\n\nقواعد پاسخ:")
            append("\n- فارسی روان و دقیق بنویس.")
            append("\n- اگر کد می‌سازی، فایل کامل و آماده‌ی کپی بده؛ تکه‌کد ناقص نده.")
            append("\n- مسیر فایل، وابستگی‌ها و دستور اجرای لازم را مشخص کن.")
            append("\n- اگر خطا را اصلاح می‌کنی، علت و نسخه‌ی کامل اصلاح‌شده را ارائه بده.")
            append("\n- اگر اطلاعات لازم کم است، فقط سؤال ضروری را بپرس.")
            append("\n- هیچ فایل یا تنظیمی را خودکار تغییر نده و ادعای اجرای کد نکن.")
        }
        runButton.isEnabled = false
        runButton.alpha = 0.55f
        output.text = "KING AI در حال بررسی درخواست است…"
        lastAnswer = ""
        ApiClient.sendMessage(this, prompt, includeMemory = false) { result ->
            if (isFinishing || isDestroyed) return@sendMessage
            if (selectedMode == "طراحی وب‌سایت" && result.success) {
                output.text = "کد دریافت شد؛ در حال بررسی کامل‌بودن HTML…"
                continueWebsiteGeneration(prompt, result.message, 2)
                return@sendMessage
            }
            runButton.isEnabled = true
            runButton.alpha = 1f
            output.text = result.message
            lastAnswer = if (result.success) result.message else ""
            lastHtml = ""
            projectZip = if (result.success && selectedMode == "ساخت پروژه اندروید") buildProjectZip(result.message) else ByteArray(0)
        }
    }

    /**
     * خروجی وب را اعتبارسنجی می‌کند و اگر مدل وسط فایل متوقف شده باشد،
     * حداکثر دو بار از همان گفتگو ادامه‌ی دقیق فایل را درخواست می‌کند.
     */
    private fun continueWebsiteGeneration(originalPrompt: String, accumulatedAnswer: String, attemptsLeft: Int) {
        if (isFinishing || isDestroyed) return
        val html = extractHtml(accumulatedAnswer)
        if (html.isNotBlank() && isCompleteHtml(html)) {
            runButton.isEnabled = true
            runButton.alpha = 1f
            lastAnswer = accumulatedAnswer
            lastHtml = html
            output.text = "✓ فایل HTML کامل شد و بررسی اولیه‌ی ساختار را گذراند. حالا می‌توانی پیش‌نمایش بگیری."
            return
        }
        if (attemptsLeft <= 0 || html.isBlank()) {
            runButton.isEnabled = true
            runButton.alpha = 1f
            lastAnswer = accumulatedAnswer
            lastHtml = ""
            output.text = "خروجی HTML هنوز ناقص است؛ برای جلوگیری از نمایش صفحه‌ی خالی، پیش‌نمایش و ذخیره‌ی HTML غیرفعال ماند. دوباره درخواست ساخت را اجرا کن یا از مدل بخواه سایت را در بخش‌های کوچک‌تر بسازد."
            return
        }

        output.text = "HTML ناقص تشخیص داده شد؛ KING AI در حال تکمیل خودکار فایل است…"
        val messages = listOf(
            ConversationMessage("user", originalPrompt),
            ConversationMessage("assistant", accumulatedAnswer),
            ConversationMessage(
                "user",
                "فایل HTML قبلی در میانه‌ی کد قطع شده است. فقط ادامه‌ی دقیق همان فایل را از اولین کاراکتر بعد از آخرین کاراکتر خروجی قبلی تولید کن؛ هیچ قسمت قبلی را تکرار نکن، توضیح نده و کل ادامه را در یک بلوک html بده. تمام تگ‌ها، آکولادهای CSS/JavaScript و تگ‌های پایانی body و html را کامل ببند. اگر برای تکمیل در یک پاسخ جا نمی‌شود، ادامه را کوتاه و منسجم نگه دار."
            )
        )
        ApiClient.sendConversation(this, messages, includeMemory = false) { continuation ->
            if (isFinishing || isDestroyed) return@sendConversation
            if (!continuation.success) {
                runButton.isEnabled = true
                runButton.alpha = 1f
                lastAnswer = accumulatedAnswer
                lastHtml = ""
                output.text = "تکمیل خودکار ناموفق شد: ${continuation.message}\nبرای جلوگیری از پیش‌نمایش خراب، فایل ناقص پذیرفته نشد."
                return@sendConversation
            }
            val fragment = continuation.message
                .replace(Regex("^\\s*```(?:html)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*```\\s*$"), "")
            val nextAnswer = if (fragment.contains(Regex("<\\s*(?:!doctype|html)", RegexOption.IGNORE_CASE))) {
                continuation.message
            } else {
                accumulatedAnswer + "\n" + fragment
            }
            continueWebsiteGeneration(originalPrompt, nextAnswer, attemptsLeft - 1)
        }
    }

    /** بررسی محافظه‌کارانه‌ی ساختار؛ جایگزین اعتبارسنجی کامل مرورگر نیست. */
    private fun isCompleteHtml(html: String): Boolean {
        if (!html.contains(Regex("<\\s*!doctype\\s+html", RegexOption.IGNORE_CASE)) &&
            !html.contains(Regex("<\\s*html(?:\\s|>)", RegexOption.IGNORE_CASE))) return false
        if (!html.contains(Regex("</\\s*html\\s*>", RegexOption.IGNORE_CASE))) return false
        if (!html.contains(Regex("</\\s*body\\s*>", RegexOption.IGNORE_CASE))) return false
        for (tag in listOf("style", "script")) {
            val opened = Regex("<\\s*$tag(?:\\s|>)", RegexOption.IGNORE_CASE).findAll(html).count()
            val closed = Regex("</\\s*$tag\\s*>", RegexOption.IGNORE_CASE).findAll(html).count()
            if (opened != closed) return false
        }
        var depth = 0
        html.forEach { ch ->
            if (ch == '{') depth++
            if (ch == '}') {
                depth--
                if (depth < 0) return false
            }
        }
        return depth == 0
    }

    private fun buildProjectZip(answer: String): ByteArray {
        val blocks = Regex("```[^\\n]*\\n([\\s\\S]*?)```").findAll(answer)
        val files = linkedMapOf<String, String>()
        for (block in blocks) {
            val lines = block.groupValues[1].replace("\r", "").lines()
            if (lines.isEmpty()) continue
            val marker = Regex("^\\s*(?://|#|<!--)\\s*FILE:\\s*(.+?)(?:\\s*-->)?\\s*$", RegexOption.IGNORE_CASE).matchEntire(lines.first()) ?: continue
            val pathName = marker.groupValues[1].trim().replace("\\", "/")
            val parts = pathName.split("/")
            if (pathName.startsWith("/") || parts.any { it == ".." || it.isBlank() } || pathName.contains(":")) continue
            val body = lines.drop(1).joinToString("\n").trimEnd()
            if (body.isNotBlank() && body.toByteArray(Charsets.UTF_8).size <= 1_000_000) files[pathName] = body
            if (files.size >= 100) break
        }
        if (files.isEmpty()) return ByteArray(0)
        return try {
            val bytes = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(bytes).use { zip ->
                files.forEach { (name, body) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                if (!files.containsKey("README-KING-AI.txt")) {
                    zip.putNextEntry(java.util.zip.ZipEntry("README-KING-AI.txt"))
                    zip.write("Generated by KING AI. Review dependencies and permissions before building or installing.".toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            bytes.toByteArray().takeIf { it.size <= 8_000_000 } ?: ByteArray(0)
        } catch (_: Exception) { ByteArray(0) }
    }

    private fun previewWebsite() {
        if (lastHtml.isBlank()) { toast("اول یک وب‌سایت تولید کن."); return }
        startActivity(android.content.Intent(this, WebPreviewActivity::class.java).putExtra(WebPreviewActivity.EXTRA_HTML, lastHtml))
    }

    private fun extractHtml(answer: String): String {
        val fenced = Regex("```(?:html)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(answer)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: answer.trim()
        val start = candidate.indexOf("<", ignoreCase = false)
        return if (start >= 0 && candidate.substring(start).contains(Regex("<\\s*(html|!doctype)", RegexOption.IGNORE_CASE))) candidate.substring(start).trim() else ""
    }

    private fun actionButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFB7FFAA.toInt(), Neon.GREEN, 0xFF16B83A.toInt())
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0xFFE0FFD8.toInt())
        }
        elevation = dp(4).toFloat()
        setOnClickListener { action() }
    }

    private fun panelBackground() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFF10251A.toInt(), 0xFF08130D.toInt())
    ).apply {
        cornerRadius = dp(17).toFloat()
        setStroke(dp(1), 0x7746A85A)
    }

    private fun sectionTitle(value: String, params: LinearLayout.LayoutParams? = null): TextView =
        text(value, 14f, Neon.GREEN, true).also { if (params != null) it.layoutParams = params }

    private fun chip(value: String, color: Int) = TextView(this).apply {
        text = value
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0x2229FF14)
            setStroke(dp(1), 0x7746A85A)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setLineSpacing(dp(2).toFloat(), 1.12f)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun top(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}