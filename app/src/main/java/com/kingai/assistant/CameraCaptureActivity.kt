package com.kingai.assistant

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class CameraCaptureActivity : Activity() {

    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    private var captured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        previewView = PreviewView(this)
        status = TextView(this).apply {
            text = "KING AI: آماده‌سازی دوربین..."
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            gravity = Gravity.CENTER
            setPadding(20, 24, 20, 24)
        }

        val root = FrameLayout(this)
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            status,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        status.text = "KING AI: دوربین در حال روشن‌شدن است..."

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                provider.unbindAll()

                provider.bindToLifecycle(
                    this as androidx.lifecycle.LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture!!
                )

                status.text =
                    "دوربین آماده است؛ عکس خودکار گرفته می‌شود..."

                previewView.postDelayed({
                    takePhoto()
                }, 1800)

            } catch (e: Exception) {
                status.text =
                    "خطا در راه‌اندازی دوربین: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (captured) return

        val capture = imageCapture ?: return
        captured = true

        val name = "KING_AI_" +
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(System.currentTimeMillis())

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/KING AI"
                )
            }
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    status.text = "عکس در گالری ذخیره شد 👑"

                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "عکس KING AI ذخیره شد",
                        Toast.LENGTH_LONG
                    ).show()

                    previewView.postDelayed({
                        finish()
                    }, 1800)
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    captured = false
                    status.text =
                        "خطا در ذخیره عکس: ${exception.message}"
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_CAMERA) {
            if (hasCameraPermission()) {
                startCamera()
            } else {
                status.text =
                    "برای عکس‌برداری باید مجوز دوربین را بدهی."
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA = 501
    }
}
