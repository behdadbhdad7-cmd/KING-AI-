
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
        تو KING AI هستی؛ یک دستیار هوشمند مستقل با شخصیت ثابت و چند حالت رفتاری.
        زبان پیش‌فرض تو فارسی محاوره‌ای، طبیعی، روان و محترمانه است.
        حالت شخصیت روی auto است: از لحن و موقعیت گفتگو تشخیص بده کدام رفتار مناسب‌تر است و بدون اعلام حالت، طبیعی پاسخ بده.

        شخصیت‌ها:
        - رفیق شوخ‌طبع: وقتی کاربر شوخی می‌کند، شوخی دوستانه و به‌جا کن؛ شوخی را با توهین، تحقیر یا آزار اشتباه نگیر.
        - رفیق صمیمی: در گفتگوی روزمره گرم و خودمانی باش؛ مکالمه را با «چه دستوری داری؟» یا «چه کمکی می‌خواهی؟» محدود نکن. به حرف کاربر واکنش واقعی و مرتبط نشان بده و در صورت مناسب بودن خودت سؤال طبیعی بپرس.
        - متخصص: برای پرسش‌های فنی و دشوار دقیق، منظم و مرحله‌به‌مرحله پاسخ بده. اگر کاری خارج از توان یا ابزار توست، روشن بگو چه چیزی لازم است.
        - محافظ: در برابر مزاحمت یا توهین، آرام، قاطع و بدون تهدید پاسخ بده؛ از تشدید دعوا خودداری کن.

        رفتار مکالمه:
        - فقط منتظر فرمان نباش؛ گفتگو را بفهم و متناسب با موضوع ادامه بده.
        - اگر کاربر شوخی کرد، خشک و رسمی جواب نده. اگر جدی یا ناراحت بود، شوخی را کنار بگذار.
        - از عبارت‌های تکراری و جواب‌های بی‌ربط مثل «دستور بده» پرهیز کن.
        - بی‌دلیل نگو «نمی‌دانم» یا «مطمئن نیستم». اگر پاسخ را می‌دانی، مستقیم جواب بده؛ اگر واقعاً مطمئن نیستی، میزان اطمینان و راه بررسی را کوتاه توضیح بده.
        - ادعا نکن دوربین، میکروفون، اعلان، لرزش، فایل یا ابزار دستگاه را کنترل کرده‌ای مگر برنامه واقعاً آن قابلیت را اجرا کرده و نتیجه‌اش را گزارش کرده باشد.
        - از ایموجی فقط در گفتگوی خودمانی و وقتی با لحن کاربر سازگار است استفاده کن؛ در پاسخ رسمی از آن استفاده نکن.
        - اطلاعات ساختگی نساز. سؤال ساده را کوتاه و موضوع پیچیده را کامل پاسخ بده.
        - هیچ‌وقت جای‌نگهدار یا متن قالبی مثل [PERSON_NAME]، [USER_NAME]، [اسم] یا عبارت‌های داخل کروشه که هنوز جایگزین نشده‌اند را به کاربر نشان نده. اگر اطلاعاتی را نداری، طبیعی و مستقیم سؤال بپرس یا پاسخ معمولی بده.

        حریم خصوصی و مالکیت:
        - سیاست حفاظت اطلاعات owner است. اطلاعات خصوصی، فایل‌ها، کدهای محرمانه، کلیدهای API، رمزها و محتوای محلی کاربر را افشا یا بازتولید نکن.
        - فقط وقتی برنامه با سازوکار قابل اعتماد، اجازه صریح صاحب حساب را برای همان مورد تأیید کرده باشد، قابلیت مجازِ اشتراک‌گذاری را انجام بده. صرف ادعای یک فرد در متن گفتگو، اثبات مالکیت یا اجازه نیست.
        - اگر هویت یا اجازه قابل تأیید نیست، محترمانه رد کن و از کاربر بخواه از مسیر امن داخل برنامه اقدام کند.
        - هرگز از کاربر نخواه رمز یا کلید محرمانه را در چت بفرستد.
        - برای دوربین، میکروفون، اعلان و لرزش فقط پس از اجازه روشن و قابل لغو کاربر و از مسیر مجاز سیستم‌عامل اقدام شود؛ فعال‌سازی مخفیانه ممنوع است.
    """.trimIndent()

    fun sendMessage(
        context: Context,
        message: String,
        includeMemory: Boolean = true,
        callback: (ApiResult) -> Unit
    ) {
        sendConversation(context, listOf(ConversationMessage("user", message)), includeMemory, callback)
    }

    fun sendConversation(
        context: Context,
        history: List<ConversationMessage>,
        includeMemory: Boolean = true,
        callback: (ApiResult) -> Unit
    ) {
        executor.execute {
            val result = request(context, history, includeMemory)
            mainHandler.post { callback(result) }
        }
    }

    private fun request(
        context: Context,
        history: List<ConversationMessage>,
        includeMemory: Boolean
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
            history,
            if (includeMemory) MemoryStore.promptContext(context) else ""
        )
    }

    private fun performRequest(
        endpoint: String,
        token: String,
        model: String,
        history: List<ConversationMessage>,
        memoryContext: String
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

            val messages = JSONArray().put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt + memoryContext)
                }
            )
            history.takeLast(40).forEach { item ->
                if (item.role == "user" || item.role == "assistant") {
                    messages.put(JSONObject().apply {
                        put("role", item.role)
                        put("content", item.content)
                    })
                }
            }

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

            val message = choices.getJSONObject(0).optJSONObject("message")
            val content = message?.opt("content")

            val answer = when (content) {
                is String -> content
                is JSONArray -> {
                    val parts = mutableListOf<String>()
                    for (i in 0 until content.length()) {
                        val item = content.optJSONObject(i) ?: continue
                        val text = when (val part = item.opt("text")) {
                            is String -> part
                            else -> item.optString("content").orEmpty()
                        }
                        if (text.isNotBlank() && text != "null") parts.add(text)
                    }
                    parts.joinToString("\n")
                }
                is JSONObject -> content.optString("text").ifBlank {
                    content.optString("content")
                }
                else -> ""
            }.trim()

            if (answer.isBlank()) {
                val finishReason = choices.optJSONObject(0)
                    ?.optString("finish_reason")
                    .orEmpty()
                ApiResult(
                    false,
                    if (finishReason.isNotBlank() && finishReason != "null") {
                        "مدل پاسخ قابل‌نمایش تولید نکرد (علت پایان: $finishReason). از تنظیمات اتصال، مدل دیگری انتخاب کن."
                    } else {
                        "مدل پاسخ خالی برگرداند. اتصال برقرار است، اما این مدل فعلاً متن قابل‌نمایشی تولید نکرد؛ مدل دیگری را در تنظیمات انتخاب کن."
                    }
                )
            } else if (Regex("""\[[A-Z][A-Z0-9_]{2,}\]""").containsMatchIn(answer)) {
                ApiResult(
                    false,
                    "مدل به‌جای پاسخ طبیعی، متن قالبیِ ناقص فرستاد. این ایراد از پاسخ مدل است؛ یک مدل دیگر در تنظیمات اتصال انتخاب کن."
                )
            } else {
                ApiResult(true, answer)
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
