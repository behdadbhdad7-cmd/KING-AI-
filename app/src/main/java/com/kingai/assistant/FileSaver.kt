
package com.kingai.assistant

import android.content.Context
import android.net.Uri

/**
 * ذخیره پاسخ هوش مصنوعی در فایل واقعی گوشی
 */
object FileSaver {

    fun save(
        context: Context,
        uri: Uri,
        content: String
    ): Boolean {
        return try {
            context.contentResolver
                .openOutputStream(uri)
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer ->
                    writer.write(content)
                } ?: return false

            true
        } catch (e: Exception) {
            false
        }
    }
}
