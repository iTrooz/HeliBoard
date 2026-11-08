// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.inputmethod.InputMethodSubtype
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.dictionary.DictionaryInfoUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests discovery of emoji dictionaries across enabled typing languages. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class DictionaryInfoUtilsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs get() = latinIME.prefs()

    @Test fun discoversSecondaryOnlyDictionariesAndDeduplicatesLocales() {
        prefs.edit {
            putString(Settings.PREF_ENABLED_SUBTYPES, "")
            putString(Settings.PREF_ADDITIONAL_SUBTYPES, "")
        }
        SubtypeSettings.reloadEnabledSubtypes(latinIME)
        SubtypeSettings.addEnabledSubtype(prefs, subtype("en", "SecondaryLocales=fr:de:fr"))
        SubtypeSettings.addEnabledSubtype(prefs, subtype("fr", "SecondaryLocales=de:es"))
        createEmojiDict(Locale.FRENCH)
        createEmojiDict(Locale.GERMAN)

        assertEquals(listOf(Locale.FRENCH, Locale.GERMAN), DictionaryInfoUtils.getLocalesWithEmojiDicts(latinIME))
    }

    // Build a subtype
    private fun subtype(locale: String, extraValues: String): InputMethodSubtype =
        InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeLocale(locale)
            .setSubtypeMode("keyboard").setSubtypeExtraValue(extraValues).build()

    // Create a minimal file that DictionaryInfoUtils recognizes as an emoji dictionary
    private fun createEmojiDict(locale: Locale) {
        val dir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, latinIME)!!
        File(dir, "emoji_test.dict").createNewFile()
    }
}
