
package com.kingai.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
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
        فارسی را طبیعی، روان و محاوره‌ای صحبت کن.
        پاسخ‌ها دقیق، مفید، دوستانه و جدی باشند.
        از ایموجی و استیکر استفاده نکن.
        اطلاعات ساختگی نده.
        سؤال ساده را کوتاه و سؤال پیچیده را مرحله‌به‌مرحله جواب بده.
        اگر اطلاعات کافی نداری، سؤال روشن‌کننده بپرس.
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
            return ApiResult(false, "آدرس سرویس یا نام مدل تنظیم نشده است.")
        }

        if (!baseUrl.startsWith("https://", true)) {
            return ApiResult(false, "آدرس سرویس باید HTTPS باشد.")
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

        return performRequest(
            endpoint,
            token,
            model,
            message
        )
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
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
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
                put("temperature", 0.7)
                put("max_tokens", 512)
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
                return ApiResult(false, "سرویس پاسخ خالی برگرداند.")
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
                ApiResult(false, "پاسخ متنی دریافت نشد.")
            } else {
                ApiResult(true, answer.trim())
            }

        } catch (_: SocketTimeoutException) {
            ApiResult(
                false,
                "زمان پاسخ تمام شد؛ اینترنت یا سرور کند است."
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
