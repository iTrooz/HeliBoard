// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.max

private const val CURSOR_BLINK_INTERVAL_MS = 500L

/** Displays a query cursor without taking focus away from the host editor. */
class EmojiSearchQueryView(context: Context) : TextView(context) {
    var onCursorTap: ((Int) -> Unit)? = null
    private var cursor = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var trackingTap = false
    private var cursorVisible = true
    private val blink = object : Runnable {
        override fun run() {
            if (!shouldBlink()) return
            cursorVisible = !cursorVisible
            invalidate()
            postDelayed(this, CURSOR_BLINK_INTERVAL_MS)
        }
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = resources.displayMetrics.density * 2 }

    init {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setSingleLine(true)
        ellipsize = null
        setHorizontallyScrolling(true)
        isFocusable = false
    }

    /** Updates the displayed insertion point without changing the query. */
    fun setCursor(offset: Int) {
        cursor = offset.coerceIn(0, text.length)
        ensureCursorVisible()
        restartBlink()
    }

    /** Uses the text layout for visual movement through bidirectional text. */
    fun cursorToSide(offset: Int, right: Boolean): Int {
        val value = text.toString()
        var safeOffset = offset.coerceIn(0, value.length)
        if (safeOffset > 0 && safeOffset < value.length && Character.isHighSurrogate(value[safeOffset - 1]) &&
            Character.isLowSurrogate(value[safeOffset])) safeOffset--
        val current = layout
        if (current == null || current.text.toString() != value) {
            return if (right && safeOffset < value.length) value.offsetByCodePoints(safeOffset, 1)
            else if (!right && safeOffset > 0) value.offsetByCodePoints(safeOffset, -1)
            else safeOffset
        }
        return if (right) current.getOffsetToRightOf(safeOffset) else current.getOffsetToLeftOf(safeOffset)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                trackingTap = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (dx * dx + dy * dy > touchSlop.toFloat() * touchSlop) trackingTap = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                trackingTap = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (trackingTap && dx * dx + dy * dy <= touchSlop.toFloat() * touchSlop &&
                    event.x >= 0 && event.x < width && event.y >= 0 && event.y < height) {
                    restartBlink()
                    onCursorTap?.invoke(getOffsetForPosition(event.x, event.y))
                    performClick()
                }
                trackingTap = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                trackingTap = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartBlink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
        removeCallbacks(blink)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) restartBlink()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) restartBlink()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        ensureCursorVisible()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cursorVisible) return
        val current = layout ?: return
        val position = cursor.coerceIn(0, current.text.length)
        val line = current.getLineForOffset(position)
        val x = compoundPaddingLeft + current.getPrimaryHorizontal(position)
        val availableHeight = height - compoundPaddingTop - compoundPaddingBottom
        val y = compoundPaddingTop + max(0, (availableHeight - current.height) / 2)
        cursorPaint.color = currentTextColor
        canvas.drawLine(x, (y + current.getLineTop(line)).toFloat(), x,
            (y + current.getLineBottom(line)).toFloat(), cursorPaint)
    }

    /** Keeps the cursor visible immediately after interaction and stops work while hidden. */
    private fun restartBlink() {
        removeCallbacks(blink)
        cursorVisible = true
        invalidate()
        if (shouldBlink()) postDelayed(blink, CURSOR_BLINK_INTERVAL_MS)
    }

    private fun shouldBlink() = isAttachedToWindow && isShown && windowVisibility == View.VISIBLE

    private fun ensureCursorVisible() {
        val current = layout ?: return
        val available = width - compoundPaddingLeft - compoundPaddingRight
        if (available <= 0) return
        val x = current.getPrimaryHorizontal(cursor.coerceIn(0, current.text.length))
        val margin = cursorPaint.strokeWidth
        val target = when {
            x - scrollX < margin -> (x - margin).toInt()
            x - scrollX > available - margin -> (x - available + margin).toInt()
            else -> scrollX
        }
        scrollTo(target.coerceIn(0, max(0, current.width - available)), scrollY)
    }
}
