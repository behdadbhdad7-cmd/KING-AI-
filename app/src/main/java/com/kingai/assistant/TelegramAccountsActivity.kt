
package com.kingai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class TelegramAccountsActivity : Activity() {

    private lateinit var manager: TelegramAccountManager
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val green = Color.rgb(0, 255, 130)
    private val bg = Color.rgb(5, 12, 10)
    private val card = Color.rgb(15, 29, 23)
    private val white = Color.WHITE
    private val muted = Color.rgb(165, 180, 171)

    private lateinit var root: LinearLayout

    private val ids = listOf(
        TelegramAccountManager.ACCOUNT_ONE,
        TelegramAccountManager.ACCOUNT_TWO
    )

    private val names = listOf("حساب ۱", "حساب ۲")

    private val added = mutableSetOf<String>()
    private val preferences by lazy {
        getSharedPreferences("telegram_account_ui", MODE_PRIVATE)
    }

    private var currentPage = "accounts"
    private var selectedId = TelegramAccountManager.ACCOUNT_ONE

    private var statusView: TextView? = null
    private var connectionView: TextView? = null
    private var continueButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = TelegramAccountManager.get(this)

        added.addAll(
            preferences.getStringSet(
                "added_account_ids",
                setOf(TelegramAccountManager.ACCOUNT_ONE)
            ) ?: setOf(TelegramAccountManager.ACCOUNT_ONE)
        )

        added.retainAll(ids.toSet())
        added.add(TelegramAccountManager.ACCOUNT_ONE)
        saveAdded()

        showAccountsPage()

        uiScope.launch {
            manager.accounts.collect { accounts ->
                if (currentPage == "accounts") {
                    refreshAccountPage()
                } else {
                    val info = accounts[selectedId]
                    statusView?.text = info?.status ?: "آماده اتصال"
                    connectionView?.text =
                        if (info?.authorized == true) "● حساب متصل است"
                        else "○ حساب هنوز وارد نشده است"

                    continueButton?.text =
                        authButtonText(info?.status, info?.authorized == true)
                }
            }
        }
    }

    private fun saveAdded() {
        preferences.edit()
            .putStringSet("added_account_ids", added.toSet())
            .apply()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeRoot(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        scroll.addView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scroll)
        root = layout
        return layout
    }

    private fun rounded(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(35, 65, 49))
        }
    }

    private fun addTitle(text: String, size: Float = 24f) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(view)
    }

    private fun addDescription(text: String) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(22))
        }
        root.addView(view)
    }

    private fun makeButton(
        text: String,
        primary: Boolean = true
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (primary) Color.BLACK else green)
            background = rounded(
                if (primary) green else card
            )
            isAllCaps = false
            minHeight = dp(52)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
    }

    private fun addButton(
        text: String,
        primary: Boolean = true,
        action: () -> Unit
    ): Button {
        val button = makeButton(text, primary)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(12)
        root.addView(button, params)
        button.setOnClickListener { action() }
        return button
    }

    private fun addCard(title: String, subtitle: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(card)
        }

        val heading = TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
        }

        val description = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        }

        box.addView(heading)
        box.addView(description)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(14)
        root.addView(box, params)

        return box
    }

    // صفحه انتخاب حساب‌ها

    private fun showAccountsPage() {
        currentPage = "accounts"
        makeRoot()

        addTitle("KING AI")
        addDescription("مرکز مدیریت حساب‌های تلگرام")

        ids.forEachIndexed { index, id ->
            if (id in added) {
                val info = manager.accounts.value[id]
                val connected = info?.authorized == true

                val box = addCard(
                    "مدیریت ${names[index]}",
                    if (connected) "● متصل به تلگرام"
                    else "برای ورود و مدیریت انتخاب کن"
                )

                box.setOnClickListener {
                    selectedId = id
                    showManagementPage(id)
                }

                box.isClickable = true
                box.isFocusable = true
            }
        }

        if (added.size < ids.size) {
            addButton("＋ افزودن حساب تلگرام") {
                val next = ids.firstOrNull { it !in added }
                if (next != null) {
                    added.add(next)
                    saveAdded()
                    selectedId = next
                    showManagementPage(next)
                }
            }
        }

        addButton("بازگشت", false) {
            finish()
        }
    }

    private fun refreshAccountPage() {
        if (currentPage == "accounts") {
            showAccountsPage()
        }
    }

    // پنل مستقل مدیریت هر حساب

    private fun showManagementPage(id: String) {
        currentPage = "management"
        selectedId = id
        makeRoot()

        val index = ids.indexOf(id)
        val title = names.getOrElse(index) { "حساب تلگرام" }
        val info = manager.accounts.value[id]
        val authorized = info?.authorized == true

        addTitle("مدیریت $title")
        addDescription("پنل اختصاصی دستیار تلگرام KING AI")

        val accountBox = addCard(
            "وضعیت حساب",
            if (authorized) {
                "${info?.name ?: ""}\n${info?.phone ?: ""}"
            } else {
                "هنوز به این حساب وارد نشده‌ای"
            }
        )

        connectionView = TextView(this).apply {
            text = if (authorized) "● حساب متصل است" else "○ حساب متصل نیست"
            textSize = 14f
            setTextColor(if (authorized) green else muted)
            setPadding(0, dp(12), 0, 0)
        }
        accountBox.addView(connectionView)

        statusView = TextView(this).apply {
            text = info?.status ?: "آماده اتصال"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(10), 0, 0)
        }
        accountBox.addView(statusView)

        if (!authorized) {
            continueButton = addButton(
                authButtonText(info?.status, false)
            ) {
                if (manager.startAccount(id)) {
                    showAuthInput(id)
                } else {
                    statusView?.text =
                        manager.accounts.value[id]?.status ?: "خطا در اتصال"
                }
            }
        } else {
            addCard(
                "دستیار هوشمند",
                "حساب با موفقیت متصل شده است. تنظیمات پاسخ‌گویی را انجام بده."
            )

            addCard(
                "پاسخ‌گویی خودکار",
                "فعال یا غیرفعال کردن پاسخ به پیام‌های خصوصی"
            )

            addButton("تنظیمات پاسخ‌گویی") {
                showReplySettings(id)
            }

            addCard(
                "شخصیت و لحن هوش مصنوعی",
                "انتخاب کن KING AI چگونه با مخاطبان صحبت کند"
            )

            addButton("تنظیم لحن و زبان پاسخ") {
                showReplySettings(id)
            }

            addButton("قطع اتصال حساب") {
                AlertDialog.Builder(this)
                    .setTitle("قطع اتصال")
                    .setMessage("نشست حساب حفظ می‌شود. فقط اتصال برنامه قطع خواهد شد.")
                    .setNegativeButton("لغو", null)
                    .setPositiveButton("قطع اتصال") { _, _ ->
                        manager.closeAccount(id)
                        showManagementPage(id)
                    }
                    .show()
            }

            addButton("خروج از حساب تلگرام", false) {
                AlertDialog.Builder(this)
                    .setTitle("خروج از تلگرام")
                    .setMessage("آیا می‌خواهی از این حساب خارج شوی؟")
                    .setNegativeButton("لغو", null)
                    .setPositiveButton("خروج") { _, _ ->
                        manager.logoutAccount(id)
                        showManagementPage(id)
                    }
                    .show()
            }
        }

        addButton("بازگشت به حساب‌ها", false) {
            showAccountsPage()
        }
    }

    private fun authButtonText(status: String?, authorized: Boolean): String {
        if (authorized) return "حساب متصل است"

        return when {
            status?.contains("کد تأیید") == true -> "وارد کردن کد تأیید"
            status?.contains("رمز دومرحله‌ای") == true -> "وارد کردن رمز دومرحله‌ای"
            status?.contains("کد ایمیل") == true -> "وارد کردن کد ایمیل"
            status?.contains("ایمیل") == true -> "وارد کردن ایمیل"
            else -> "اتصال یا ورود به حساب"
        }
    }

    // ورود مرحله‌ای

    private fun showAuthInput(id: String) {
        val info = manager.accounts.value[id]
        val status = info?.status ?: ""

        when {
            info?.authorized == true -> showManagementPage(id)

            status.contains("کد تأیید") -> {
                showInput("کد تأیید تلگرام", "کد ارسال‌شده") {
                    manager.submitCode(id, it)
                }
            }

            status.contains("رمز دومرحله‌ای") -> {
                showInput("رمز دومرحله‌ای", "رمز حساب") {
                    manager.submitPassword(id, it)
                }
            }

            status.contains("کد ایمیل") -> {
                showInput("کد تأیید ایمیل", "کد ارسال‌شده به ایمیل") {
                    manager.submitEmailCode(id, it)
                }
            }

            status.contains("ایمیل") -> {
                showInput("ایمیل", "آدرس ایمیل") {
                    manager.submitEmail(id, it)
                }
            }

            else -> showCountryAndPhone(id)
        }
    }

    private fun showCountryAndPhone(id: String) {
        val countries = arrayOf(
            "ایران  +98",
            "فنلاند  +358",
            "آلمان  +49",
            "ترکیه  +90",
            "آمریکا  +1",
            "روسیه  +7",
            "عراق  +964",
            "افغانستان  +93"
        )

        var code = "+98"

        AlertDialog.Builder(this)
            .setTitle("انتخاب کشور")
            .setItems(countries) { _, which ->
                code = countries[which].substringAfter("+").let { "+$it" }
                showPhoneInput(id, code)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showPhoneInput(id: String, code: String) {
        val input = EditText(this).apply {
            hint = "شماره تلفن بدون صفر اول"
            textSize = 18f
            setTextColor(white)
            setHintTextColor(muted)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("ورود به تلگرام")
            .setMessage("کد کشور: $code")
            .setView(input)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ادامه") { _, _ ->
                var number = input.text.toString().trim()
                    .replace(" ", "")
                    .replace("-", "")

                if (number.startsWith("0")) {
                    number = number.drop(1)
                }

                if (number.isNotBlank()) {
                    manager.submitPhone(id, code + number)
                } else {
                    Toast.makeText(this, "شماره را وارد کن", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showInput(
        title: String,
        hint: String,
        callback: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            this.hint = hint
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 17f
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("لغو", null)
            .setPositiveButton("تأیید") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotBlank()) callback(value)
            }
            .show()
    }

    // تنظیمات پاسخ‌گویی

    private fun showReplySettings(id: String) {
        val choices = arrayOf(
            "جدی و طبیعی",
            "رسمی",
            "دوستانه",
            "کوتاه و مستقیم"
        )

        AlertDialog.Builder(this)
            .setTitle("انتخاب لحن پاسخ")
            .setItems(choices) { _, which ->
                Toast.makeText(
                    this,
                    "لحن انتخاب‌شده: ${choices[which]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}
