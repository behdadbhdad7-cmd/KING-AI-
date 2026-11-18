package com.kingai.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** رنگ‌های سبز نئونی و مشکی KING AI */
object Neon {
    val BLACK = 0xFF020604.toInt()
    val PANEL_TOP = 0xFF0E2418.toInt()
    val PANEL_BOTTOM = 0xFF040C08.toInt()
    val GREEN = 0xFF39FF14.toInt()
    val GREEN_SOFT = 0xFF9BFF8A.toInt()
    val GREEN_DEEP = 0xFF11A82E.toInt()
    val GREEN_DARK = 0xFF06501A.toInt()
    val TEXT_DIM = 0xFFA9CDB4.toInt()
    val DANGER = 0xFFFF5252.toInt()
    val AMBER = 0xFFFFC107.toInt()
}

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

/** تبدیل رقم‌های انگلیسی به فارسی */
fun String.fa(): String = buildString {
    for (ch in this@fa) {
        if (ch in '0'..'9') append('\u06F0' + (ch - '0')) else append(ch)
    }
}

/** سایه‌ی سبز درخشان (روی اندروید ۹ به بالا رنگ سبز می‌گیرد) */
fun View.neonGlow(elevationDp: Int) {
    elevation = context.dp(elevationDp).toFloat()
    if (Build.VERSION.SDK_INT >= 28) {
        outlineSpotShadowColor = Neon.GREEN
        outlineAmbientShadowColor = Neon.GREEN
    }
}

/** انیمیشن ورود: محو شدن + بالا آمدن نرم */
fun View.enter(delayMs: Long, offsetDp: Int = 28) {
    alpha = 0f
    translationY = context.dp(offsetDp).toFloat()
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delayMs)
        .setDuration(500)
        .setInterpolator(DecelerateInterpolator(1.6f))
        .start()
}

fun label(c: Context, text: String, sp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(c).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        setLineSpacing(0f, 1.2f)
    }

enum class ChipKind { ON, OFF, INFO }

/** برچسب وضعیت (فعال / غیرفعال / اطلاعات) */
fun chip(c: Context, text: String, kind: ChipKind): TextView {
    val col = when (kind) {
        ChipKind.ON -> Neon.GREEN
        ChipKind.OFF -> Neon.DANGER
        ChipKind.INFO -> Neon.AMBER
    }
    return label(c, text, 12f, col, true).apply {
        setPadding(c.dp(10), c.dp(4), c.dp(10), c.dp(4))
        background = GradientDrawable().apply {
            cornerRadius = c.dp(12).toFloat()
            setColor((col and 0x00FFFFFF) or 0x22000000)
            setStroke(c.dp(1), col)
        }
    }
}

/** کارت سه‌بعدی تیره با لبه و سایه‌ی سبز */
fun neonCard(c: Context): LinearLayout = LinearLayout(c).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(c.dp(16), c.dp(14), c.dp(16), c.dp(16))
    clipChildren = false
    background = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Neon.PANEL_TOP, Neon.PANEL_BOTTOM)
    ).apply {
        cornerRadius = c.dp(20).toFloat()
        setStroke(c.dp(1), 0x9939FF14.toInt())
    }
    neonGlow(8)
}

fun neonInput(c: Context, hintText: String): EditText = EditText(c).apply {
    hint = hintText
    setHintTextColor(Neon.TEXT_DIM)
    setTextColor(Color.WHITE)
    textSize = 15f
    inputType = InputType.TYPE_CLASS_TEXT
    maxLines = 1
    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    setPadding(c.dp(14), c.dp(12), c.dp(14), c.dp(12))
    background = GradientDrawable().apply {
        cornerRadius = c.dp(14).toFloat()
        setColor(Neon.BLACK)
        setStroke(c.dp(2), Neon.GREEN_DEEP)
    }
}

/**
 * دکمه‌ی سه‌بعدی: یک «صفحه‌ی روی دکمه» سبز درخشان و یک «لبه‌ی عمق» تیره زیر آن.
 * هنگام لمس، صفحه پایین می‌رود و هنگام رها کردن با انیمیشن نرم برمی‌گردد.
 */
class NeonButton(context: Context, label: String, compact: Boolean = false) : FrameLayout(context) {
    private val depthPx = context.dp(6)
    private val face = TextView(context)

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
        val radius = context.dp(16).toFloat()

