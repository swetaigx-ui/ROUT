package com.rout.aiassistant.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rout.aiassistant.MainActivity

/**
 * Quick Settings tile: one tap starts a live voice conversation.
 * Honest Android note: a background tile cannot legally grab the mic itself,
 * so the tap opens ROUT and starts the user-initiated LIVE session from the
 * foreground; the floating orb then appears over other apps. Tapping the tile
 * while LIVE runs stops it.
 */
class RoutTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            val st = com.rout.aiassistant.RoutApplication.live.state.value
            val on = st == "live" || st == "connecting"
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "ROUT Live"
            if (android.os.Build.VERSION.SDK_INT >= 29) subtitle = if (on) "Chal raha hai" else "Tap to start"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val st = com.rout.aiassistant.RoutApplication.live.state.value
        if (st == "live" || st == "connecting") {
            // LIVE already running: the tile stops it (and the mic) without opening the app.
            com.rout.aiassistant.RoutApplication.live.stop()
            startService(Intent(this, LiveOverlayService::class.java).setAction(LiveOverlayService.ACTION_STOP))
            qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
            return
        }
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("tile_live", true)
        @Suppress("DEPRECATION")
        startActivityAndCollapse(i)
    }
}
