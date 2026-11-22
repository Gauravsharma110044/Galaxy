package app.aether.player

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A-B repeat: two points in the song that is playing. With both set, the player goes back to A each
 * time it reaches B. The service arms a player message at B so the jump is made on the player's own
 * clock, not by polling the position. A new song clears it.
 */
object AbRepeat {
    /** Where the loop starts, in milliseconds, or null when not set. */
    val a = MutableStateFlow<Long?>(null)

    /** Where the loop ends, in milliseconds, or null when not set. */
    val b = MutableStateFlow<Long?>(null)

    /** Whether a full loop (A and B) is set. */
    val active: Boolean get() = a.value != null && b.value != null

    fun clear() {
        a.value = null
        b.value = null
    }
}
