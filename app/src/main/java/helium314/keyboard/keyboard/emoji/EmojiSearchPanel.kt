// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.util.Consumer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.EMOJI_HINT_LABEL
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.getCode
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiDefaultVersion
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiKeyDimensions
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiNeutralVersion
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiPopupSpec
import helium314.keyboard.keyboard.internal.keyboard_parser.loadEmojiDefaultVersionsAndPopupSpecs
import helium314.keyboard.latin.EmojiDictionaryFacilitator
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs

/** Partition search results into full grid pages, retaining an empty page when needed. */
internal fun <T> emojiSearchPages(results: List<T>, columns: Int, rows: Int): List<List<T>> =
    results.chunked(columns * rows).ifEmpty { listOf(emptyList()) }

/** Compact search results owned by the IME; text input remains in the alphabet keyboard. */
class EmojiSearchPanel(context: Context) : LinearLayout(context) {
    private val colors = Settings.getValues().mColors
    private val template: Keyboard
    private val keyboardParams: KeyboardParams
    private val keyWidth: Float
    private val keyHeight: Float
    private val rows = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 1 else 2
    private val queryView: EmojiSearchQueryView
    private val pager: ViewPager2
    private val adapter = PageAdapter()
    private var dictionary: EmojiDictionaryFacilitator? = null
    private var query = ""
    private var pages: List<List<Key>> = listOf(emptyList())

    /** Called when the back button requests closing the panel. */
    var onClose: Runnable? = null
    /** Called when the clear button requests clearing the externally owned query. */
    var onClearQuery: Runnable? = null
    /** Called with the selected emoji without closing the panel. */
    var onEmojiSelected: Consumer<String>? = null
    /** Called when the query display requests a new insertion point. */
    var onCursorTap: Consumer<Int>? = null

