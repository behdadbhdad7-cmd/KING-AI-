
package com.kingai.assistant

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

        val rawToken = ApiConfig.getToken(context)

        if (rawToken.isNullOrBlank()) {
            return ApiResult(false, "کلید API تنظیم نشده است.")
        }

        val token = rawToken.trim()
            .removePrefix("Bearer ")
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

        var connection: HttpURLConnection? = null

        return try {

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
                        400 -> "درخواست نامعتبر: ${detail.take(300)}"
                        401 -> "کلید API نامعتبر است (401): ${detail.take(300)}"
                        403 -> "دسترسی به سرویس ممنوع است (403): ${detail.take(300)}"
                        404 -> "مدل یا مسیر پیدا نشد (404): ${detail.take(300)}"
                        429 -> "سهمیه رایگان یا محدودیت درخواست تمام شده (429): ${detail.take(300)}"
                        in 500..599 -> "خطای سرور ($status): ${detail.take(300)}"
                        else -> "خطای HTTP $status: ${detail.take(300)}"
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
                ApiResult(true, answer)
            }

        } catch (_: SocketTimeoutException) {
            ApiResult(false, "زمان اتصال تمام شد؛ دوباره تلاش کن.")
        } catch (_: UnknownHostException) {
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
