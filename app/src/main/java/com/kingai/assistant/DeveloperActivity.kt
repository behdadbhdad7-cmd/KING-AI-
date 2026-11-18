package com.kingai.assistant

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** محیط برنامه‌نویس؛ فایل فقط با انتخاب و ارسال صریح کاربر خوانده می‌شود. */
class DeveloperActivity : ComponentActivity() {
    private lateinit var task: EditText
    private lateinit var output: TextView
    private lateinit var fileInfo: TextView
    private var selectedText = ""
    private var selectedName = ""
    private var lastAnswer = ""

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
                    fileInfo.text = "فایل بزرگ‌تر از ۱۰۰ کیلوبایت است؛ فایل کوچک‌تری انتخاب کن."
                } else {
                    selectedText = String(collected.toByteArray(), Charsets.UTF_8)
                    selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "فایل انتخابی"
                    fileInfo.text = "فایل آماده است: $selectedName؛ فقط با زدن «بررسی کد» ارسال می‌شود."
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
        window.statusBarColor = Color.rgb(2, 6, 4)
        window.navigationBarColor = Color.rgb(2, 6, 4)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(16))
            setBackgroundColor(Color.rgb(2, 6, 4))
        }
        root.addView(text("برنامه‌نویس KING", 24f, 0xFF39FF14.toInt(), true))
        root.addView(text("کد بساز، خطا پیدا کن یا فایل پروژه را بررسی کن.", 14f, 0xFFA9CDB4.toInt()))
        root.addView(text("فقط فایل انتخاب‌شده و فقط پس از زدن دکمه‌ی بررسی برای مدل ارسال می‌شود.", 12f, 0xFFFFD166.toInt()), paramsTop(10))
        task = EditText(this).apply {
            hint = "مثلاً: این کد را بررسی کن و خطاهایش را اصلاح کن..."
            setHintTextColor(0xFF789581.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF102018.toInt())
        }
        root.addView(task, paramsTop(14))
        fileInfo = text("فایلی انتخاب نشده؛ می‌توانی فقط توضیح بنویسی.", 12f, 0xFFA9CDB4.toInt())
        root.addView(fileInfo, paramsTop(8))
        root.addView(actionButton("انتخاب فایل کد") {
            openFile.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "application/octet-stream"))
        }, paramsTop(8))
        root.addView(actionButton("بررسی کد / ساخت برنامه") { runTask() }, paramsTop(8))
        val scroll = ScrollView(this)
        output = text("نتیجه‌ی KING AI اینجا نمایش داده می‌شود.", 14f, Color.WHITE)
        output.setPadding(dp(12), dp(12), dp(12), dp(12))
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        root.addView(actionButton("ذخیره‌ی پاسخ در فایل") {
            if (lastAnswer.isBlank()) toast("هنوز پاسخی برای ذخیره نیست.")
            else saveFile.launch("KING-AI-Code-Answer.txt")
        }, paramsTop(8))
        root.addView(actionButton("بازگشت به گفتگو") { finish() }, paramsTop(8))
        setContentView(root)
    }

    private fun runTask() {
        val request = task.text.toString().trim()
        if (request.isBlank()) { toast("اول بنویس چه کاری روی کد انجام بدهم."); return }
        if (!ApiConfig.hasToken(this) || ApiConfig.getBaseUrl(this).isBlank() || ApiConfig.getModel(this).isBlank()) {
            toast("اول اتصال API را از تنظیمات فعال کن.")
            return
        }
        val prompt = buildString {
            append("در نقش برنامه‌نویس متخصص KING AI عمل کن. درخواست کاربر: ")
            append(request)
            if (selectedText.isNotBlank()) {
                append("\n\nنام فایل انتخاب‌شده: ")
                append(selectedName)
                append("\nمحتوای فایل که کاربر صریحاً برای بررسی انتخاب کرده است:\n")
                append(selectedText)
            }
            append("\n\nپاسخ را فارسی بده. اگر کد می‌نویسی، کامل و قابل کپی ارائه کن؛ وابستگی‌ها، مسیر فایل و مراحل اجرا را مشخص کن. اگر اطلاعات کافی نیست سؤال دقیق بپرس. هیچ فایل یا تنظیمی را خودکار تغییر نده.")
        }
        output.text = "KING AI در حال بررسی است..."
        lastAnswer = ""
        ApiClient.sendMessage(this, prompt, includeMemory = false) { result ->
            if (isFinishing || isDestroyed) return@sendMessage
            output.text = result.message
            lastAnswer = if (result.success) result.message else ""
        }
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

    private fun paramsTop(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}
