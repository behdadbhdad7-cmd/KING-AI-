package com.kingai.assistant

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * ارتباط با سرویس‌های هوش مصنوعی سازگار با OpenAI API
 */
object ApiClient {

    data class ApiResult(
        val success: Boolean,
        val message: String
    )

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendMessage(
        context: android.content.Context,
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
        context: android.content.Context,
        message: String
    ): ApiResult {

        val token = ApiConfig.getToken(context)
        val baseUrl = ApiConfig.getBaseUrl(context)
        val model = ApiConfig.getModel(context)

        if (token.isNullOrBlank()) {
            return ApiResult(false, "کلید API تنظیم نشده است.")
        }

        if (baseUrl.isBlank() || model.isBlank()) {
            return ApiResult(false, "آدرس سرویس یا نام مدل تنظیم نشده است.")
        }

        if (!baseUrl.startsWith("https://", true)) {
            return ApiResult(false, "آدرس سرویس باید HTTPS باشد.")
        }

        var connection: HttpURLConnection? = null

        return try {
            val endpoint = if (
                baseUrl.endsWith("/v1", true)
            ) {
                "$baseUrl/chat/completions"
            } else {
                "$baseUrl/v1/chat/completions"
            }

            connection = URL(endpoint).openConnection()
                as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val body = JSONObject().apply {
                put("model", model)

                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", message)
                        }
                    )
                )

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
                    JSONObject(responseText)
                        .optJSONObject("error")
                        ?.optString("message")
                        .orEmpty()
                } catch (_: Exception) {
                    ""
                }

                return ApiResult(
                    false,
                    when (status) {
                        401 -> "کلید API اشتباه یا نامعتبر است."
                        403 -> "سرویس اجازه دسترسی نداده است."
                        404 -> "آدرس یا مسیر API پیدا نشد."
                        429 -> "محدودیت درخواست یا اعتبار سرویس."
                        in 500..599 -> "سرور هوش مصنوعی خطا داده است."
                        else -> "خطای HTTP $status: " +
                            detail.take(250)
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
                ApiResult(false, "پاسخ متنی دریافت نشد.")
            } else {
                ApiResult(true, answer)
            }

        } catch (e: java.net.SocketTimeoutException) {
            ApiResult(false, "زمان اتصال تمام شد؛ دوباره تلاش کن.")
        } catch (e: java.net.UnknownHostException) {
            ApiResult(false, "اینترنت یا آدرس سرور در دسترس نیست.")
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
