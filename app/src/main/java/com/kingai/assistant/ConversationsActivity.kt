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
import androidx.activity.result.contract.ActivityResultContracts

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
    private var pendingExport = ""
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && pendingExport.isNotBlank()) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray(Charsets.UTF_8)) }
                android.widget.Toast.makeText(this, "فایل ذخیره شد.", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "ذخیره فایل ناموفق بود.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        pendingExport = ""
    }

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
        session.messages.forEach { addBubble(it.content, it.role == "user", it.role == "assistant") }
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
            textSize = getChatTextSize()\n            typeface = getChatTypeface()\n            background = null
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
            addBubble(result.message, false, true)
            if (result.success) ConversationStore.add(this, id, "assistant", result.message)
        }
    }

    private fun addBubble(value: String, user: Boolean, canSave: Boolean = false): View {
        if (value.startsWith("[[KING_IMAGE:") && value.endsWith("]]")) {
            val uriText = value.removePrefix("[[KING_IMAGE:").removeSuffix("]]")
            val image = android.widget.ImageView(this).apply {
                setImageURI(android.net.Uri.parse(uriText))
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                contentDescription = "عکس گرفته‌شده با KING AI"
                setOnClickListener {
                    try {
                        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            .setDataAndType(android.net.Uri.parse(uriText), "image/jpeg")
                        startActivity(viewIntent)
                    } catch (_: Exception) { }
                }
            }
            val imageRow = LinearLayout(this).apply {
                gravity = Gravity.END
                setPadding(0, dp(4), 0, dp(4))
                addView(image, LinearLayout.LayoutParams(dp(240), dp(240)))
            }
            chatHost.addView(imageRow)
            scrollBottom()
            return imageRow
        }
        val bubble = TextView(this).apply {
            text = value
            textSize = getChatTextSize()\n            typeface = getChatTypeface()\n            setTextColor(if (user) Color.WHITE else 0xFFE7F5EA.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (user) 0xFF075D37.toInt() else 0xFF0A1510.toInt())
                setStroke(dp(1), if (user) 0xFF28C45A.toInt() else 0x6646A85A)
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (user) Gravity.START else Gravity.END
            addView(bubble)
            if (canSave) {
                val fence = 96.toChar().toString().repeat(3)
                val html = Regex(fence + "(?:html)?\\s*([\\s\\S]*?)" + fence, RegexOption.IGNORE_CASE)
                    .find(value)?.groupValues?.getOrNull(1)?.trim()
                val isHtml = html != null && html.contains(Regex("<\\s*(html|!doctype)", RegexOption.IGNORE_CASE))
                val payload = if (isHtml) html!! else value
                val fileName = if (isHtml) "KING-AI-Website.html" else "KING-AI-Answer.txt"
                addView(label("↓  ذخیره فایل  •  " + fileName, 12f, green).apply {
                    setPadding(dp(10), dp(8), dp(10), dp(5))
                    setOnClickListener {
                        pendingExport = payload
                        exportLauncher.launch(fileName)
                    }
                })
            }
        }
        val row = LinearLayout(this).apply {
            gravity = if (user) Gravity.START else Gravity.END
            setPadding(0, dp(4), 0, dp(4))
            addView(content)
        }
        chatHost.addView(row)
        scrollBottom()
        return row
    }


    private fun appearancePrefs() = getSharedPreferences("king_chat_appearance", MODE_PRIVATE)

    private fun getChatTextSize(): Float =
        appearancePrefs().getFloat("text_size", 16f).coerceIn(14f, 22f)

    private fun getChatTypeface(): Typeface {
        return when (appearancePrefs().getString("font_family", "default")) {
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }

    private fun showTextAppearanceDialog() {
        val sizes = arrayOf("۱۴", "۱۶", "۱۸", "۲۰", "۲۲")
        val values = floatArrayOf(14f, 16f, 18f, 20f, 22f)
        val current = getChatTextSize()
        val selected = values.indices.minByOrNull { kotlin.math.abs(values[it] - current) } ?: 1
        android.app.AlertDialog.Builder(this)
            .setTitle("اندازه نوشته گفتگو")
            .setSingleChoiceItems(sizes, selected) { dialog, which ->
                appearancePrefs().edit().putFloat("text_size", values[which]).apply()
                dialog.dismiss()
                activeId?.let { showConversation(it) }
            }
            .setNegativeButton("بستن", null)
            .setNeutralButton("انتخاب فونت") { _, _ ->
                showFontFamilyDialog()
            }
            .show()
    }

    private fun showFontFamilyDialog() {
        val names = arrayOf("پیش‌فرض دستگاه", "سریف", "تک‌فاصله")
        val keys = arrayOf("default", "serif", "mono")
        val current = appearancePrefs().getString("font_family", "default")
        val selected = keys.indexOf(current).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("فونت گفتگو")
            .setSingleChoiceItems(names, selected) { dialog, which ->
                appearancePrefs().edit().putString("font_family", keys[which]).apply()
                dialog.dismiss()
                activeId?.let { showConversation(it) }
            }
            .setNegativeButton("بستن", null)
            .show()
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
        val fontButton = label("Aa", 16f, green, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = outline()
            contentDescription = "تنظیم فونت و اندازه نوشته"
            setOnClickListener { showTextAppearanceDialog() }
        }
        bar.addView(fontButton, LinearLayout.LayoutParams(dp(52), dp(42)))
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
