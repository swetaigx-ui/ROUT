package com.rout.aiassistant.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rout.aiassistant.MainActivity

/**
 * Quick Settings tile: one tap starts a live voice conversation.
 * Honest Android note: a background tile cannot legally grab the mic itself,
 * so the tap opens ROUT straight into listening - one tap total.
 */
class RoutTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (ListeningService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("tile_voice", true)
        @Suppress("DEPRECATION")
        startActivityAndCollapse(i)
    }
}
