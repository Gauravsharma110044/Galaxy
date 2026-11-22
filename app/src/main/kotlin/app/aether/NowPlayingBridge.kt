package app.aether

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.view.KeyEvent
import android.widget.RemoteViews
import app.aether.player.PlaybackService

/**
 * What the home-screen widget and the quick-settings tile show. Both live outside the app's own
 * screens, so the app leaves the song and whether it is playing in a small private file on the
 * phone; nothing leaves the device. The buttons send an ordinary media key to the player service.
 */
object NowPlayingBridge {
    private const val FILE = "galaxy_now_playing"

    fun publish(context: Context, title: String, artist: String, playing: Boolean) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (p.getString("title", null) == title && p.getString("artist", null) == artist &&
            p.getBoolean("playing", false) == playing
        ) return
        p.edit().putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply()
        refreshWidgets(context)
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, GalaxyTile::class.java))
        }
    }

    fun title(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("title", null) ?: ""
    fun artist(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("artist", null) ?: ""
    fun playing(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("playing", false)

    /** A media key for the player service, the same one a Bluetooth headset sends. */
    fun keyIntent(context: Context, keyCode: Int, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return PendingIntent.getForegroundService(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun render(context: Context): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.galaxy_widget)
        val title = title(context)
        v.setTextViewText(R.id.widget_title, if (title.isEmpty()) "Galaxy" else title)
        v.setTextViewText(R.id.widget_artist, if (title.isEmpty()) "Nothing playing yet" else artist(context))
        v.setImageViewResource(
            R.id.widget_play,
            if (playing(context)) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        v.setOnClickPendingIntent(R.id.widget_prev, keyIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 11))
        v.setOnClickPendingIntent(R.id.widget_play, keyIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 12))
        v.setOnClickPendingIntent(R.id.widget_next, keyIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT, 13))
        val open = PendingIntent.getActivity(
            context, 14, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.widget_title, open)
        v.setOnClickPendingIntent(R.id.widget_artist, open)
        return v
    }

    fun refreshWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, GalaxyWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, render(context))
    }
}
