package com.kingai.assistant

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * صفحه‌ی دوربین KING AI.
 * فقط بعد از فرمان کاربر (مثلاً «شات بگیر») از OverlayService باز می‌شود،
 * پیش‌نمایش زنده را نشان می‌دهد، یک عکس می‌گیرد و در Pictures/KING-AI ذخیره می‌کند.
 */
class CameraCaptureActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var imageCapture: ImageCapture? = null
    private var cameraStarted = false
    private var captureScheduled = false
    private var photoTaken = false

    // روش استاندارد مجوزگیری (Activity Result API)؛ جایگزین onRequestPermissionsResult
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAllPermissions()) {
            startCamera()
        } else {
            onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        status = TextView(this).apply {
            text = "در حال آماده‌سازی دوربین..."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0x99000000.toInt())
        }

        val layout = FrameLayout(this)
        layout.setBackgroundColor(android.graphics.Color.BLACK)
        layout.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        layout.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        setContentView(layout)

        if (hasAllPermissions()) {
            startCamera()
        } else {
            status.text = "لطفاً اجازه‌ی دسترسی به دوربین را تأیید کن."
            permissionLauncher.launch(requiredPermissions())
        }
    }

    // ------------------------------------------------------------ Permissions

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // اندروید ۹ و پایین‌تر برای ذخیره در گالری به این مجوز نیاز دارد
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun onPermissionDenied() {
        status.text = "دسترسی داده نشد. برای فعال‌سازی، اینجا را لمس کن تا تنظیمات برنامه باز شود."
        status.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    // ----------------------------------------------------------------- Camera

    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        status.text = "در حال باز کردن دوربین..."

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )

                // عکس فقط وقتی گرفته می‌شود که تصویر زنده واقعاً شروع به نمایش کرده باشد
                previewView.previewStreamState.observe(this) { state ->
                    if (state == PreviewView.StreamState.STREAMING && !captureScheduled) {
                        captureScheduled = true
                        status.text = "دوربین آماده است؛ در حال گرفتن عکس..."
                        // کمی مکث برای تنظیم نور و فوکوس
                        handler.postDelayed({ takePhoto() }, 800)
                    }
                }
            } catch (e: Exception) {
                cameraStarted = false
                status.text = "خطا در راه‌اندازی دوربین: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (photoTaken || isFinishing || isDestroyed) return

        val capture = imageCapture ?: return
        photoTaken = true

        val fileName = "KING_AI_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KING-AI")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "KING-AI"
                )
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    status.text = "عکس با موفقیت ذخیره شد."
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "عکس در گالری (Pictures/KING-AI) ذخیره شد 👑",
                        Toast.LENGTH_LONG
                    ).show()
                    handler.postDelayed({ finish() }, 1500)
                }

                override fun onError(exception: ImageCaptureException) {
                    photoTaken = false
                    status.text = "گرفتن عکس ناموفق بود: ${exception.message}"
                }
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
