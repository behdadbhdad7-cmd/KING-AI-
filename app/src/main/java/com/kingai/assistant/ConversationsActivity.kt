package com.kingai.assistant

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/** فهرست گفتگوهای قبلی و پنل ادامه‌دادن هر گفتگو */
class ConversationsActivity : ComponentActivity() {
    private val green = 0xFF39FF14.toInt()
    private val bg = 0xFF020604.toInt()
    private val muted = 0xFFA9CDB4.toInt()
    private lateinit var root: LinearLayout
    private var activeId: String? = null
    private var busy = false
    private lateinit var chatHost: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var composer: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showList()
    }

    private fun base(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(10))
        setBackgroundColor(bg)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun showList() {
        activeId = null
        root = base()
        root.addView(header("گفتگوهای قدیمی") { finish() }, lp(-1, -2))
        val sessions = ConversationStore.list(this)
        if (sessions.isEmpty()) {
            root.addView(label("هنوز گفتگوی ذخیره‌شده‌ای نداری. گفتگوهای جدیدت از این به بعد اینجا نمایش داده می‌شوند.", 14f, muted), lp(-1, -2).apply { topMargin = dp(24) })
        } else {
            val scroll = ScrollView(this)
            val host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            sessions.forEach { session ->
                val card = card()
                card.addView(label(session.title.ifBlank { "گفتگوی بدون عنوان" }, 15f, Color.WHITE, true))
                card.addView(label("${session.messages.count { it.role == "user" }} پیام  •  ${if (session.updatedAt > 0) clockTime(session.updatedAt) else ""}", 11f, muted), lp(-1, -2).apply { topMargin = dp(7) })
                val preview = session.messages.lastOrNull()?.content.orEmpty()
                if (preview.isNotBlank()) card.addView(label(preview.take(100), 12f, muted), lp(-1, -2).apply { topMargin = dp(6) })
                card.setOnClickListener { showConversation(session.id) }
                host.addView(card, lp(-1, -2).apply { topMargin = dp(10) })
            }
            scroll.addView(host)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        }
        setContentView(root)
    }

    private fun showConversation(id: String) {
        val session = ConversationStore.get(this, id) ?: run { showList(); return }
        activeId = id
        root = base()
        root.addView(header(session.title) { showList() }, lp(-1, -2))
        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chatHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        chatScroll.addView(chatHost)
        session.messages.forEach { addBubble(it.content, it.role == "user") }
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = outline()
            setPadding(dp(8), dp(5), dp(6), dp(5))
        }
        composer = EditText(this).apply {
            hint = "ادامه این گفتگو را بنویس..."
            setHintTextColor(0xFF789581.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false
            }
        }
        bar.addView(composer, LinearLayout.LayoutParams(0, dp(48), 1f))
        val send = label("➤", 22f, Color.BLACK, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(green) }
            setOnClickListener { send() }
        }
        bar.addView(send, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(bar, lp(-1, -2).apply { topMargin = dp(8) })
        setContentView(root)
        scrollBottom()
    }

    private fun send() {
        val id = activeId ?: return
        val question = composer.text.toString().trim()
        if (question.isEmpty() || busy) return
        composer.setText("")
        ConversationStore.add(this, id, "user", question)
        addBubble(question, true)
        busy = true
        val waiting = addBubble("در حال فکر کردن...", false)
        val history = ConversationStore.get(this, id)?.messages.orEmpty()
        ApiClient.sendConversation(this, history) { result ->
            if (isFinishing || isDestroyed) return@sendConversation
            busy = false
            chatHost.removeView(waiting)
            addBubble(result.message, false)
            if (result.success) ConversationStore.add(this, id, "assistant", result.message)
        }
    }

    private fun addBubble(value: String, user: Boolean): View {
        val bubble = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (user) Color.WHITE else 0xFFE7F5EA.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (user) 0xFF075D37.toInt() else 0xFF0A1510.toInt())
                setStroke(dp(1), if (user) 0xFF28C45A.toInt() else 0x6646A85A)
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        }
        val row = LinearLayout(this).apply {
            gravity = if (user) Gravity.START else Gravity.END
            setPadding(0, dp(4), 0, dp(4))
            addView(bubble)
        }
        chatHost.addView(row)
        scrollBottom()
        return row
    }

    private fun scrollBottom() {
        chatScroll.post { chatHost.post { chatScroll.smoothScrollTo(0, chatHost.height) } }
    }

    private fun header(title: String, back: () -> Unit): View {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val arrow = label("‹", 30f, green, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { back() }
        }
        bar.addView(arrow, LinearLayout.LayoutParams(dp(42), dp(44)))
        bar.addView(label(title, 17f, green, true), LinearLayout.LayoutParams(0, -2, 1f))
        return bar
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = outline()
        isClickable = true
        isFocusable = true
    }

    private fun outline() = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(0xFF09160F.toInt())
        setStroke(dp(1), 0xAA39FF14.toInt())
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
