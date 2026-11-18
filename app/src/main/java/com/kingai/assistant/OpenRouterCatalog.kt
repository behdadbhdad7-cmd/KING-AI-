package com.kingai.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * دریافت فهرست مدل‌های رایگان OpenRouter.
 * فقط شناسه‌های دارای پسوند :free پذیرفته می‌شوند؛
 * بنابراین مدل پولی وارد فهرست انتخاب دستی نمی‌شود.
 */
object OpenRouterCatalog {
    data class FreeModel(val id: String, val name: String) {
        override fun toString(): String = "$name  •  $id"
    }

    fun fetchFreeModels(baseUrl: String, token: String?): List<FreeModel> {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.contains("openrouter.ai", ignoreCase = true)) {
            "فهرست مدل رایگان فقط برای OpenRouter فعال است."
        }

        val root = when {
            normalized.endsWith("/chat/completions", true) ->
                normalized.removeSuffix("/chat/completions")
            normalized.endsWith("/v1", true) -> normalized
            else -> "$normalized/v1"
        }
        val endpoint = "$root/models"

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("دریافت فهرست مدل‌ها ناموفق بود (HTTP $status). ${detail.take(180)}")
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = json.optJSONArray("data") ?: return listOf(
                FreeModel("openrouter/free", "مسیریاب خودکار رایگان")
            )

            val models = mutableListOf<FreeModel>()
            models.add(FreeModel("openrouter/free", "مسیریاب خودکار رایگان"))

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                if (!id.endsWith(":free", ignoreCase = true)) continue

                val pricing = item.optJSONObject("pricing") ?: continue
                val promptPrice = pricing.optString("prompt")
                val completionPrice = pricing.optString("completion")
                if (!isZeroPrice(promptPrice) || !isZeroPrice(completionPrice)) continue

                val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: id
                models.add(FreeModel(id, name))
            }

            return models.distinctBy { it.id }.sortedWith(
                compareBy<FreeModel> { if (it.id == "openrouter/free") 0 else 1 }
                    .thenBy { it.name.lowercase() }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isZeroPrice(value: String): Boolean {
        return value.toDoubleOrNull() == 0.0
    }
}
