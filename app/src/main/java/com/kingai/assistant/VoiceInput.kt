package com.kingai.assistant

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/**
 * ورودی صوتی فارسی با پنجره‌ی رسمی تشخیص گفتار اندروید.
 * فقط وقتی کاربر دکمه‌ی میکروفون را لمس کند باز می‌شود و KING AI خودش به میکروفون دسترسی ندارد.
 */
object VoiceInput {

    fun createIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "فرمان خود را بگو")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    @Suppress("DEPRECATION")
    fun isAvailable(ctx: Context): Boolean =
        try {
            ctx.packageManager.queryIntentActivities(createIntent(), 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }

    fun extract(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
}
