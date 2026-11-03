// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.os.Build
import android.util.Log
import android.view.inputmethod.InputConnection

/** Notifies the original editor while search consumes input, when it is still current. */
class EmojiSearchInputConsumption @JvmOverloads constructor(
    private val connection: InputConnection,
    private val sdkVersion: Int = Build.VERSION.SDK_INT,
    private val isTargetCurrent: () -> Boolean,
) {
    private var attempted = false

    /** Requests input consumption without changing editor contents or selection. */
    fun open() {
        if (attempted || sdkVersion < Build.VERSION_CODES.S || !isTargetCurrent()) return
        attempted = true
        notifyEditor(connection, true)
    }

    /** Restores the flag only if the captured connection still identifies the same editor. */
    fun close() {
        if (!attempted) return
        attempted = false
        if (sdkVersion >= Build.VERSION_CODES.S && isTargetCurrent()) notifyEditor(connection, false)
    }

    /** Restores on the new connection when Android restarts the same editor. */
    fun closeForRestart(currentConnection: InputConnection?) {
        if (!attempted || currentConnection == null) return
        attempted = false
        if (sdkVersion >= Build.VERSION_CODES.S) notifyEditor(currentConnection, false)
    }

    private fun notifyEditor(target: InputConnection, consumes: Boolean) {
        try {
            target.setImeConsumesInput(consumes)
        } catch (e: RuntimeException) {
            Log.w("EmojiSearch", "Unable to notify editor about input consumption", e)
        }
    }
}
