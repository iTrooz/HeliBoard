// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.dictionary.DictionaryInfoUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.suggestions.SuggestionResults
import java.util.Locale

/** Manages the emoji dictionaries for the active typing languages. */
class EmojiDictionaryFacilitator internal constructor(
    private val load: (Locale) -> SingleDictionaryFacilitator?
) {
    constructor(context: Context) : this({ locale ->
        DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, context)
            ?.let { DictionaryFactory.getDictionary(it, locale) }?.let { SingleDictionaryFacilitator(it) }
    })

    private var locales: List<Locale>? = null
    private var dictionaries = emptyList<SingleDictionaryFacilitator>()

    val isEmpty: Boolean get() = dictionaries.isEmpty()

    /** Reload when the ordered set of typing locales changes. */
    fun update(primary: Locale, secondary: List<Locale>): Boolean {
        val requested = (listOf(primary) + secondary).distinct()
        if (requested == locales && dictionaries.isNotEmpty()) return false
        closeDictionaries()
        locales = requested
        dictionaries = requested.mapNotNull(load)
        return true
    }

    /** Combine each word's best match across languages using the single-dictionary scoring rule. */
    fun getSuggestions(words: List<String>): SuggestionResults {
        val results = SuggestionResults(SuggestedWords.MAX_SUGGESTIONS, NgramContext.EMPTY_PREV_WORDS_INFO, false)
        val matches = words.flatMap { word ->
            dictionaries.flatMap { it.getSuggestions(word) }.groupBy { it.word }
                .values.map { candidates -> candidates.maxBy { it.score } }
        }
        matches.groupBy { it.word }.values.forEach { candidates ->
            val info = candidates.maxBy { it.score }
            val score = (candidates.sumOf { it.score.toLong() - Int.MIN_VALUE } / words.size + Int.MIN_VALUE).toInt()
            results.add(info.copy(score = score))
        }
        return results
    }

    /** Prefer the primary language when displaying emoji descriptions. */
    fun getWordProperty(word: String) = dictionaries.firstNotNullOfOrNull { it.getWordProperty(word) }

    /** Release all owned dictionaries. */
    fun closeDictionaries() {
        dictionaries.forEach { it.closeDictionaries() }
        dictionaries = emptyList()
        locales = null
    }

    companion object {
        /** Emoji dictionary collection for the current typing languages. */
        @JvmStatic
        fun createEmojiDictFacilitatorForCurrentSettings(context: Context): SingleDictionaryFacilitator? =
            DictionaryFactory.createEmojiDictionaryCollection(context,
                RichInputMethodManager.getInstance().currentSubtype.locale, Settings.getValues().mSecondaryLocales
            )?.let { SingleDictionaryFacilitator(it) }
    }
}
