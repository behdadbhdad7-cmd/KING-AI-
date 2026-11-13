
package com.kingai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
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
    private val bgColor = Color.rgb(5, 13, 10)
    private val cardColor = Color.rgb(13, 27, 21)
    private val white = Color.WHITE

    private lateinit var root: LinearLayout
    private lateinit var addAccountButton: Button

    private val statusViews = mutableMapOf<String, TextView>()
    private val logoutButtons = mutableMapOf<String, Button>()
    private val accountCards = mutableMapOf<String, View>()

    private val accountIds = listOf(
        TelegramAccountManager.ACCOUNT_ONE,
        TelegramAccountManager.ACCOUNT_TWO
    )

    private val accountNames = listOf("حساب اول", "حساب دوم")

    private val addedAccountIds = mutableSetOf<String>()

    private val preferences by lazy {
        getSharedPreferences("telegram_account_ui", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = TelegramAccountManager.get(this)

        addedAccountIds.addAll(
            preferences.getStringSet(
                "added_account_ids",
                setOf(TelegramAccountManager.ACCOUNT_ONE)
            ) ?: setOf(TelegramAccountManager.ACCOUNT_ONE)
        )

        addedAccountIds.retainAll(accountIds.toSet())
        addedAccountIds.add(TelegramAccountManager.ACCOUNT_ONE)
        saveAddedAccounts()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 25, 20, 20)
            setBackgroundColor(bgColor)
        }

        val scroll = ScrollView(this)
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)

        addText("مرکز حساب‌های تلگرام", 24f, green, true)
        addText("مدیریت حساب‌های مستقل تلگرام", 14f, Color.LTGRAY)

        // ساخت حساب‌های قبلی
        accountIds.forEachIndexed { index, id ->
            if (id in addedAccountIds) {
                addAccountCard(id, accountNames[index])
            }
        }

        // دکمه افزودن حساب
        addAccountButton = makeButton("افزودن حساب")

        addAccountButton.setOnClickListener {
            val nextId = accountIds.firstOrNull {
                it !in addedAccountIds
            }

            if (nextId == null) {
                Toast.makeText(
                    this,
                    "حداکثر دو حساب قابل افزودن است",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val index = accountIds.indexOf(nextId)

            addedAccountIds.add(nextId)
            saveAddedAccounts()

            addAccountCard(nextId, accountNames[index])
            updateAddButton()
        }

        root.addView(addAccountButton)

        // دکمه بازگشت
        val back = makeButton("بازگشت")
        back.setOnClickListener {
            finish()
        }
        root.addView(back)

        updateAddButton()

        // دریافت وضعیت حساب‌ها
        uiScope.launch {
            manager.accounts.collect { accounts ->
                accounts.values.forEach { info ->
                    statusViews[info.id]?.text =
                        "${info.name}\n${info.phone}\n${info.status}"

                    logoutButtons[info.id]?.visibility =
                        if (info.authorized) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                }
            }
        }
    }

    private fun saveAddedAccounts() {
        preferences.edit()
            .putStringSet(
                "added_account_ids",
                addedAccountIds.toSet()
            )
            .apply()
    }

    private fun updateAddButton() {
        addAccountButton.visibility =
            if (addedAccountIds.size >= accountIds.size) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }

    private fun addAccountCard(
        id: String,
        title: String
    ) {
        if (accountCards.containsKey(id)) return

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(cardColor)
        }

        val heading = TextView(this).apply {
            text = "●  $title"
            textSize = 19f
            setTextColor(green)
        }

        val status = TextView(this).apply {
            text = manager.accounts.value[id]?.status
                ?: "اتصال برقرار نشده"
            textSize = 14f
            setTextColor(white)
            setPadding(0, 12, 0, 12)
        }

        statusViews[id] = status

        // اتصال و ورود
        val connect = makeButton("اتصال / ورود به حساب")

        connect.setOnClickListener {
            if (manager.startAccount(id)) {
                showPhoneDialog(id)
            } else {
                status.text =
                    manager.accounts.value[id]?.status
                        ?: "خطا در اتصال"
            }
        }

        // وارد کردن شماره
        val phone = makeButton("وارد کردن شماره تلفن")

        phone.setOnClickListener {
            if (manager.startAccount(id)) {
                showPhoneDialog(id)
            } else {
                status.text =
                    manager.accounts.value[id]?.status
                        ?: "خطا در اتصال"
            }
        }

        // قطع اتصال بدون خروج از تلگرام
        val close = makeButton("قطع اتصال همین حساب")

        close.setOnClickListener {
            manager.closeAccount(id)
            status.text = "اتصال قطع شد؛ نشست ذخیره می‌ماند"
        }

        // خروج واقعی از تلگرام
        val logout = makeButton("خروج واقعی از تلگرام")

        logout.visibility =
            if (manager.accounts.value[id]?.authorized == true) {
                View.VISIBLE
            } else {
                View.GONE
            }

        logout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("خروج از تلگرام")
                .setMessage(
                    "آیا مطمئنی می‌خواهی از این حساب خارج شوی؟"
                )
                .setNegativeButton("لغو", null)
                .setPositiveButton("خروج") { _, _ ->
                    manager.logoutAccount(id)
                    status.text = "در حال خروج از تلگرام..."
                }
                .show()
        }

        logoutButtons[id] = logout

        // حذف حساب از KING AI
        val remove = makeButton("حذف حساب از KING AI")

        remove.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("حذف حساب")
                .setMessage(
                    "این کار حساب را از KING AI حذف می‌کند، " +
                    "از تلگرام خارج می‌شود و اطلاعات نشست محلی را پاک می‌کند. ادامه می‌دهی؟"
                )
                .setNegativeButton("لغو", null)
                .setPositiveButton("حذف") { _, _ ->
                    manager.removeAccount(id)

                    addedAccountIds.remove(id)
                    saveAddedAccounts()

                    removeAccountCard(id)
                    updateAddButton()
                }
                .show()
        }

        box.addView(heading)
        box.addView(status)
        box.addView(connect)
        box.addView(phone)
        box.addView(close)
        box.addView(logout)
        box.addView(remove)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = 20

        // اصلاح خطای lateinit:
        // هنگام ساخت اولین کارت، دکمه افزودن هنوز ساخته نشده است.
        if (
            ::addAccountButton.isInitialized &&
            root.indexOfChild(addAccountButton) >= 0
        ) {
            root.addView(
                box,
                root.indexOfChild(addAccountButton),
                params
            )
        } else {
            root.addView(box, params)
        }

        accountCards[id] = box
    }

    private fun removeAccountCard(id: String) {
        accountCards.remove(id)?.let {
            root.removeView(it)
        }

        statusViews.remove(id)
        logoutButtons.remove(id)
    }

    private fun showPhoneDialog(id: String) {
        val current = manager.accounts.value[id]

        when {
            current?.status?.contains("کد تأیید") == true -> {
                showInput(
                    "کد تأیید تلگرام",
                    "کد ارسال‌شده"
                ) {
                    manager.submitCode(id, it)
                }
            }

            current?.status?.contains("رمز دومرحله‌ای") == true -> {
                showInput(
                    "رمز دومرحله‌ای",
                    "رمز حساب"
                ) {
                    manager.submitPassword(id, it)
                }
            }

            current?.status?.contains("کد ایمیل") == true -> {
                showInput(
                    "کد ایمیل",
                    "کد ارسال‌شده به ایمیل"
                ) {
                    manager.submitEmailCode(id, it)
                }
            }

            current?.status?.contains("ایمیل") == true -> {
                showInput(
                    "ایمیل تأیید",
                    "آدرس ایمیل"
                ) {
                    manager.submitEmail(id, it)
                }
            }

            else -> {
                showInput(
                    "شماره تلفن",
                    "+989xxxxxxxxx"
                ) {
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

                if (value.isNotBlank()) {
                    callback(value)
                }
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

            if (bold) {
                setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
                )
            }

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
        uiScope.cancel()

        // مدیر تلگرام را اینجا dispose نمی‌کنیم.
        // نشست‌ها باید باقی بمانند.

        super.onDestroy()
    }
}
