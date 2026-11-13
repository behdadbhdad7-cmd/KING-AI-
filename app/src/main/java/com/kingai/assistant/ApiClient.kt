
package com.kingai.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors

object ApiClient {

    data class ApiResult(
        val success: Boolean,
        val message: String
    )

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val systemPrompt = """
        تو KING AI هستی؛ یک دستیار هوشمند حرفه‌ای.

        شخصیت و رفتار:
        - فارسی را طبیعی، روان و محاوره‌ای صحبت کن.
        - منظور واقعی کاربر را درک کن، نه فقط کلمات او را.
        - قبل از پاسخ، موضوع را تحلیل کن.
        - پاسخ دقیق، مفید و متناسب با سؤال ارائه بده.
        - در موضوعات پیچیده، مرحله‌به‌مرحله توضیح بده.
        - اگر اطلاعات کافی نداری، سؤال روشن‌کننده بپرس.
        - اگر پاسخ را نمی‌دانی، صادقانه بگو.
        - از جواب‌های کلیشه‌ای و تکراری خودداری کن.
        - لحن دوستانه، جدی، باهوش و محترمانه داشته باش.
        - خودت را یک دستیار هوشمند معرفی کن.
        - از ایموجی و استیکر استفاده نکن.
        - اطلاعات ساختگی تولید نکن.
        - پاسخ را متناسب با نیاز کاربر بنویس.

        اگر کاربر فارسی صحبت کرد، فارسی جواب بده.
        هدف تو کمک واقعی و دقیق به کاربر است.
    """.trimIndent()

    fun sendMessage(
        context: Context,
        message: String,
        callback: (ApiResult) -> Unit
    ) {
        executor.execute {
            val result = request(context, message)

            mainHandler.post {
                callback(result)
            }
        }
    }

    private fun request(
        context: Context,
        message: String
    ): ApiResult {

        val rawToken = ApiConfig.getToken(context)

        if (rawToken.isNullOrBlank()) {
            return ApiResult(false, "کلید API تنظیم نشده است.")
        }

        val token = rawToken.trim()
            .replace(
                Regex("^Bearer\\s+", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()

        val baseUrl = ApiConfig.getBaseUrl(context)
            .trim()
            .trimEnd('/')

        val model = ApiConfig.getModel(context).trim()

        if (token.isBlank()) {
            return ApiResult(false, "کلید API خالی است.")
        }

        if (baseUrl.isBlank() || model.isBlank()) {
            return ApiResult(
                false,
                "آدرس سرویس یا نام مدل تنظیم نشده است."
            )
        }

        if (!baseUrl.startsWith("https://", true)) {
            return ApiResult(
                false,
                "آدرس سرویس باید HTTPS باشد."
            )
        }

        val endpoint = when {
            baseUrl.endsWith("/chat/completions", true) ->
                baseUrl

            baseUrl.endsWith("/v1beta/openai", true) ->
                "$baseUrl/chat/completions"

            baseUrl.endsWith("/v1", true) ->
                "$baseUrl/chat/completions"

            else ->
                "$baseUrl/v1/chat/completions"
        }

        var lastResult = ApiResult(
            false,
            "ارتباط با سرویس برقرار نشد."
        )

        for (attempt in 0..2) {

            lastResult = performRequest(
                endpoint,
                token,
                model,
                message
            )

            if (lastResult.success) {
                return lastResult
            }

            val temporaryError =
                lastResult.message.contains("503") ||
                lastResult.message.contains("502") ||
                lastResult.message.contains("504") ||
                lastResult.message.contains("429")

            if (!temporaryError || attempt == 2) {
                return lastResult
            }

            try {
                Thread.sleep(1000L * (attempt + 1))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return ApiResult(
                    false,
                    "درخواست متوقف شد."
                )
            }
        }

        return lastResult
    }

    private fun performRequest(
        endpoint: String,
        token: String,
        model: String,
        message: String
    ): ApiResult {

        var connection: HttpURLConnection? = null

        return try {

            connection = URL(endpoint).openConnection()
                as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 45000
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val messages = JSONArray()
                .put(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                )
                .put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }
                )

            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("stream", false)
            }

            connection.outputStream.use { output ->
                output.write(
                    body.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val status = connection.responseCode

            val responseText = if (status in 200..299) {
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            } else {
                connection.errorStream?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()
            }

            if (status !in 200..299) {

                val detail = try {
                    val error = JSONObject(responseText)
                        .optJSONObject("error")

                    error?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: responseText
                } catch (_: Exception) {
                    responseText
                }

                return ApiResult(
                    false,
                    when (status) {
                        400 -> "درخواست نامعتبر: ${detail.take(250)}"
                        401 -> "کلید API نامعتبر است (401)."
                        403 -> "دسترسی به سرویس ممنوع است (403)."
                        404 -> "مدل یا مسیر پیدا نشد (404)."
                        429 -> "محدودیت سهمیه یا درخواست (429)."
                        500 -> "خطای داخلی سرور (500)."
                        502 -> "سرور موقتاً در دسترس نیست (502)."
                        503 -> "سرور Gemini موقتاً مشغول است (503)."
                        504 -> "زمان پاسخ سرور تمام شد (504)."
                        in 500..599 ->
                            "خطای سرور ($status): ${detail.take(200)}"
                        else ->
                            "خطای HTTP $status: ${detail.take(200)}"
                    }
                )
            }

            val json = JSONObject(responseText)

            val choices = json.optJSONArray("choices")
                ?: return ApiResult(
                    false,
                    "ساختار پاسخ سرویس قابل شناسایی نیست."
                )

            if (choices.length() == 0) {
                return ApiResult(
                    false,
                    "سرویس پاسخ خالی برگرداند."
                )
            }

            val content = choices
                .getJSONObject(0)
                .optJSONObject("message")
                ?.opt("content")

            val answer = when (content) {
                is String -> content

                is JSONArray -> {
                    val parts = mutableListOf<String>()

                    for (i in 0 until content.length()) {
                        val item = content.optJSONObject(i)
                        val text = item?.optString("text").orEmpty()

                        if (text.isNotBlank()) {
                            parts.add(text)
                        }
                    }

                    parts.joinToString("\n")
                }

                else -> ""
            }

            if (answer.isBlank()) {
                ApiResult(
                    false,
                    "پاسخ متنی دریافت نشد."
                )
            } else {
                ApiResult(true, answer.trim())
            }

        } catch (_: SocketTimeoutException) {
            ApiResult(
                false,
                "زمان اتصال تمام شد؛ دوباره تلاش کن."
            )
        } catch (_: UnknownHostException) {
            ApiResult(
                false,
                "اینترنت یا آدرس سرور در دسترس نیست."
            )
        } catch (e: Exception) {
            ApiResult(
                false,
                "خطای ارتباط: ${e.javaClass.simpleName}"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
