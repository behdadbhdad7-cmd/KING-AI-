package com.kingai.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class HomeActivity : ComponentActivity() {
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var brainHero: ImageView
    private lateinit var hero: LinearLayout
    private var busy = false
    private var activeConversationId: String? = null
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF020604.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brainMark = ImageView(this).apply {
            setImageResource(R.drawable.king_ai_brain_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "نشان هوش مصنوعی KING AI"
        }
        header.addView(brainMark, LinearLayout.LayoutParams(dp(48), dp(48)))
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

        hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        brainHero = ImageView(this).apply {
            setImageResource(R.drawable.king_ai_brain_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "مغز هوشمند KING AI"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x2200FF22)
                setStroke(dp(1), 0x5539FF14)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        hero.addView(brainHero, LinearLayout.LayoutParams(dp(136), dp(136)).apply {
            topMargin = dp(4)
            bottomMargin = dp(8)
        })
        val welcome = text("سلام! من KING AI هستم", 19f, green, true).apply {
            gravity = Gravity.CENTER
        }
        hero.addView(welcome, LinearLayout.LayoutParams(-1, -2))
        hero.addView(text("هر چیزی می‌خواهی بپرس یا یک فرمان بده", 13f, muted).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        page.addView(hero, LinearLayout.LayoutParams(-1, -2))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
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
            setSingleLine(true)
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
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun send() {
        if (busy) return
        val question = input.text.toString().trim()
        if (question.isEmpty()) return
        input.setText("")
        if (activeConversationId == null) activeConversationId = ConversationStore.create(this)
        val conversationId = activeConversationId!!
        ConversationStore.add(this, conversationId, "user", question)
        addBubble(question, true)
        if (hero.visibility != View.GONE) {
            hero.animate().cancel()
            hero.animate().alpha(0f).setDuration(160).withEndAction {
                hero.visibility = View.GONE
                hero.alpha = 1f
                scrollToBottom()
            }.start()
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)

        val local = CommandRouter(this).handle(question)
        if (local.success) {
            addBubble(local.message, false)
            ConversationStore.add(this, conversationId, "assistant", local.message)
            HistoryStore.add(this, question, local.message)
            return
        }
        if (!ApiConfig.hasToken(this) || ApiConfig.getBaseUrl(this).isBlank() ||
            ApiConfig.getModel(this).isBlank()) {
            val note = "برای پاسخ‌گویی هوشمند، اتصال API را از منوی تنظیمات فعال کن."
            addBubble(note, false)
            ConversationStore.add(this, conversationId, "assistant", note)
            return
        }

        busy = true
        val waitingRow = addBubble("در حال فکر کردن...", false)
        val history = ConversationStore.get(this, conversationId)?.messages.orEmpty()
        ApiClient.sendConversation(this, history) { response ->
            if (isFinishing || isDestroyed) return@sendConversation
            busy = false
            messages.removeView(waitingRow)
            addBubble(response.message, false)
            ConversationStore.add(this, conversationId, "assistant", response.message)
            HistoryStore.add(this, question, response.message)
        }
    }

    private fun addBubble(value: String, fromUser: Boolean): View {
        val bubble = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(if (fromUser) Color.WHITE else 0xFFE7F5EA.toInt())
            setLineSpacing(dp(2).toFloat(), 1.08f)
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (fromUser) 0xFF075D37.toInt() else 0xFF0A1510.toInt())
                setStroke(dp(1), if (fromUser) 0xFF28C45A.toInt() else 0x6646A85A)
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        }
        val row = LinearLayout(this).apply {
            gravity = if (fromUser) Gravity.START else Gravity.END
            setPadding(0, dp(5), 0, dp(5))
            addView(bubble)
        }
        messages.addView(row)
        scrollToBottom()
        return row
    }

    private fun scrollToBottom() {
        // صبر می‌کنیم اندازه‌ی حباب و ارتفاع ScrollView به‌روز شود.
        scroll.post {
            messages.post {
                scroll.smoothScrollTo(0, messages.height)
            }
        }
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
        val menuScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(menu)
        }
        val popupHeight = minOf(dp(480), resources.displayMetrics.heightPixels - dp(150))
        val popup = PopupWindow(menuScroll, -2, popupHeight, true).apply {
            elevation = dp(12).toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
        }
        item("گفت‌وگوی جدید") {
            activeConversationId = null
            messages.removeAllViews()
            hero.visibility = View.VISIBLE
            hero.alpha = 1f
            popup.dismiss()
        }
        item("گفتگوهای قدیمی") {
            popup.dismiss()
            startActivity(android.content.Intent(this, ConversationsActivity::class.java))
        }
        item("تاریخچه فرمان‌ها") {
            popup.dismiss()
            startActivity(android.content.Intent(this, HistoryActivity::class.java))
        }
        item("تنظیمات اتصال هوشمند") {
            popup.dismiss()
            startActivity(android.content.Intent(this, ApiSettingsActivity::class.java))
        }
        item("حافظه‌ی KING AI") {
            popup.dismiss()
            val saved = MemoryStore.all(this)
            android.app.AlertDialog.Builder(this)
                .setTitle("حافظه‌ی محلی")
                .setMessage(if (saved.isEmpty()) "حافظه خالی است." else saved.mapIndexed { i, v -> "${i + 1}. $v" }.joinToString("\n"))
                .setNegativeButton("بستن", null)
                .setNeutralButton("پاک‌کردن همه") { _, _ ->
                    android.app.AlertDialog.Builder(this)
                        .setMessage("همه‌ی موارد حافظه پاک شوند؟")
                        .setNegativeButton("لغو", null)
                        .setPositiveButton("پاک کن") { _, _ -> MemoryStore.clear(this) }
                        .show()
                }
                .show()
        }
        item("برنامه‌نویس KING") {
            popup.dismiss()
            startActivity(android.content.Intent(this, DeveloperActivity::class.java))
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
