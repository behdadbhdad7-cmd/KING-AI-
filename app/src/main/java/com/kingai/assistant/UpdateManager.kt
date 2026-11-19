package com.kingai.assistant

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Optional in-app updater. The APK must be published as a GitHub Release asset
 * named KING-AI.apk and signed with the same signing key as the installed app.
 */
object UpdateManager {
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/behdadbhdad7-cmd/KING-AI-/main/update.json"
    private const val APK_FILE = "KING-AI-update.apk"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun checkAndPrompt(activity: HomeActivity, force: Boolean = false) {
        executor.execute {
            val info = try {
                val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    if (connection.responseCode !in 200..299) return@execute
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                return@execute
            }

            val latestCode = info.optInt("versionCode", 0)
            val latestName = info.optString("versionName", "")
            val apkUrl = info.optString("apkUrl", "")
            val notes = info.optString("releaseNotes", "بهبود عملکرد و امکانات برنامه")
            if (latestCode <= BuildConfig.VERSION_CODE || latestName.isBlank() ||
                !apkUrl.startsWith("https://") || !apkUrl.endsWith(".apk", true)) {
                if (force) main.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        AlertDialog.Builder(activity)
                            .setMessage("در حال حاضر نسخه جدیدی پیدا نشد.")
                            .setPositiveButton("باشه", null)
                            .show()
                    }
                }
                return@execute
            }

            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                AlertDialog.Builder(activity)
                    .setTitle("بروزرسانی KING AI آماده است")
                    .setMessage("نسخه $latestName منتشر شده است.\n\n$notes\n\nمی‌خواهی همین حالا بروزرسانی کنی؟")
                    .setPositiveButton("بروزرسانی") { _, _ ->
                        downloadAndInstall(activity, apkUrl)
                    }
                    .setNegativeButton("بعداً", null)
                    .setCancelable(true)
                    .show()
            }
        }
    }

    private fun downloadAndInstall(activity: HomeActivity, apkUrl: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("دانلود بروزرسانی KING AI")
                .setDescription("دریافت نسخه جدید برنامه")
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_FILE)

            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 20, 36, 8)
            }
            val label = TextView(activity).apply {
                text = "در حال دریافت نسخه جدید..."
                textSize = 14f
            }
            val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
                max = 100
            }
            container.addView(label)
            container.addView(progress, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18 })

            val dialog = AlertDialog.Builder(activity)
                .setTitle("بروزرسانی KING AI")
                .setView(container)
                .setCancelable(false)
                .create()
            dialog.show()

            val poll = object : Runnable {
                override fun run() {
                    if (activity.isFinishing || activity.isDestroyed) {
                        main.removeCallbacks(this)
                        return
                    }
                    val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                    cursor.use {
                        if (!it.moveToFirst()) {
                            dialog.dismiss()
                            showError(activity, "وضعیت دانلود پیدا نشد.")
                            return
                        }
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                main.removeCallbacks(this)
                                dialog.dismiss()
                                installDownloaded(activity)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                main.removeCallbacks(this)
                                val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                dialog.dismiss()
                                showError(activity, "دانلود ناموفق بود. کد خطا: $reason")
                            }
                            else -> {
                                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                if (total > 0) {
                                    val percent = ((done * 100L) / total).toInt().coerceIn(0, 100)
                                    progress.isIndeterminate = false
                                    progress.progress = percent
                                    label.text = "در حال دانلود... $percent٪"
                                } else {
                                    progress.isIndeterminate = true
                                    label.text = "در حال دریافت نسخه جدید..."
                                }
                                main.postDelayed(this, 500)
                            }
                        }
                    }
                }
            }
            main.post(poll)
        } catch (_: Exception) {
            showError(activity, "شروع دانلود بروزرسانی ممکن نشد.")
        }
    }

    private fun installDownloaded(activity: HomeActivity) {
        val apk = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE)
        if (!apk.isFile || apk.length() < 1024) {
            showError(activity, "فایل بروزرسانی معتبر یا کامل نیست.")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("اجازه نصب بروزرسانی")
                .setMessage("برای نصب نسخه جدید، اجازه نصب برنامه از KING AI را در تنظیمات اندروید فعال کن.")
                .setPositiveButton("رفتن به تنظیمات") { _, _ ->
                    activity.getSharedPreferences("king_update", Context.MODE_PRIVATE)
                        .edit().putBoolean("awaiting_install_permission", true).apply()
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.packageName))
                    )
                }
                .setNegativeButton("بعداً", null)
                .show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                activity, activity.packageName + ".fileprovider", apk
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(install)
        } catch (_: Exception) {
            showError(activity, "بازکردن نصب‌کننده اندروید ناموفق بود.")
        }
    }

    fun resumePendingInstall(activity: HomeActivity) {
        val prefs = activity.getSharedPreferences("king_update", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("awaiting_install_permission", false)) return
        prefs.edit().remove("awaiting_install_permission").apply()
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            activity.packageManager.canRequestPackageInstalls()) {
            installDownloaded(activity)
        }
    }

    private fun showError(activity: HomeActivity, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("بروزرسانی انجام نشد")
            .setMessage(message)
            .setPositiveButton("باشه", null)
            .show()
    }
}
