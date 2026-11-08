package com.kingai.assistant

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class KingTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(this, OverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.updateTile()
            // Close the Quick Settings shade after activating the assistant.
            collapseStatusBar()
        } else {
            val setup = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivityAndCollapse(setup)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state = if (Settings.canDrawOverlays(this)) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        qsTile?.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun collapseStatusBar() {
        // Android closes the shade after the tile click on supported versions.
    }
}
