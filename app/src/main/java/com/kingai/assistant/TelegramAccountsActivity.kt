
package com.kingai.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class TelegramAccountsActivity : Activity() {

    private lateinit var manager: TelegramAccountManager
    private val uiScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private val green = Color.rgb(0, 255, 130)
    private val background = Color.rgb(5, 13, 10)
    private val card = Color.rgb(13, 27, 21)
    private val white = Color.WHITE
    private lateinit var root: LinearLayout
    private lateinit var statusViews: MutableMap<String, TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = TelegramAccountManager(this)
        statusViews = mutableMapOf()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 25, 20, 20)
            setBackgroundColor(background)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        addText("مرکز حساب‌های تلگرام", 24f, green, true)
        addText(
            "مدیریت دو حساب مستقل تلگرام",
            14f,
            Color.LTGRAY
        )

        addAccountCard(TelegramAccountManager.ACCOUNT_ONE, "حساب اول")
        addAccountCard(TelegramAccountManager.ACCOUNT_TWO, "حساب دوم")

        val back = makeButton("بازگشت")
        back.setOnClickListener { finish() }
        root.addView(back)

        uiScope.launch {
            manager.accounts.collect { accounts ->
                accounts.values.forEach { info ->
                    statusViews[info.id]?.text =
                        "${info.name}\n${info.phone}\n${info.status}"
                }
            }
        }
    }

    private fun addAccountCard(id: String, title: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(card)
        }

        val heading = TextView(this).apply {
            text = "●  $title"
            textSize = 19f
            setTextColor(green)
        }

        val status = TextView(this).apply {
            text = "اتصال برقرار نشده"
            textSize = 14f
            setTextColor(white)
            setPadding(0, 12, 0, 12)
        }

        statusViews[id] = status

        val connect = makeButton("اتصال / ورود به حساب")
        connect.setOnClickListener {
            if (manager.startAccount(id)) {
                showPhoneDialog(id)
            } else {
                status.text =
                    manager.accounts.value[id]?.status ?: "خطا در اتصال"
            }
        }

        val phone = makeButton("وارد کردن شماره تلفن")
        phone.setOnClickListener { showPhoneDialog(id) }

        val close = makeButton("قطع اتصال همین حساب")
        close.setOnClickListener {
            manager.closeAccount(id)
        }

        box.addView(heading)
        box.addView(status)
        box.addView(connect)
        box.addView(phone)
        box.addView(close)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 20
        root.addView(box, params)
    }

    private fun showPhoneDialog(id: String) {
        val current = manager.accounts.value[id]

        when {
            current?.status?.contains("کد تأیید") == true -> {
                showInput("کد تأیید تلگرام", "کد ارسال‌شده") {
                    manager.submitCode(id, it)
                }
            }

            current?.status?.contains("رمز دومرحله‌ای") == true -> {
                showInput("رمز دومرحله‌ای", "رمز حساب") {
                    manager.submitPassword(id, it)
                }
            }

            else -> {
                showInput("شماره تلفن", "+989xxxxxxxxx") {
                    manager.submitPhone(id, it)
                }
            }
        }
    }

    private fun showInput(
        title: String,
        hint: String,
        callback: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            this.hint = hint
            setTextColor(white)
            setHintTextColor(Color.GRAY)
            textSize = 16f
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ارسال") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotBlank()) callback(value)
            }
            .show()
    }

    private fun addText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
        root.addView(view)
    }

    private fun makeButton(title: String): Button {
        return Button(this).apply {
            text = title
            setTextColor(Color.BLACK)
            setBackgroundColor(green)
            textSize = 14f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        manager.dispose()
    }
}
