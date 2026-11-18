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

    private val modes = listOf(
        "رفع خطا و اصلاح کد", "ساخت کد جدید",
        "توضیح و آموزش کد", "بهینه‌سازی کد",
        "بررسی امنیت کد", "طراحی برنامه", "ساخت پروژه کامل APK"
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
                if (map === modeButtons) selectedMode = value else selectedPlatform = value
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
            if (selectedMode == "ساخت پروژه کامل APK") {
                append("\\n\\nحالت ساخت پروژه کامل اندروید فعال است. مانند یک مهندس ارشد Android عمل کن.")
                append("\\nابتدا نیازمندی‌ها را خلاصه کن، سپس ساختار کامل پروژه را با مسیر دقیق هر فایل ارائه بده.")
                append("\\nبرای هر فایل، یک بلوک کد مستقل با مسیر فایل در خط اول مانند // FILE: app/src/main/... بساز.")
                append("\\nفایل‌های Gradle، Manifest، Activityها، منابع و کدهای لازم را کامل ارائه کن؛ هیچ فایل ضروری را با سه‌نقطه حذف نکن.")
                append("\\nاز APIها و وابستگی‌های واقعی و سازگار استفاده کن؛ نسخه‌ها را حدس نزن و ناسازگاری را اعلام کن.")
                append("\\nقبل از پایان، فهرست فایل‌ها، دستور ساخت، پیش‌نیازها و تست‌های لازم را بررسی کن.")
                append("\\nهرگز ادعا نکن APK ساخته یا تست شده مگر واقعاً ابزار ساخت آن را اجرا کرده باشد.")
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
            runButton.isEnabled = true
            runButton.alpha = 1f
            output.text = result.message
            lastAnswer = if (result.success) result.message else ""
        }
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