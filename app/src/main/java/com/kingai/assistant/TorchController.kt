package com.kingai.assistant

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** کنترل چراغ‌قوه با CameraManager.setTorchMode (بدون نیاز به مجوز دوربین). */
object TorchController {

    @Volatile
    var isOn: Boolean = false
        private set

    private var cameraId: String? = null
    private var callbackRegistered = false

    private fun manager(ctx: Context): CameraManager =
        ctx.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun findTorchCamera(ctx: Context): String? {
        cameraId?.let { return it }
        return try {
            val m = manager(ctx)
            val id = m.cameraIdList.firstOrNull { cid ->
                val ch = m.getCameraCharacteristics(cid)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cameraId = id
            id
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureCallback(ctx: Context) {
        if (callbackRegistered) return
        try {
            manager(ctx).registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) isOn = enabled
                    }

                    override fun onTorchModeUnavailable(id: String) {
                        if (id == cameraId) isOn = false
                    }
                },
                Handler(Looper.getMainLooper())
            )
            callbackRegistered = true
        } catch (e: Exception) {
            // اگر ثبت نشد، وضعیت فقط از آخرین فرمان خودمان خوانده می‌شود
        }
    }

    fun set(ctx: Context, on: Boolean): CommandResult {
        val id = findTorchCamera(ctx)
            ?: return CommandResult("این گوشی چراغ‌قوه‌ی قابل کنترل ندارد.", false)
        ensureCallback(ctx)
        return try {
            manager(ctx).setTorchMode(id, on)
            isOn = on
            CommandResult(if (on) "چراغ‌قوه روشن شد." else "چراغ‌قوه خاموش شد.")
        } catch (e: CameraAccessException) {
            CommandResult("چراغ‌قوه در دسترس نیست؛ شاید دوربین در حال استفاده است.", false)
        } catch (e: Exception) {
            CommandResult("کنترل چراغ‌قوه ناموفق بود: ${e.message}", false)
        }
    }
}
