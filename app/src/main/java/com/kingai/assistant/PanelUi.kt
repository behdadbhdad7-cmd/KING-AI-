package com.kingai.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ helpers

fun Context.vlp(topDp: Int): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(topDp) }

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

fun Activity.applyNeonWindow() {
    window.setBackgroundDrawable(ColorDrawable(Neon.BLACK))
    window.statusBarColor = Neon.BLACK
    window.navigationBarColor = Neon.BLACK
}

fun Activity.openOverlaySettings() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    )
}

fun Activity.openAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    )
}

fun Activity.openNotificationSettings() {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    startActivity(i)
}

fun Activity.confirmClearHistory(onDone: () -> Unit) {
    android.app.AlertDialog.Builder(this)
        .setTitle("پاک‌کردن تاریخچه")
        .setMessage("همه‌ی فرمان‌های ذخیره‌شده پاک شوند؟")
        .setPositiveButton("پاک کن") { _, _ ->
            HistoryStore.clear(this)
            onDone()
        }
        .setNegativeButton("انصراف", null)
        .show()
}

/** ساعت به‌صورت HH:mm با ارقام فارسی */
fun clockTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(millis)).fa()

/** تاریخ شمسی (ICU داخلی اندروید)؛ در صورت خطا تاریخ میلادی نشان داده می‌شود. */
fun persianDate(millis: Long, full: Boolean): String =
    try {
        val loc = android.icu.util.ULocale("fa_IR@calendar=persian")
        val pattern = if (full) "EEEE d MMMM y" else "y/MM/dd"
        android.icu.text.SimpleDateFormat(pattern, loc).format(Date(millis)).fa()
    } catch (e: Exception) {
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(millis)).fa()
    }

/** صفحه‌ی مشترک: پس‌زمینه‌ی شبکه‌ای + ScrollView + ستون محتوا (راست‌به‌چپ). */
fun neonScreen(activity: Activity): Pair<FrameLayout, LinearLayout> {
    val root = FrameLayout(activity)
    root.layoutDirection = View.LAYOUT_DIRECTION_RTL
    root.setBackgroundColor(Neon.BLACK)
    root.addView(GridBackground(activity), FrameLayout.LayoutParams(MATCH, MATCH))

    val scroll = ScrollView(activity)
    scroll.isFillViewport = true
    scroll.overScrollMode = View.OVER_SCROLL_NEVER
    scroll.clipChildren = false
    scroll.clipToPadding = false
    root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

    val content = LinearLayout(activity)
    content.orientation = LinearLayout.VERTICAL
    content.clipChildren = false
    content.setPadding(activity.dp(16), activity.dp(36), activity.dp(16), activity.dp(32))
    scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
    return Pair(root, content)
}

fun topBar(activity: Activity, title: String): LinearLayout {
    val row = LinearLayout(activity)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL

    val back = GlassIconButton(activity, KIcon.BACK, false, "بازگشت")
    back.setOnClickListener { activity.finish() }
    row.addView(back, LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)))

    val t = label(activity, title, 20f, Neon.GREEN, true)
    row.addView(
        t,
        LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = activity.dp(12) }
    )
    return row
}

fun permCard(
    c: Context,
    title: String,
    purpose: String,
    chipText: String,
    kind: ChipKind,
    buttonText: String?,
    onClick: (() -> Unit)?
): LinearLayout {
    val card = neonCard(c)
    val row = LinearLayout(c)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.addView(
        label(c, title, 16f, 0xFFFFFFFF.toInt(), true),
        LinearLayout.LayoutParams(0, WRAP, 1f)
    )
    row.addView(chip(c, chipText, kind))
    card.addView(row)
    card.addView(label(c, purpose, 13f, Neon.TEXT_DIM), c.vlp(8))
    if (buttonText != null && onClick != null) {
        val b = NeonButton(c, buttonText, true)
        b.setOnClickListener { onClick() }
        card.addView(b, c.vlp(14))
    }
    return card
}

// -------------------------------------------------------------------- icons

enum class KIcon {
    TORCH, GALLERY, WIFI, BLUETOOTH, CAMERA, MIC, SETTINGS, HISTORY, SEND, BACK, TRASH, APPS
}

