package com.kingai.assistant

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class KingTileService : TileService() {

    override fun onClick() {
        super.onClick()
        when {
            // بدون مجوز نمایش روی سایر برنامه‌ها: صفحه‌ی راه‌اندازی باز می‌شود.
            !Settings.canDrawOverlays(this) ->
                openAndCollapse(Intent(this, MainActivity::class.java))

            // پنل باز است: لمس کاشی آن را می‌بندد.
            OverlayService.running -> {
                stopService(Intent(this, OverlayService::class.java))
                setTileState(Tile.STATE_INACTIVE)
            }

            // پنل بسته است: صفحه‌ی گذرا پرده را می‌بندد و پنل را شروع می‌کند.
            else -> openAndCollapse(Intent(this, LaunchPanelActivity::class.java))
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        // کاشی همیشه قابل لمس می‌ماند (UNAVAILABLE لمس را غیرفعال می‌کند).
        // وضعیت فعال فقط وقتی نشان داده می‌شود که پنل واقعاً باز است.
        setTileState(
            if (Settings.canDrawOverlays(this) && OverlayService.running) Tile.STATE_ACTIVE
            else Tile.STATE_INACTIVE
        )
    }

    private fun setTileState(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openAndCollapse(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
