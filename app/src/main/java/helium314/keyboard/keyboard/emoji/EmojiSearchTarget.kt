// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

/** Identifies the editor and connection that opened a search session. */
class EmojiSearchTarget(
    private val connection: Any,
    private val editor: Any,
    private val packageName: String?,
    private val fieldId: Int,
) {
    /** Requires both identities and the captured editor metadata to remain unchanged. */
    fun matches(connection: Any?, editor: Any?, packageName: String?, fieldId: Int): Boolean =
        this.connection === connection && this.editor === editor &&
            this.packageName == packageName && this.fieldId == fieldId

    /** Checks stable metadata in addition to Android's same-editor restart signal. */
    fun matchesRestart(packageName: String?, fieldId: Int): Boolean =
        packageName != null && this.packageName == packageName && this.fieldId == fieldId
}
