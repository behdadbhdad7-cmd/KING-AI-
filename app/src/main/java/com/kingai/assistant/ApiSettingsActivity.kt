package com.kingai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class ApiSettingsActivity : Activity() {

    private lateinit var baseUrlInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    private lateinit var modeGroup: RadioGroup
    private lateinit var tokenLimitLabel: TextView
    private lateinit var tokenLimitBar: SeekBar

    private val green = Color.rgb(0, 255, 100)
    private val pageBackground = Color.rgb(10, 15, 20)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "مدیریت هوش KING AI"
        window.statusBarColor = pageBackground
        window.navigationBarColor = pageBackground

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 25, 20, 25)
            setBackgroundColor(pageBackground)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        val heading = TextView(this).apply {
            text = "KING AI  •  BRAIN CONTROL"
            textSize = 23f
            setTextColor(green)
            gravity = Gravity.CENTER
        }
        root.addView(heading, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 75
        ))

        root.addView(label("آدرس سرویس"))
        baseUrlInput = editText("https://openrouter.ai/api/v1")
        baseUrlInput.setText(ApiConfig.getBaseUrl(this).ifBlank { "https://openrouter.ai/api/v1" })
        root.addView(baseUrlInput)

        root.addView(label("روش انتخاب مدل"))
        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val autoRadio = RadioButton(this).apply {
            id = 1001
            text = "خودکار  •  openrouter/free"
            setTextColor(white)
        }
        val manualRadio = RadioButton(this).apply {
            id = 1002
            text = "دستی  •  انتخاب از مدل‌های رایگان"
            setTextColor(white)
        }
        modeGroup.addView(autoRadio)
        modeGroup.addView(manualRadio)
        modeGroup.check(if (ApiConfig.getMode(this) == "manual") 1002 else 1001)
        root.addView(modeGroup)

        root.addView(label("مدل انتخاب‌شده"))
        modelInput = editText("مثال: provider/model:free")
        modelInput.setText(ApiConfig.getModel(this).ifBlank { "openrouter/free" })
        root.addView(modelInput)

        val fetchButton = button("دریافت و به‌روزرسانی فهرست مدل‌های رایگان")
        root.addView(fetchButton)

        root.addView(label("کلید API (Token)"))
        tokenInput = editText("کلید API را وارد کن")
        tokenInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(tokenInput)

        root.addView(label("کلید API روی گوشی رمزگذاری می‌شود و در GitHub قرار نمی‌گیرد."))

        root.addView(label("حداکثر طول پاسخ  •  تعادل کیفیت و مصرف"))
        tokenLimitLabel = label("")
        root.addView(tokenLimitLabel)
        tokenLimitBar = SeekBar(this).apply {
            max = 1280
            progress = (ApiConfig.getMaxTokens(this@ApiSettingsActivity) - 256).coerceIn(0, 1280)
            progressTintList = android.content.res.ColorStateList.valueOf(green)
            thumbTintList = android.content.res.ColorStateList.valueOf(green)
        }
        tokenLimitLabel.text = "حداکثر توکن پاسخ: ${tokenLimitBar.progress + 256}"
        tokenLimitBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tokenLimitLabel.text = "حداکثر توکن پاسخ: ${progress + 256}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        root.addView(tokenLimitBar)

        saveButton = button("ذخیره تنظیمات")
        root.addView(saveButton)
        testButton = button("آزمایش اتصال")
        root.addView(testButton)
        val clearButton = button("پاک‌کردن تنظیمات API")
        root.addView(clearButton)

        statusText = TextView(this).apply {
            text = "وضعیت: آماده"
            textSize = 15f
            setTextColor(green)
            setPadding(5, 25, 5, 20)
            gravity = Gravity.CENTER
        }
        root.addView(statusText)
        setContentView(scroll)

        fetchButton.setOnClickListener { fetchFreeModels() }
        saveButton.setOnClickListener { saveConfig() }
        testButton.setOnClickListener { testConnection() }
        clearButton.setOnClickListener {
            ApiConfig.clear(this)
            baseUrlInput.setText("https://openrouter.ai/api/v1")
            modelInput.setText("openrouter/free")
            tokenInput.setText("")
            modeGroup.check(1001)
            tokenLimitBar.progress = 512
            statusText.text = "تنظیمات API پاک شد."
            Toast.makeText(this, "تنظیمات پاک شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun label(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(white)
            setPadding(5, 18, 5, 8)
        }
    }

    private fun editText(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 14f
            setTextColor(white)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            setPadding(15, 10, 15, 10)
            backgroundTintList = android.content.res.ColorStateList.valueOf(green)
        }
    }

    private fun button(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 14f
            setTextColor(Color.BLACK)
            setBackgroundColor(green)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
    }

    private fun fetchFreeModels() {
        val url = baseUrlInput.text.toString().trim()
        val typedToken = tokenInput.text.toString().trim()
        val token = typedToken.ifBlank { ApiConfig.getToken(this).orEmpty() }
        statusText.text = "در حال دریافت مدل‌های رایگان..."
        Thread {
            try {
                val models = OpenRouterCatalog.fetchFreeModels(url, token)
                runOnUiThread {
                    if (models.isEmpty()) {
                        statusText.text = "مدل رایگان قابل‌استفاده پیدا نشد."
                        return@runOnUiThread
                    }
                    val labels = models.map { it.toString() }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("مدل رایگان را انتخاب کن")
                        .setItems(labels) { _, which ->
                            modelInput.setText(models[which].id)
                            modeGroup.check(1002)
                            statusText.text = "مدل انتخاب شد: ${models[which].id}"
                        }
                        .setNegativeButton("بستن", null)
                        .show()
                    statusText.text = "فهرست به‌روز شد: ${models.size} گزینه رایگان."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "دریافت مدل‌ها ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
                }
            }
        }.start()
    }

    private fun saveConfig(): Boolean {
        val url = baseUrlInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val token = tokenInput.text.toString().trim()
        val mode = if (modeGroup.checkedRadioButtonId == 1002) "manual" else "auto"

        if (!url.startsWith("https://", true) || !url.contains("openrouter.ai", true)) {
            statusText.text = "برای حالت رایگان، آدرس رسمی OpenRouter را وارد کن."
            return false
        }
        if (model.isBlank()) {
            statusText.text = "نام مدل خالی است."
            return false
        }
        if (mode == "manual" && model != "openrouter/free" &&
            !model.endsWith(":free", ignoreCase = true)) {
            statusText.text = "در حالت رایگان، مدل دستی باید پسوند :free داشته باشد."
            return false
        }
        if (!ApiConfig.saveEndpoint(this, url, model)) {
            statusText.text = "ذخیره آدرس یا مدل ناموفق بود."
            return false
        }
        if (!ApiConfig.saveMode(this, mode)) {
            statusText.text = "ذخیره حالت انتخاب مدل ناموفق بود."
            return false
        }
        if (!ApiConfig.saveMaxTokens(this, tokenLimitBar.progress + 256)) {
            statusText.text = "ذخیره محدودیت پاسخ ناموفق بود."
            return false
        }
        if (token.isNotBlank()) {
            if (!ApiConfig.saveToken(this, token)) {
                statusText.text = "ذخیره امن توکن ناموفق بود."
                return false
            }
            tokenInput.setText("")
        } else if (!ApiConfig.hasToken(this)) {
            statusText.text = "کلید API را وارد کن."
            return false
        }

        statusText.text = if (mode == "auto") {
            "ذخیره شد؛ مدل‌یابی خودکار رایگان فعال است."
        } else {
            "ذخیره شد؛ مدل رایگان دستی فعال است."
        }
        Toast.makeText(this, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun testConnection() {
        if (!saveConfig()) return
        saveButton.isEnabled = false
        testButton.isEnabled = false
        statusText.text = "در حال آزمایش اتصال رایگان..."
        ApiClient.sendMessage(this, "سلام! فقط کوتاه و طبیعی به فارسی بگو اتصال KING AI برقرار است.") { result ->
            saveButton.isEnabled = true
            testButton.isEnabled = true
            statusText.text = if (result.success) {
                "اتصال موفق بود.\nمدل واقعی: ${result.actualModel ?: "نامشخص"}\n\nپاسخ:\n${result.message}"
            } else {
                "اتصال ناموفق بود:\n${result.message}"
            }
        }
    }
}
