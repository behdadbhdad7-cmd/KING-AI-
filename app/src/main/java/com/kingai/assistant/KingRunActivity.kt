
package com.kingai.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.random.Random

class KingRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(3, 12, 6)
        window.navigationBarColor = Color.rgb(3, 12, 6)
        setContentView(KingRunView(this))
    }

    inner class KingRunView(context: android.content.Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val prefs = getSharedPreferences("king_run", MODE_PRIVATE)

        private var playerY = 0f
        private var velocityY = 0f
        private var groundY = 0f
        private var playerSize = 0f

        private var obstacles = mutableListOf<RectF>()
        private var speed = 0f
        private var score = 0
        private var record = prefs.getInt("record", 0)

        private var started = false
        private var gameOver = false
        private var lastTime = 0L
        private var spawnTimer = 0f

        private val gravity = 2200f
        private val jumpPower = -850f

        private val green = Color.rgb(57, 255, 20)
        private val dark = Color.rgb(3, 12, 6)
        private val white = Color.WHITE

        private fun text(
            canvas: Canvas,
            value: String,
            x: Float,
            y: Float,
            size: Float,
            color: Int
        ) {
            paint.color = color
            paint.textSize = size
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL
            canvas.drawText(value, x, y, paint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            if (w <= 0 || h <= 0) return

            groundY = h * 0.78f
            playerSize = h * 0.075f

            canvas.drawColor(dark)

            paint.color = Color.rgb(8, 35, 15)
            canvas.drawCircle(w * 0.8f, h * 0.2f, h * 0.25f, paint)

            paint.color = green
            paint.strokeWidth = 3f
            canvas.drawLine(
                0f, groundY + 2f,
                w, groundY + 2f,
                paint
            )

            paint.color = Color.rgb(15, 65, 28)
            paint.strokeWidth = 2f

            for (i in 0..12) {
                val x = i * w / 12f
                canvas.drawLine(x, groundY + 5f, x - 25f, h, paint)
            }

            val playerX = w * 0.17f

            if (playerY == 0f) {
                playerY = groundY - playerSize
            }

            val playerRect = RectF(
                playerX,
                playerY,
                playerX + playerSize,
                playerY + playerSize
            )

            paint.color = green
            canvas.drawRoundRect(
                playerRect,
                12f, 12f,
                paint
            )

            paint.color = Color.BLACK
            canvas.drawCircle(
                playerX + playerSize * 0.68f,
                playerY + playerSize * 0.3f,
                playerSize * 0.07f,
                paint
            )

            for (obstacle in obstacles) {
                paint.color = Color.rgb(255, 75, 75)
                canvas.drawRoundRect(
                    obstacle,
                    7f, 7f,
                    paint
                )
            }

            text(
                canvas,
                "KING RUN",
                w / 2f,
                h * 0.12f,
                h * 0.055f,
                green
            )

            paint.textAlign = Paint.Align.LEFT
            paint.color = white
            paint.textSize = h * 0.035f
            canvas.drawText(
                "امتیاز: $score",
                25f,
                55f,
                paint
            )

            canvas.drawText(
                "رکورد: $record",
                25f,
                95f,
                paint
            )

            if (!started && !gameOver) {
                text(
                    canvas,
                    "برای شروع لمس کن",
                    w / 2f,
                    h * 0.48f,
                    h * 0.04f,
                    white
                )
            }

            if (gameOver) {
                text(
                    canvas,
                    "بازی تمام شد!",
                    w / 2f,
                    h * 0.42f,
                    h * 0.06f,
                    Color.RED
                )

                text(
                    canvas,
                    "امتیاز: $score",
                    w / 2f,
                    h * 0.49f,
                    h * 0.04f,
                    white
                )

                text(
                    canvas,
                    "برای شروع دوباره لمس کن",
                    w / 2f,
                    h * 0.57f,
                    h * 0.035f,
                    green
                )
            }

            if (started && !gameOver) {
                updateGame()
            }

            postInvalidateDelayed(16)
        }

        private fun updateGame() {
            val now = System.nanoTime()
            if (lastTime == 0L) {
                lastTime = now
                return
            }

            val dt = ((now - lastTime) / 1_000_000_000f)
                .coerceIn(0f, 0.04f)

            lastTime = now

            speed = 420f + score * 5f

            velocityY += gravity * dt
            playerY += velocityY * dt

            if (playerY + playerSize >= groundY) {
                playerY = groundY - playerSize
                velocityY = 0f
            }

            spawnTimer += dt

            if (spawnTimer > Random.nextFloat() * 0.7f + 0.8f) {
                spawnTimer = 0f

                val obstacleHeight = playerSize *
                    (0.7f + Random.nextFloat() * 0.7f)

                val x = width.toFloat() + 20f

                obstacles.add(
                    RectF(
                        x,
                        groundY - obstacleHeight,
                        x + playerSize * 0.7f,
                        groundY
                    )
                )
            }

            val playerRect = RectF(
                width * 0.17f,
                playerY,
                width * 0.17f + playerSize,
                playerY + playerSize
            )

            val iterator = obstacles.iterator()

            while (iterator.hasNext()) {
                val obstacle = iterator.next()

                obstacle.offset(-speed * dt, 0f)

                if (obstacle.right < 0f) {
                    iterator.remove()
                    score++
                    continue
                }

                val hitbox = RectF(
                    playerRect.left + playerSize * 0.12f,
                    playerRect.top + playerSize * 0.12f,
                    playerRect.right - playerSize * 0.12f,
                    playerRect.bottom - playerSize * 0.08f
                )

                if (RectF.intersects(hitbox, obstacle)) {
                    finishGame()
                    break
                }
            }
        }

        private fun finishGame() {
            gameOver = true
            started = false

            if (score > record) {
                record = score
                prefs.edit().putInt("record", record).apply()
            }
        }

        private fun resetGame() {
            obstacles.clear()
            score = 0
            speed = 420f
            spawnTimer = 0f
            velocityY = 0f
            playerY = groundY - playerSize
            lastTime = 0L
            gameOver = false
            started = true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                when {
                    gameOver -> resetGame()
                    !started -> resetGame()
                    playerY >= groundY - playerSize - 2f -> {
                        velocityY = jumpPower
                    }
                }
                return true
            }

            return true
        }
    }
}