    init {
        orientation = VERTICAL
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues())
        template = KeyboardLayoutSet.Builder(context, null, null)
            .setSubtype(RichInputMethodSubtype.emojiSubtype)
            .setKeyboardGeometry(width, EmojiLayoutParams(resources).emojiKeyboardHeight)
            .build().getKeyboard(KeyboardElement.EMOJI_RECENTS)
        val builder = KeyboardBuilder(context, KeyboardParams())
        builder.load(newGrid().mId)
        keyboardParams = builder.mParams
        val dimensions = getEmojiKeyDimensions(keyboardParams, context)
        keyWidth = dimensions.first
        keyHeight = dimensions.second
        loadEmojiDefaultVersionsAndPopupSpecs(context)

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            colors.setBackground(this, ColorType.EMOJI_SEARCH_BACKGROUND)
        }
        val back = ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_back)
            contentDescription = context.getString(R.string.spoken_description_action_previous)
            setColorFilter(colors.get(ColorType.EMOJI_SEARCH_TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClose?.run() }
        }
        queryView = EmojiSearchQueryView(context).apply {
            setTextColor(colors.get(ColorType.EMOJI_SEARCH_TEXT))
            setHintTextColor(colors.get(ColorType.EMOJI_SEARCH_TEXT))
            setHint(R.string.search_field_placeholder)
            textSize = 16f
            onCursorTap = { this@EmojiSearchPanel.onCursorTap?.accept(it) }
        }
        val clear = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = context.getString(android.R.string.cancel)
            setColorFilter(colors.get(ColorType.EMOJI_SEARCH_TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                onClearQuery?.run()
            }
        }
        val headerHeight = (40 * resources.displayMetrics.density).toInt()
        header.addView(back, LayoutParams(headerHeight, headerHeight))
        header.addView(queryView, LayoutParams(0, headerHeight, 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        header.addView(clear, LayoutParams(headerHeight, headerHeight))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, headerHeight))
        pager = ViewPager2(context).apply {
            adapter = this@EmojiSearchPanel.adapter
        }
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, newGrid().occupiedHeight + 20))
    }

    /** Returns the last measured panel height in pixels, including the header and results. */
    fun getPanelHeight(): Int = measuredHeight

    /** Replaces the externally managed query and refreshes results for the active languages. */
    fun updateQuery(text: String) {
        query = text
        queryView.text = text
        if (isAttachedToWindow) refreshResults()
    }

    /** Updates only the cursor, without repeating the emoji search. */
    fun updateCursor(offset: Int) = queryView.setCursor(offset)

    /** Returns the adjacent visual insertion point for a horizontal arrow. */
    fun cursorToSide(offset: Int, right: Boolean): Int = queryView.cursorToSide(offset, right)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshResults()
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }

    /** Releases dictionaries when the panel is hidden or no longer needed. */
    fun close() {
        dictionary?.closeDictionaries()
        dictionary = null
    }

    /** Builds a grid using the same dimensions and row count as activity search. */
    private fun newGrid(): DynamicGridKeyboard = DynamicGridKeyboard.ofRowCount(
        context.prefs(), template, rows, false, ResourceUtils.getKeyboardWidth(context, Settings.getValues())
    )

    /** Reloads dictionaries when languages change and resets paging for the new query. */
    private fun refreshResults() {
        val facilitator = dictionary ?: EmojiDictionaryFacilitator(context.applicationContext).also { dictionary = it }
        facilitator.update(RichInputMethodManager.getInstance().currentSubtype.locale, Settings.getValues().mSecondaryLocales)
        val grid = newGrid()
        val keys = if (facilitator.isEmpty || KeyboardSwitcher.getInstance().keyboard == null) emptyList() else
            facilitator.getSuggestions(query.splitOnWhitespace()).filter { it.isEmoji }.map {
                val emoji = getEmojiDefaultVersion(it.word)
                val popup = getEmojiPopupSpec(emoji)
                Key.KeyParams(emoji, emoji.getCode(), if (popup != null) EMOJI_HINT_LABEL else null, popup,
                    Key.LABEL_FLAGS_FONT_NORMAL, keyboardParams).apply {
                    mAbsoluteWidth = keyWidth
                    mAbsoluteHeight = keyHeight
                }.createKey()
            }
        pages = emojiSearchPages(keys, grid.occupiedColumnCount, rows)
        adapter.notifyDataSetChanged()
        pager.setCurrentItem(0, false)
    }

    /** Hosts standard emoji pages so long-press variants and descriptions remain available. */
    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = EmojiPageKeyboardView(context, null).apply {
                layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                colors.setBackground(this, ColorType.MAIN_BACKGROUND)
                setPadding(0, 10, 0, 10)
                setEmojiViewCallback(object : EmojiViewCallback {
                    override fun onPressKey(key: Key) {}
                    override fun onReleaseKey(key: Key) {
                        val emoji = if (key.code == KeyCode.MULTIPLE_CODE_POINTS) key.getOutputText() else Character.toString(key.code)
                        if (emoji != null) {
                            RecentEmojis.add(emoji)
                            onEmojiSelected?.accept(emoji)
                        }
                    }
                    override fun getDescription(emoji: String): String? = if (Settings.getValues().mShowEmojiDescriptions)
                        dictionary?.getWordProperty(getEmojiNeutralVersion(emoji))?.let {
                            if (it.mHasShortcuts) it.mShortcutTargets[0]?.mWord else null
                        } else null
                    override fun onRemoveRecentsKey(key: Key?) {}
                })
            }
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val grid = newGrid()
            pages[position].forEach(grid::addKeyLast)
            holder.view.setKeyboard(grid)
        }

        override fun onViewDetachedFromWindow(holder: PageHolder) {
            holder.view.cancelLongPress()
            holder.view.releaseCurrentKey(false)
            holder.view.deallocateMemory()
        }

        override fun getItemCount(): Int = pages.size
    }

    /** Holds a single reusable emoji grid page. */
    private class PageHolder(val view: EmojiPageKeyboardView) : RecyclerView.ViewHolder(view)
}