/** آیکون‌های برداری که با Canvas روی شبکه‌ی ۲۴×۲۴ کشیده می‌شوند (بدون فایل تصویر). */
class IconView(context: Context, private val kind: KIcon, tint: Int = Neon.GREEN) : View(context) {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tint
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tint
    }
    private val path = Path()
    private val rect = RectF()

    fun setTint(color: Int) {
        stroke.color = color
        fill.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        stroke.strokeWidth = 2f

        when (kind) {
            KIcon.TORCH -> {
                path.reset()
                path.moveTo(8f, 2f)
                path.lineTo(16f, 2f)
                path.lineTo(16f, 6f)
                path.lineTo(14f, 9f)
                path.lineTo(10f, 9f)
                path.lineTo(8f, 6f)
                path.close()
                canvas.drawPath(path, stroke)
                rect.set(10f, 9f, 14f, 22f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke)
                canvas.drawLine(12f, 13f, 12f, 15f, stroke)
            }
            KIcon.GALLERY -> {
                rect.set(3f, 4f, 21f, 20f)
                canvas.drawRoundRect(rect, 3f, 3f, stroke)
                path.reset()
                path.moveTo(3f, 17f)
                path.lineTo(9f, 11f)
                path.lineTo(13f, 15f)
                path.lineTo(16f, 12f)
                path.lineTo(21f, 17f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(8f, 8.5f, 1.6f, fill)
            }
            KIcon.WIFI -> {
                val cx = 12f
                val cy = 19f
                for (r in floatArrayOf(4f, 8f, 12f)) {
                    rect.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawArc(rect, 225f, 90f, false, stroke)
                }
                canvas.drawCircle(cx, cy, 1.4f, fill)
            }
            KIcon.BLUETOOTH -> {
                path.reset()
                path.moveTo(7f, 7f)
                path.lineTo(17f, 17f)
                path.lineTo(12f, 22f)
                path.lineTo(12f, 2f)
                path.lineTo(17f, 7f)
                path.lineTo(7f, 17f)
                canvas.drawPath(path, stroke)
            }
            KIcon.CAMERA -> {
                rect.set(2f, 7f, 22f, 20f)
                canvas.drawRoundRect(rect, 3f, 3f, stroke)
                path.reset()
                path.moveTo(8f, 7f)
                path.lineTo(9.5f, 4f)
                path.lineTo(14.5f, 4f)
                path.lineTo(16f, 7f)
                canvas.drawPath(path, stroke)
                canvas.drawCircle(12f, 13.5f, 3.5f, stroke)
            }
            KIcon.MIC -> {
                rect.set(9f, 2f, 15f, 13f)
                canvas.drawRoundRect(rect, 3f, 3f, stroke)
                rect.set(5f, 6f, 19f, 18f)
                canvas.drawArc(rect, 0f, 180f, false, stroke)
                canvas.drawLine(12f, 18f, 12f, 22f, stroke)
                canvas.drawLine(8f, 22f, 16f, 22f, stroke)
            }
            KIcon.SETTINGS -> {
                canvas.drawCircle(12f, 12f, 3f, stroke)
                canvas.drawCircle(12f, 12f, 7.5f, stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = cos(a).toFloat()
                    val sn = sin(a).toFloat()
                    canvas.drawLine(
                        12f + c * 7.5f, 12f + sn * 7.5f,
                        12f + c * 10.5f, 12f + sn * 10.5f,
                        stroke
                    )
                }
            }
            KIcon.HISTORY -> {
                canvas.drawCircle(12f, 12f, 9f, stroke)
                path.reset()
                path.moveTo(12f, 7f)
                path.lineTo(12f, 12f)
                path.lineTo(16f, 14f)
                canvas.drawPath(path, stroke)
            }
            KIcon.SEND -> {
                path.reset()
                path.moveTo(3f, 3f)
                path.lineTo(21f, 12f)
                path.lineTo(3f, 21f)
                path.lineTo(6f, 12f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(6f, 12f, 21f, 12f, stroke)
            }
            KIcon.BACK -> {
                canvas.drawLine(5f, 12f, 19f, 12f, stroke)
                path.reset()
                path.moveTo(13f, 6f)
                path.lineTo(19f, 12f)
                path.lineTo(13f, 18f)
                canvas.drawPath(path, stroke)
            }
            KIcon.TRASH -> {
                canvas.drawLine(4f, 6f, 20f, 6f, stroke)
                path.reset()
                path.moveTo(9f, 6f)
                path.lineTo(9f, 3f)
                path.lineTo(15f, 3f)
                path.lineTo(15f, 6f)
                canvas.drawPath(path, stroke)
                path.reset()
                path.moveTo(6f, 6f)
                path.lineTo(7f, 21f)
                path.lineTo(17f, 21f)
                path.lineTo(18f, 6f)
                canvas.drawPath(path, stroke)
            }
            KIcon.APPS -> {
                rect.set(4f, 4f, 10f, 10f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke)
                rect.set(14f, 4f, 20f, 10f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke)
                rect.set(4f, 14f, 10f, 20f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke)
                rect.set(14f, 14f, 20f, 20f)
                canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke)
            }
        }
        canvas.restore()
    }
}

private fun View.pressFeedback() {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
        false
    }
}

/** دکمه‌ی گرد شیشه‌ای با آیکون */
class GlassIconButton(
    context: Context,
    kind: KIcon,
    filled: Boolean = false,
    desc: String = ""
) : FrameLayout(context) {

    init {
        isClickable = true
        isFocusable = true
        contentDescription = desc
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (filled) Neon.GREEN else 0x2239FF14)
            setStroke(context.dp(1), 0xAA39FF14.toInt())
        }
        val icon = IconView(context, kind, if (filled) Neon.BLACK else Neon.GREEN)
        val m = context.dp(11)
        addView(icon, LayoutParams(MATCH, MATCH).apply { setMargins(m, m, m, m) })
        pressFeedback()
    }
}

/** کاشی دسترسی سریع (آیکون + عنوان) با حالت فعال */
class QuickTile(context: Context, kind: KIcon, title: String) : LinearLayout(context) {

    private val icon = IconView(context, kind, Neon.GREEN_SOFT)
    private val titleView = label(context, title, 12f, Color.WHITE, true)
    private val bg = GradientDrawable()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = context.dp(92)
        setPadding(context.dp(6), context.dp(12), context.dp(6), context.dp(10))
        isClickable = true
        isFocusable = true
        bg.cornerRadius = context.dp(18).toFloat()
        applyState(false)
        background = bg

        addView(icon, LayoutParams(context.dp(30), context.dp(30)))
        titleView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        titleView.gravity = Gravity.CENTER
        titleView.maxLines = 2
        addView(titleView, LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(8) })
        pressFeedback()
    }

    private fun applyState(active: Boolean) {
        if (active) {
            bg.setColor(0x4439FF14)
            bg.setStroke(context.dp(2), Neon.GREEN)
            icon.setTint(Neon.GREEN)
        } else {
            bg.setColor(0x1A39FF14)
            bg.setStroke(context.dp(1), 0x6639FF14)
            icon.setTint(Neon.GREEN_SOFT)
        }
    }

    fun setActive(active: Boolean) {
        applyState(active)
    }

    fun setTitle(text: String) {
        titleView.text = text
    }
}
