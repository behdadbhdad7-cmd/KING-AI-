
package com.kingai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import kotlinx.coroutines.*

class TelegramAccountsActivity : Activity() {

    private val green = Color.rgb(0, 255, 130)
    private val bg = Color.rgb(5, 12, 9)
    private val cardColor = Color.rgb(15, 29, 22)

    private lateinit var manager: TelegramAccountManager
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = TelegramAccountManager(this)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "‹   مدیریت حساب‌های تلگرام"
            textSize = 22f
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finish() }
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "حساب‌ها مستقل از یکدیگر مدیریت می‌شوند"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 20)
        }
        root.addView(subtitle)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)

        uiScope.launch {
            manager.accounts.collect {
                renderAccounts()
            }
        }

        manager.startAccount(TelegramAccountManager.ACCOUNT_ONE)
        manager.startAccount(TelegramAccountManager.ACCOUNT_TWO)
    }

    private fun renderAccounts() {
        list.removeAllViews()

        val accounts = manager.accounts.value

        accounts.values.forEach { account ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                background = rounded(cardColor, green)
            }

            val name = TextView(this).apply {
                text = account.name
                textSize = 19f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            card.addView(name)

            val phone = TextView(this).apply {
                text = if (account.phone.isBlank()) "شماره: هنوز ثبت نشده"
                else "شماره: ${account.phone}"
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, 8, 0, 8)
            }
            card.addView(phone)

            val status = TextView(this).apply {
                text = "● ${account.status}"
                textSize = 13f
                setTextColor(green)
            }
            card.addView(status)

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val connect = makeButton(
                if (account.authorized) "متصل" else "اتصال / ورود"
            ) {
                if (!account.authorized) {
                    showLogin(account.id)
                }
            }

            val manage = makeButton("مدیریت") {
                showManagement(account.id)
            }

            val delete = makeButton("حذف حساب") {
                confirmDelete(account.id)
            }

            actions.addView(connect, weightParams())
            actions.addView(manage, weightParams())
            actions.addView(delete, weightParams())

            card.addView(actions)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = 16
            list.addView(card, params)
        }

        val add = makeButton("+ افزودن حساب تلگرام") {
            val free = accounts.values.firstOrNull {
                !it.authorized &&
                it.status != "در حال اتصال..."
            }

            if (free == null) {
                Toast.makeText(this, "هر دو جایگاه حساب استفاده شده‌اند", Toast.LENGTH_LONG).show()
            } else {
                showLogin(free.id)
            }
        }

        list.addView(add, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showManagement(id: String) {
        val account = manager.accounts.value[id] ?: return

        AlertDialog.Builder(this)
            .setTitle(account.name)
            .setMessage("وضعیت: ${account.status}\n\nمدیریت این حساب")
            .setPositiveButton("فعال‌سازی") { _, _ ->
                manager.startAccount(id)
            }
            .setNeutralButton("غیرفعال‌سازی") { _, _ ->
                manager.closeAccount(id)
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun confirmDelete(id: String) {
        AlertDialog.Builder(this)
            .setTitle("حذف حساب")
            .setMessage("آیا می‌خواهی نشست این حساب از دستگاه خارج شود؟ حساب دیگر دست‌نخورده می‌ماند.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف حساب") { _, _ ->
                manager.closeAccount(id)
                Toast.makeText(this, "نشست بسته شد", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLogin(id: String) {
        manager.startAccount(id)

        val input = EditText(this).apply {
            hint = "شماره تلفن با کد کشور"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        AlertDialog.Builder(this)
            .setTitle("ورود به حساب تلگرام")
            .setMessage("شماره تلفن را وارد کن")
            .setView(input)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ادامه") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.isNotBlank()) {
                    manager.submitPhone(id, phone)
                    showCode(id)
                }
            }
            .show()
    }

    private fun showCode(id: String) {
        val input = EditText(this).apply {
            hint = "کد تأیید تلگرام"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        AlertDialog.Builder(this)
            .setTitle("کد تأیید")
            .setView(input)
            .setNegativeButton("بعداً", null)
            .setPositiveButton("تأیید") { _, _ ->
                manager.submitCode(id, input.text.toString().trim())
                showPassword(id)
            }
            .show()
    }

    private fun showPassword(id: String) {
        val input = EditText(this).apply {
            hint = "در صورت داشتن رمز دومرحله‌ای"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        AlertDialog.Builder(this)
            .setTitle("رمز دومرحله‌ای")
            .setView(input)
            .setNegativeButton("فعلاً نه", null)
            .setPositiveButton("ارسال") { _, _ ->
                if (input.text.isNotBlank()) {
                    manager.submitPassword(id, input.text.toString())
                }
            }
            .show()
    }

    private fun makeButton(text: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(green)
            setPadding(8, 14, 8, 14)
            background = rounded(bg, green)
            setOnClickListener { action() }
        }
    }

    private fun weightParams() =
        LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(3, 12, 3, 0)
        }

    private fun rounded(color: Int, border: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = 18f
            setStroke(1, border)
        }

    override fun onDestroy() {
        uiScope.cancel()
        manager.dispose()
        super.onDestroy()
    }
}
