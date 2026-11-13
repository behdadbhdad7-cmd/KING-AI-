
package com.kingai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class TelegramAccountsActivity : Activity() {

    private lateinit var manager: TelegramAccountManager
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout

    private val uiScope = CoroutineScope(
        Dispatchers.Main + Job()
    )

    private val accountIds = listOf(
        TelegramAccountManager.ACCOUNT_ONE,
        TelegramAccountManager.ACCOUNT_TWO
    )

    private val accountNames = mapOf(
        TelegramAccountManager.ACCOUNT_ONE to "حساب تلگرام ۱",
        TelegramAccountManager.ACCOUNT_TWO to "حساب تلگرام ۲"
    )

    private val lastPrompt = mutableMapOf<String, String>()
    private var currentScreen = "accounts"
    private var selectedAccount = TelegramAccountManager.ACCOUNT_ONE

    private val backgroundColor = Color.rgb(12, 17, 20)
    private val cardColor = Color.rgb(23, 31, 34)
    private val greenColor = Color.rgb(0, 220, 145)
    private val whiteColor = Color.rgb(240, 246, 244)
    private val mutedColor = Color.rgb(155, 173, 168)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = TelegramAccountManager.get(this)

        buildBase()
        showAccountsScreen()

        uiScope.launch {
            manager.accounts.collect { accounts ->
                if (currentScreen == "accounts") {
                    showAccountsScreen()
                } else {
                    showAccountPanel(selectedAccount)
                }

                accountIds.forEach { id ->
                    val status = accounts[id]?.status ?: ""
                    handleLoginPrompt(id, status)
                }
            }
        }
    }

    private fun buildBase() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        setContentView(scroll)
    }

    private fun resetScreen(title: String, subtitle: String) {
        root.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(22))
        }

        header.addView(text(
            "KING AI",
            27f,
            greenColor,
            true
        ))

        header.addView(text(
            title,
            22f,
            whiteColor,
            true
        ).apply {
            setPadding(0, dp(12), 0, dp(5))
        })

        header.addView(text(
            subtitle,
            13f,
            mutedColor
        ))

        root.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(content)
    }

    private fun showAccountsScreen() {
        currentScreen = "accounts"

        resetScreen(
            "حساب‌های تلگرام",
            "حساب موردنظرت را انتخاب کن یا حساب جدید اضافه کن."
        )

        accountIds.forEachIndexed { index, id ->
            val info = manager.accounts.value[id]
                ?: TelegramAccountManager.AccountInfo(id)

            val card = createCard()

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val number = text(
                if (index == 0) "01" else "02",
                23f,
                greenColor,
                true
            )

            top.addView(number)

            val titleArea = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            titleArea.addView(text(
                accountNames[id] ?: "حساب تلگرام",
                16f,
                whiteColor,
                true
            ))

            titleArea.addView(text(
                info.phone.ifBlank { info.status },
                12f,
                mutedColor
            ).apply {
                setPadding(0, dp(5), 0, 0)
            })

            top.addView(titleArea)

            val statusDot = text(
                if (info.authorized) "●" else "○",
                19f,
                if (info.authorized) greenColor else mutedColor,
                true
            )

            top.addView(statusDot)
            card.addView(top)

            card.addView(text(
                info.status,
                12f,
                mutedColor
            ).apply {
                setPadding(0, dp(15), 0, dp(12))
            })

            card.addView(actionButton(
                if (info.authorized) "مدیریت این حساب" else "ورود و مدیریت حساب",
                true
            ) {
                selectedAccount = id

                if (!info.authorized) {
                    manager.startAccount(id)
                }

                showAccountPanel(id)
            })

            content.addView(card)
            animateIn(card)
        }

        val addCard = createCard()

        addCard.addView(text(
            "افزودن حساب تلگرام",
            17f,
            whiteColor,
            true
        ))

        addCard.addView(text(
            "برای اتصال حساب دوم از این گزینه استفاده کن.",
            12f,
            mutedColor
        ).apply {
            setPadding(0, dp(8), 0, dp(14))
        })

        addCard.addView(actionButton(
            "＋ افزودن حساب دوم",
            false
        ) {
            val first = manager.accounts.value[
                TelegramAccountManager.ACCOUNT_ONE
            ]

            val second = manager.accounts.value[
                TelegramAccountManager.ACCOUNT_TWO
            ]

            if (first?.authorized == true && second?.authorized == true) {
                showMessage("هر دو حساب قبلاً متصل شده‌اند.")
            } else {
                selectedAccount =
                    if (first?.authorized != true) {
                        TelegramAccountManager.ACCOUNT_ONE
                    } else {
                        TelegramAccountManager.ACCOUNT_TWO
                    }

                manager.startAccount(selectedAccount)
                showAccountPanel(selectedAccount)
            }
        })

        content.addView(addCard)
        animateIn(addCard)
    }

    private fun showAccountPanel(id: String) {
        currentScreen = "panel"
        selectedAccount = id

        val info = manager.accounts.value[id]
            ?: TelegramAccountManager.AccountInfo(id)

        resetScreen(
            accountNames[id] ?: "مدیریت حساب",
            "تنظیمات و وضعیت اتصال این حساب"
        )

        val statusCard = createCard()

        statusCard.addView(text(
            "وضعیت حساب",
            16f,
            whiteColor,
            true
        ))

        statusCard.addView(text(
            if (info.authorized) "متصل به تلگرام" else info.status,
            14f,
            if (info.authorized) greenColor else mutedColor,
            true
        ).apply {
            setPadding(0, dp(12), 0, dp(8))
        })

        statusCard.addView(text(
            "نام: ${info.name}",
            13f,
            whiteColor
        ))

        statusCard.addView(text(
            "شماره: ${info.phone.ifBlank { "هنوز ثبت نشده" }}",
            13f,
            mutedColor
        ).apply {
            setPadding(0, dp(6), 0, 0)
        })

        content.addView(statusCard)

        val loginCard = createCard()

        loginCard.addView(text(
            "اتصال تلگرام",
            16f,
            whiteColor,
            true
        ))

        loginCard.addView(actionButton(
            "شروع اتصال / ادامه ورود",
            true
        ) {
            manager.startAccount(id)
        })

        loginCard.addView(actionButton(
            "وارد کردن شماره تلفن",
            false
        ) {
            showPhoneDialog(id)
        })

        content.addView(loginCard)

        val featuresCard = createCard()

        featuresCard.addView(text(
            "امکانات حساب",
            16f,
            whiteColor,
            true
        ))

        featuresCard.addView(text(
            "این بخش پس از اتصال حساب، محل تنظیم پاسخ‌گویی و مشاهده پیام‌ها خواهد بود.",
            12f,
            mutedColor
        ).apply {
            setPadding(0, dp(8), 0, dp(12))
        })

        featuresCard.addView(actionButton(
            "تنظیمات پاسخ‌گویی هوشمند",
            false
        ) {
            showMessage(
                "تنظیمات پاسخ‌گویی هنوز به موتور هوش مصنوعی متصل نشده است."
            )
        })

        featuresCard.addView(actionButton(
            "پیام‌های دریافتی",
            false
        ) {
            showMessage(
                "دریافت و نمایش پیام‌ها هنوز در TelegramAccountManager پیاده‌سازی نشده است."
            )
        })

        content.addView(featuresCard)

        val bottomCard = createCard()

        bottomCard.addView(actionButton(
            "قطع اتصال موقت",
            false
        ) {
            manager.closeAccount(id)
            showAccountsScreen()
        })

        bottomCard.addView(actionButton(
            "خروج از حساب تلگرام",
            false
        ) {
            confirmAction(
                "از این حساب خارج شوی؟",
                "با خروج، نشست ورود این حساب حذف می‌شود."
            ) {
                manager.logoutAccount(id)
                showAccountsScreen()
            }
        })

        bottomCard.addView(actionButton(
            "بازگشت به انتخاب حساب",
            false
        ) {
            showAccountsScreen()
        })

        content.addView(bottomCard)

        animateIn(statusCard)
        animateIn(loginCard)
        animateIn(featuresCard)
        animateIn(bottomCard)
    }

    private fun showPhoneDialog(id: String) {
        val input = EditText(this).apply {
            hint = "+989123456789"
            setSingleLine(true)
            textSize = 16f
            setText("+98")
            setSelection(text.length)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        val box = dialogBox()
        box.addView(text(
            "شماره را با کد کشور وارد کن.",
            14f,
            whiteColor
        ))
        box.addView(input)

        AlertDialog.Builder(this)
            .setTitle("ورود به تلگرام")
            .setView(box)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ارسال کد", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
                            val phone = input.text.toString().trim()

                            if (!phone.startsWith("+") ||
                                phone.length < 8
                            ) {
                                input.error =
                                    "شماره را با کد کشور وارد کن"
                                return@setOnClickListener
                            }

                            manager.submitPhone(id, phone)
                            dialog.dismiss()
                        }
                }
                dialog.show()
            }
    }

    private fun handleLoginPrompt(id: String, status: String) {
        if (status.isBlank()) return

        val promptType = when {
            status.contains("کد تأیید ارسال شد") -> "code"
            status.contains("منتظر رمز دومرحله‌ای") -> "password"
            status.contains("منتظر ایمیل تأیید") -> "email"
            status.contains("منتظر کد ایمیل") -> "email_code"
            else -> return
        }

        if (lastPrompt[id] == promptType) return
        lastPrompt[id] = promptType

        when (promptType) {
            "code" -> showInputDialog(
                id,
                "کد تلگرام",
                "کدی که تلگرام برایت فرستاده وارد کن."
            ) { value ->
                manager.submitCode(id, value)
            }

            "password" -> showInputDialog(
                id,
                "رمز دومرحله‌ای",
                "رمز تأیید دومرحله‌ای تلگرام را وارد کن.",
                password = true
            ) { value ->
                manager.submitPassword(id, value)
            }

            "email" -> showInputDialog(
                id,
                "ایمیل تأیید",
                "آدرس ایمیلی که تلگرام درخواست کرده وارد کن."
            ) { value ->
                manager.submitEmail(id, value)
            }

            "email_code" -> showInputDialog(
                id,
                "کد ایمیل",
                "کدی که به ایمیلت ارسال شده وارد کن."
            ) { value ->
                manager.submitEmailCode(id, value)
            }
        }
    }

    private fun showInputDialog(
        id: String,
        title: String,
        message: String,
        password: Boolean = false,
        onSubmit: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            hint = "اینجا وارد کن"
            textSize = 16f
            setSingleLine(true)

            if (password) {
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        val box = dialogBox()

        box.addView(text(
            message,
            13f,
            mutedColor
        ))

        box.addView(input)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("بعداً", null)
            .setPositiveButton("تأیید", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
                            val value = input.text.toString().trim()

                            if (value.isBlank()) {
                                input.error = "این قسمت را پر کن"
                                return@setOnClickListener
                            }

                            onSubmit(value)
                            dialog.dismiss()
                        }
                }
                dialog.show()
            }
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = rounded(
                cardColor,
                20,
                Color.rgb(42, 61, 56)
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.bottomMargin = dp(14)
            layoutParams = params
        }
    }

    private fun actionButton(
        label: String,
        primary: Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(
                if (primary) Color.BLACK else whiteColor
            )
            background = rounded(
                if (primary) greenColor else Color.rgb(36, 49, 48),
                13
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(49)
            )

            params.topMargin = dp(9)
            layoutParams = params

            setOnClickListener { onClick() }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            typeface = if (bold) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
        }
    }

    private fun dialogBox(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(5))
        }
    }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()

            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun animateIn(view: View) {
        val animation = AlphaAnimation(0f, 1f).apply {
            duration = 280
            fillAfter = true
        }
        view.startAnimation(animation)
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("باشه", null)
            .show()
    }

    private fun confirmAction(
        title: String,
        message: String,
        action: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("تأیید") { _, _ ->
                action()
            }
            .show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}
