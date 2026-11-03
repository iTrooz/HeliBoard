// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

/** Owns selection delivery while a search session is active. */
class EmojiSearchSession @JvmOverloads constructor(
    private val isTargetCurrent: () -> Boolean = { true },
    private val commit: (String) -> Unit,
) {
    private var active = true

    /** Delivers each selection until the session is closed. */
    fun select(emoji: String) {
        if (!active) return
        if (!isTargetCurrent()) {
            close()
            return
        }
        commit(emoji)
    }

    /** Prevents later callbacks from delivering to a different editor. */
    fun close() {
        active = false
    }
}
