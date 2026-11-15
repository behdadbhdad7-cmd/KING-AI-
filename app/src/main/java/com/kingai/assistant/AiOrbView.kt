package com.kingai.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** گوی هولوگرافیک سبز KING AI برای صفحه‌ی آغازین */
class AiOrbView(context: Context) : View(context) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xAA39FF14.toInt()
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 5200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.27f
        val pulse = 1f + 0.035f * sin(phase * 2f)
        val r = radius * pulse

        glow.shader = RadialGradient(
            cx, cy, r * 1.9f,
            intArrayOf(0x6639FF14, 0x2239FF14, Color.TRANSPARENT),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.9f, glow)

        core.shader = RadialGradient(
            cx - r * 0.28f, cy - r * 0.34f, r * 1.35f,
            intArrayOf(0xFFB8FFAA.toInt(), 0xFF27D94B.toInt(), 0xFF03170A.toInt()),
            floatArrayOf(0f, 0.34f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, core)

        ring.alpha = 170
        canvas.save()
        canvas.rotate(Math.toDegrees(phase.toDouble()).toFloat(), cx, cy)
        canvas.drawOval(cx - r * 1.48f, cy - r * 0.43f, cx + r * 1.48f, cy + r * 0.43f, ring)
        canvas.restore()

        ring.alpha = 100
        canvas.save()
        canvas.rotate(-Math.toDegrees(phase * 0.72).toFloat(), cx, cy)
        canvas.drawOval(cx - r * 1.62f, cy - r * 0.52f, cx + r * 1.62f, cy + r * 0.52f, ring)
        canvas.restore()

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9FFAE.toInt() }
        for (i in 0 until 4) {
            val a = phase + i * (Math.PI / 2).toFloat()
            canvas.drawCircle(
                cx + cos(a) * r * 1.52f,
                cy + sin(a) * r * 0.47f,
                dp(3.2f),
                dotPaint
            )
        }
        glow.shader = null
        core.shader = null
    }
}