        val depth = View(context)
        depth.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Neon.GREEN_DARK)
            setStroke(context.dp(1), Neon.GREEN_DEEP)
        }
        addView(depth, FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = depthPx })

        val vPad = context.dp(if (compact) 10 else 15)
        face.text = label
        face.gravity = Gravity.CENTER
        face.setTextColor(Color.BLACK)
        face.typeface = Typeface.DEFAULT_BOLD
        face.textSize = if (compact) 14f else 17f
        face.setPadding(context.dp(18), vPad, context.dp(18), vPad)
        face.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Neon.GREEN_SOFT, Neon.GREEN, Neon.GREEN_DEEP)
        ).apply {
            cornerRadius = radius
            setStroke(context.dp(1), 0xFFD6FFCC.toInt())
        }
        face.neonGlow(8)
        addView(face, FrameLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = depthPx })

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    press(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press(false)
            }
            false
        }
    }

    private fun press(down: Boolean) {
        face.animate().cancel()
        if (down) {
            face.animate()
                .translationY(depthPx * 0.8f)
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(70)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            face.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }
    }

    fun setLabel(text: String) {
        face.text = text
    }

    fun setDimmed(dimmed: Boolean) {
        alpha = if (dimmed) 0.5f else 1f
    }
}

/** نشان اختصاصی هسته‌ی هوشمند KING AI؛ مدار عصبی مینیمال با نور سبز */
class CrownView(context: Context) : View(context) {
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Neon.GREEN
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Neon.GREEN_SOFT
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulseValue = 0.55f
    private var pulse: ValueAnimator? = null

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        contentDescription = "هسته هوشمند KING AI"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulse = ValueAnimator.ofFloat(0.35f, 1f).apply {
            duration = 1900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val scale = size / 100f
        val cx = width / 2f
        val cy = height / 2f
        canvas.save()
        canvas.translate(cx - 50f * scale, cy - 50f * scale)
        canvas.scale(scale, scale)

        // هاله‌ی مرکزی
        glowPaint.shader = RadialGradient(
            50f, 50f, 43f,
            intArrayOf(0x6639FF14, 0x2239FF14, Color.TRANSPARENT),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(50f, 50f, 43f, glowPaint)

        // شش‌ضلعی مدار
        val hex = Path().apply {
            moveTo(50f, 12f)
            lineTo(82f, 31f)
            lineTo(82f, 69f)
            lineTo(50f, 88f)
            lineTo(18f, 69f)
            lineTo(18f, 31f)
            close()
        }
        linePaint.alpha = (130 + 100 * pulseValue).toInt()
        linePaint.strokeWidth = 2.1f
        canvas.drawPath(hex, linePaint)

        // مسیرهای اتصال به هسته
        val routes = listOf(
            floatArrayOf(50f, 12f, 50f, 31f),
            floatArrayOf(82f, 31f, 65f, 41f),
            floatArrayOf(82f, 69f, 65f, 59f),
            floatArrayOf(50f, 88f, 50f, 69f),
            floatArrayOf(18f, 69f, 35f, 59f),
            floatArrayOf(18f, 31f, 35f, 41f)
        )
        for (p in routes) canvas.drawLine(p[0], p[1], p[2], p[3], linePaint)

        // هسته‌ی سه‌بعدی
        corePaint.shader = RadialGradient(
            43f, 39f, 24f,
            intArrayOf(0xFFE5FFE0.toInt(), Neon.GREEN, 0xFF087B2B.toInt()),
            floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP
        )
        corePaint.setShadowLayer(7f + 10f * pulseValue, 0f, 0f, Neon.GREEN)
        canvas.drawCircle(50f, 50f, 17f, corePaint)

        // گره‌های مدار
        val nodes = arrayOf(
            floatArrayOf(50f, 12f), floatArrayOf(82f, 31f),
            floatArrayOf(82f, 69f), floatArrayOf(50f, 88f),
            floatArrayOf(18f, 69f), floatArrayOf(18f, 31f)
        )
        for (p in nodes) {
            canvas.drawCircle(p[0], p[1], 4.2f, glowPaint.apply {
                color = 0x5539FF14
                shader = null
            })
            canvas.drawCircle(p[0], p[1], 2.8f, nodePaint)
        }
        canvas.drawCircle(50f, 50f, 4f, nodePaint)

        glowPaint.shader = null
        corePaint.shader = null
        corePaint.clearShadowLayer()
        canvas.restore()
    }
}

/** پس‌زمینه‌ی مشکی عمیق با شبکه‌ی سبز و خط اسکن متحرک */
class GridBackground(context: Context) : View(context) {
    private val gridPaint = Paint().apply {
        color = 0x2039FF14
        strokeWidth = 1f
    }
    private val glowPaint = Paint()
    private val scanPaint = Paint()
    private val step = context.dp(36).toFloat()
    private val band = context.dp(70).toFloat()
    private var t = 0f
    private var anim: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowPaint.shader = RadialGradient(
            w / 2f, h * 0.18f, h * 0.65f,
            intArrayOf(0x6039FF14, 0x0039FF14), null, Shader.TileMode.CLAMP
        )
        scanPaint.shader = LinearGradient(
            0f, 0f, 0f, band,
            intArrayOf(0x0039FF14, 0x4439FF14), null, Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Neon.BLACK)
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }

        canvas.save()
        canvas.translate(0f, t * (h + band) - band)
        canvas.drawRect(0f, 0f, w, band, scanPaint)
        canvas.restore()
    }
}
