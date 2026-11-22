package app.aether

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Galaxy's own settings in one small file: the app's settings and the equaliser's choices (including
 * each song's own sound). Playlists and liked songs are backed up by "Export" under Your playlists, so
 * this file holds neither, and nothing about an account or a sign-in.
 */
object SettingsBackup {
    private val stores = listOf("aether", "aether_equaliser")

    /** Writes the backup to [uri]; false if it couldn't be. */
    fun write(context: Context, uri: Uri): Boolean = runCatching {
        val root = JSONObject().put("galaxy_backup", 1)
        for (name in stores) {
            val o = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val e = JSONObject()
                when (value) {
                    is Boolean -> e.put("t", "b").put("v", value)
                    is Int -> e.put("t", "i").put("v", value)
                    is Long -> e.put("t", "l").put("v", value)
                    is Float -> e.put("t", "f").put("v", value.toDouble())
                    is String -> e.put("t", "s").put("v", value)
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                    else -> return@forEach
                }
                o.put(key, e)
            }
            root.put(name, o)
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)

    /** Applies the backup at [uri] over the current settings; false if it isn't a Galaxy settings backup. */
    fun read(context: Context, uri: Uri): Boolean = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        if (root.optInt("galaxy_backup", 0) != 1) return@runCatching false
        for (name in stores) {
            val o = root.optJSONObject(name) ?: continue
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            for (key in o.keys()) {
                val e = o.getJSONObject(key)
                when (e.getString("t")) {
                    "b" -> edit.putBoolean(key, e.getBoolean("v"))
                    "i" -> edit.putInt(key, e.getInt("v"))
                    "l" -> edit.putLong(key, e.getLong("v"))
                    "f" -> edit.putFloat(key, e.getDouble("v").toFloat())
                    "s" -> edit.putString(key, e.getString("v"))
                    "ss" -> {
                        val a = e.getJSONArray("v")
                        edit.putStringSet(key, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            edit.commit()
        }
        true
    }.getOrDefault(false)
}
