package com.kingai.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ApiSettingsActivity : Activity() {

    private lateinit var baseUrlInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var saveButton: Button
    private lateinit var testButton: Button

    private val green = Color.rgb(0, 255, 100)
    private val background = Color.rgb(10, 15, 20)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "تنظیمات API"
        window.statusBarColor = background
        window.navigationBarColor = background

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 25, 20, 25)
            setBackgroundColor(background)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        val heading = TextView(this).apply {
            text = "KING AI  •  API"
            textSize = 26f
            setTextColor(green)
            gravity = Gravity.CENTER
        }

        root.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                75
            )
        )

        root.addView(label("آدرس سرویس (Base URL)"))

        baseUrlInput = editText(
            "مثال: https://api.example.com"
        )
        baseUrlInput.setText(ApiConfig.getBaseUrl(this))
        root.addView(baseUrlInput)

        root.addView(label("نام مدل هوش مصنوعی"))

        modelInput = editText("مثال: gpt-4o-mini")
        modelInput.setText(ApiConfig.getModel(this))
        root.addView(modelInput)

        root.addView(label("کلید API (Token)"))

        tokenInput = editText("کلید API را وارد کن")
        tokenInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        root.addView(tokenInput)

        root.addView(
            label(
                "کلید API فقط روی گوشی ذخیره می‌شود و در گیت‌هاب قرار نمی‌گیرد."
            )
        )

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

        saveButton.setOnClickListener {
            saveConfig()
        }

        testButton.setOnClickListener {
            testConnection()
        }

        clearButton.setOnClickListener {
            ApiConfig.clear(this)
            baseUrlInput.setText("")
            modelInput.setText("")
            tokenInput.setText("")
            statusText.text = "تنظیمات API پاک شد."
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
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(green)
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
            ).apply {
                topMargin = 16
            }
        }
    }

    private fun saveConfig(): Boolean {
        val url = baseUrlInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val token = tokenInput.text.toString().trim()

        if (url.isBlank() || model.isBlank()) {
            statusText.text = "آدرس و نام مدل را وارد کن."
            return false
        }

        if (!url.startsWith("https://", true)) {
            statusText.text = "آدرس باید با https:// شروع شود."
            return false
        }

        if (!ApiConfig.saveEndpoint(this, url, model)) {
            statusText.text = "ذخیره آدرس یا مدل ناموفق بود."
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

        statusText.text = "تنظیمات ذخیره شد."
        Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun testConnection() {
        if (!saveConfig()) return

        saveButton.isEnabled = false
        testButton.isEnabled = false
        statusText.text = "در حال اتصال به سرویس..."

        ApiClient.sendMessage(
            this,
            "سلام! فقط پاسخ بده: اتصال KING AI موفق است."
        ) { result ->

            saveButton.isEnabled = true
            testButton.isEnabled = true

            if (result.success) {
                statusText.text =
                    "اتصال موفق بود!\n\nپاسخ سرویس:\n${result.message}"
            } else {
                statusText.text =
                    "اتصال ناموفق بود:\n${result.message}"
            }
        }
    }
}
