package com.kingai.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class HomeActivity : ComponentActivity() {
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var orb: AiOrbView
    private var busy = false
    private val green = 0xFF39FF14.toInt()
    private val muted = 0xFFA9CDB4.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(2, 6, 4)
        window.navigationBarColor = Color.rgb(2, 6, 4)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF020604.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scrollPage = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(16), dp(18), dp(16), dp(100))
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scrollPage.addView(page)
        root.addView(scrollPage, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val crown = CrownView(this)
        header.addView(crown, LinearLayout.LayoutParams(dp(56), dp(54)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("KING AI", 23f, green, true))
        brand.addView(text("دستیار هوشمند شما", 11f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(9) })
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(green)
            background = glassBackground()
            contentDescription = "منوی اصلی"
            isClickable = true
            isFocusable = true
            setOnClickListener { showMenu(it) }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header, LinearLayout.LayoutParams(-1, -2))

        orb = AiOrbView(this)
        page.addView(orb, LinearLayout.LayoutParams(-1, dp(270)).apply {
            topMargin = dp(12)
        })
        val welcome = text("سلام! من KING AI هستم", 19f, green, true).apply {
            gravity = Gravity.CENTER
        }
        page.addView(welcome, LinearLayout.LayoutParams(-1, -2))
        page.addView(text("هر چیزی می‌خواهی بپرس یا یک فرمان بده", 13f, muted).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })

        scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        scroll.addView(messages)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
        })

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glassBackground()
            setPadding(dp(10), dp(7), dp(7), dp(7))
        }
        input = EditText(this).apply {
            hint = "پیام خود را بنویسید..."
            setHintTextColor(0xFF789581.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = null
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        val send = TextView(this).apply {
            text = "➤"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(green)
            }
            isClickable = true
            setOnClickListener { send() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        val bottom = FrameLayout(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(composer, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return root
    }

    private fun send() {
        if (busy) return
        val question = input.text.toString().trim()
        if (question.isEmpty()) return
        input.setText("")
        addBubble(question, true)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)

        val local = CommandRouter(this).handle(question)
        if (local.success) {
            addBubble(local.message, false)
            HistoryStore.add(this, question, local.message)
            return
        }
        if (!ApiConfig.hasToken(this) || ApiConfig.getBaseUrl(this).isBlank() ||
            ApiConfig.getModel(this).isBlank()) {
            addBubble("برای پاسخ‌گویی هوشمند، اتصال API را از منوی تنظیمات فعال کن.", false)
            return
        }

        busy = true
        addBubble("در حال فکر کردن...", false)
        ApiClient.sendMessage(this, question) { response ->
            if (isFinishing || isDestroyed) return@sendMessage
            busy = false
            val last = messages.getChildAt(messages.childCount - 1)
            if (last is TextView && last.text == "در حال فکر کردن...") messages.removeView(last)
            addBubble(response.message, false)
            HistoryStore.add(this, question, response.message)
        }
    }

    private fun addBubble(value: String, fromUser: Boolean) {
        val bubble = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (fromUser) Color.WHITE else 0xFFE7F5EA.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (fromUser) 0xFF087B42.toInt() else 0xFF0B1911.toInt())
                setStroke(dp(1), if (fromUser) green else 0x6646A85A)
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        }
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.START else Gravity.END
            setPadding(0, dp(5), 0, dp(5))
            addView(bubble)
        }
        messages.addView(row)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showMenu(anchor: View) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xF20A1B11.toInt())
                setStroke(dp(1), green)
            }
        }
        fun item(label: String, action: () -> Unit) {
            val v = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                isClickable = true
                setOnClickListener { action() }
            }
            menu.addView(v, LinearLayout.LayoutParams(dp(210), -2))
        }
        val popup = PopupWindow(menu, -2, -2, true).apply {
            elevation = dp(12).toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
        }
        item("گفت‌وگوی جدید") { messages.removeAllViews(); popup.dismiss() }
        item("تاریخچه فرمان‌ها") {
            popup.dismiss()
            startActivity(android.content.Intent(this, HistoryActivity::class.java))
        }
        item("تنظیمات اتصال هوشمند") {
            popup.dismiss()
            startActivity(android.content.Intent(this, ApiSettingsActivity::class.java))
        }
        item("ابزارها و امکانات برنامه") {
            popup.dismiss()
            startActivity(android.content.Intent(this, MainActivity::class.java))
        }
        popup.showAsDropDown(anchor, -dp(165), dp(6))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun glassBackground() = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(0xCC07120B.toInt())
        setStroke(dp(1), 0xCC39FF14.toInt())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
