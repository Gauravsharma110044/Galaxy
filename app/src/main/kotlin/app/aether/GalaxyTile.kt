package app.aether

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent

/** The quick-settings tile: tap to play or pause; it shows the song while one is playing. */
class GalaxyTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (NowPlayingBridge.title(this).isEmpty()) { openApp(); return }
        runCatching {
            NowPlayingBridge.keyIntent(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 22).send()
        }.onFailure { openApp() }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val title = NowPlayingBridge.title(this)
        tile.label = if (title.isEmpty()) "Galaxy" else title
        tile.state = if (NowPlayingBridge.playing(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (NowPlayingBridge.playing(this)) "Playing" else "Tap to play"
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 23, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
