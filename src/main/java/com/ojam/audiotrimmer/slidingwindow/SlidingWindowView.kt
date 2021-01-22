package com.ojam.audiotrimmer.slidingwindow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.ojam.audiotrimmer.dpToPx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class SlidingWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val HOLD_LEFT_BAR = 0
    private val HOLD_RIGHT_BAR = 1
    private val HOLD_NOTHING = 2
    private val HOLD_THUMB = 3

    @DrawableRes
    var leftBarRes: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    @DrawableRes
    var rightBarRes: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    @DrawableRes
    var sliderThumbRes: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var barWidth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var sliderWidth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var borderWidth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    @ColorInt
    var borderColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    @ColorInt
    var overlayColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var listener: Listener? = null
    var extraDragSpace: Float = 0f

    private val borderPaint: Paint = Paint().apply { isAntiAlias = true }
    private val overlayPaint: Paint = Paint().apply { isAntiAlias = true }

    private var leftBarX = -1f    // Left-Top
    private var rightBarX = -1f   // Left-Top
    private var thumbX = -1f      // Left-Top

    private var leftBarXPercentage = -1f
    private var rightBarXPercentage = -1f
    private var thumbXPercentage = -1f

    private var hold = HOLD_NOTHING

    /* -------------------------------------------------------------------------------------------*/
    /* Public APIs */
    fun setBarPositions(leftPercentage: Float, rightPercentage: Float, thumbPercentage: Float) {
        this.leftBarXPercentage = leftPercentage
        this.rightBarXPercentage = rightPercentage
        this.thumbXPercentage = thumbPercentage

        postInvalidate()
    }

    fun setThumbPosition(thumbPercentage: Float) {
        this.thumbXPercentage = thumbPercentage

        postInvalidate()
    }

    fun reset() {
        leftBarX = -1f
        rightBarX = -1f
        leftBarXPercentage = -1f
        rightBarXPercentage = -1f
        thumbXPercentage = -1f

        invalidate()
    }

    /* -------------------------------------------------------------------------------------------*/
    /* Draw */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (leftBarXPercentage >= 0f && rightBarXPercentage > 0f) {
            restoreBarPositions()
        }

        if(thumbXPercentage > 0f) {
            restoreThumb()
        }

        if (leftBarX < 0) {
            leftBarX = 0f
        }

        if(thumbX < barWidth) {
            thumbX = barWidth
        }

        if (rightBarX < 0) {
            rightBarX = width - barWidth
        }

        if(thumbX < leftBarX) {
            thumbX = leftBarX
        }

        if(thumbX > rightBarX) {
            thumbX = rightBarX
        }

        drawBorder(canvas)
        drawLeftBar(canvas)
        drawRightBar(canvas)
        drawOverlay(canvas)
        drawSliderThumb(canvas)
    }

    private fun drawLeftBar(canvas: Canvas) {
        ContextCompat.getDrawable(context, leftBarRes)?.apply {
            setBounds(0, dpToPx(context, 20f).roundToInt(), barWidth.roundToInt(), height - dpToPx(context, 20f).roundToInt())

            canvas.save()

            canvas.translate(leftBarX, 0f)
            draw(canvas)

            canvas.restore()
        }
    }

    private fun drawSliderThumb(canvas: Canvas) {
        ContextCompat.getDrawable(context, sliderThumbRes)?.apply {
            setBounds(0, 0, sliderWidth.roundToInt(), height)

            canvas.save()

            canvas.translate(thumbX, 0f)
            draw(canvas)

            canvas.restore()
        }
    }

    private fun drawRightBar(canvas: Canvas) {
        ContextCompat.getDrawable(context, rightBarRes)?.apply {
            setBounds(0, dpToPx(context, 20f).roundToInt(), barWidth.roundToInt(), height - dpToPx(context, 20f).roundToInt())

            canvas.save()

            canvas.translate(rightBarX, 0f)
            draw(canvas)

            canvas.restore()
        }
    }

    private fun drawBorder(canvas: Canvas) {
        borderPaint.strokeWidth = borderWidth
        borderPaint.color = borderColor

        val fromX = leftBarX + barWidth - 1
        val toX = rightBarX + 1

        drawTopBorder(canvas, fromX, toX)
        drawBottomBorder(canvas, fromX, toX)
    }

    private fun drawTopBorder(canvas: Canvas, fromX: Float, toX: Float) {
        val y = dpToPx(context, 20f).roundToInt() + borderWidth / 2f
        canvas.drawLine(fromX, y, toX, y, borderPaint)
    }

    private fun drawBottomBorder(canvas: Canvas, fromX: Float, toX: Float) {
        val y = height - dpToPx(context, 20f).roundToInt() - (borderWidth / 2f)
        canvas.drawLine(fromX, y, toX, y, borderPaint)
    }

    private fun drawOverlay(canvas: Canvas) {
        overlayPaint.color = overlayColor

        // Left side overlay
        if (leftBarX > barWidth) {
            canvas.drawRect(
                barWidth,
                borderWidth,
                leftBarX,
                height - dpToPx(context, 20f).roundToInt() - borderWidth,
                overlayPaint
            )
        }

        // Right side overlay
        if (rightBarX < width - 2 * barWidth) {
            canvas.drawRect(
                rightBarX + barWidth,
                borderWidth,
                width - barWidth,
                height - dpToPx(context, 20f).roundToInt() - borderWidth,
                overlayPaint
            )
        }
    }

    /* -------------------------------------------------------------------------------------------*/
    /* OnTouch */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            ACTION_DOWN -> onDown(event.x, event.y)
            ACTION_MOVE -> onMove(event.x, event.y)
            ACTION_UP -> onUp(event.x, event.y)
            else -> super.onTouchEvent(event)
        }
    }

    private fun onDown(x: Float, y: Float): Boolean {
        hold = when {
            isLeftBarTouched(x, y) -> HOLD_LEFT_BAR
            isRightBarTouched(x, y) -> HOLD_RIGHT_BAR
            isThumbTouched(x, y) -> HOLD_THUMB
            else -> HOLD_NOTHING
        }

        when (hold) {
            HOLD_LEFT_BAR, HOLD_RIGHT_BAR ->
                listener?.onDragRangeBarStart()
            HOLD_THUMB ->
                listener?.onProgressStart()
        }

        return hold != HOLD_NOTHING
    }

    private fun onMove(x: Float, y: Float): Boolean {
        when (hold) {
            HOLD_LEFT_BAR -> moveLeftBar(x, y)
            HOLD_RIGHT_BAR -> moveRightBar(x, y)
            HOLD_THUMB -> moveSlider(x, y)
            else -> return false
        }

        return true
    }

    private fun onUp(x: Float, y: Float): Boolean {
        when (hold) {
            HOLD_LEFT_BAR, HOLD_RIGHT_BAR -> {
                val percentage = calculateXPercentage(leftBarX, rightBarX)
                listener?.onDragRangeBarEnd(percentage[0], percentage[1])
            }
            HOLD_THUMB -> {
                val percentage = calculateThumbXPercentage(thumbX)
                listener?.onProgressEnd(percentage)
            }
        }

        hold = HOLD_NOTHING
        return true
    }

    /* -------------------------------------------------------------------------------------------*/
    /* Internal helpers */
    private fun isLeftBarTouched(x: Float, y: Float): Boolean {
        return x in (leftBarX - extraDragSpace)..(leftBarX + barWidth + extraDragSpace)
                && y in 0f..height.toFloat()
    }

    private fun isRightBarTouched(x: Float, y: Float): Boolean {
        return x in (rightBarX - extraDragSpace)..(rightBarX + barWidth + extraDragSpace)
                && y in 0f..height.toFloat()
    }

    private fun isThumbTouched(x: Float, y: Float): Boolean {
        return x in (thumbX - extraDragSpace)..(thumbX + sliderWidth + extraDragSpace)
                && y in 0f..height.toFloat()
    }

    private fun moveLeftBar(x: Float, y: Float) {
        var predictedLeftBarX = x - (barWidth / 2f)
        predictedLeftBarX = max(predictedLeftBarX, 0f)
        predictedLeftBarX = min(predictedLeftBarX, rightBarX - barWidth - 1)

        val percentage = calculateXPercentage(predictedLeftBarX, rightBarX)
        if (listener?.onDragRangeBar(percentage[0], percentage[1]) != false) {
            leftBarX = predictedLeftBarX
            postInvalidate()
        }
    }

    private fun moveRightBar(x: Float, y: Float) {
        var predictedRightBarX = x - (barWidth / 2f)
        predictedRightBarX = max(predictedRightBarX, leftBarX + barWidth + 1)
        predictedRightBarX = min(predictedRightBarX, width.toFloat() - barWidth)

        val percentage = calculateXPercentage(leftBarX, predictedRightBarX)
        if (listener?.onDragRangeBar(percentage[0], percentage[1]) != false) {
            rightBarX = predictedRightBarX
            postInvalidate()
        }
    }

    private fun moveSlider(x: Float, y: Float) {
        var predictedSliderX = x - (sliderWidth / 2f)
        predictedSliderX = max(predictedSliderX, leftBarX + barWidth + 1)
        predictedSliderX = min(predictedSliderX, rightBarX - barWidth - 1)

        var percentage = calculateThumbXPercentage(predictedSliderX)
        if (listener?.onDragProgressBar(percentage) != false) {
            thumbX = predictedSliderX
            postInvalidate()
        }
    }

    private fun calculateXPercentage(leftBarX: Float, rightBarX: Float): FloatArray {
        val totalLength = width - barWidth
        val left = leftBarX / totalLength
        val right = rightBarX / totalLength
        return floatArrayOf(left, right)
    }

    private fun calculateThumbXPercentage(thumbLeftX: Float): Float {
        val totalLength = width - barWidth

        return thumbLeftX / totalLength
    }

    private fun restoreBarPositions() {
        val totalLength = width - barWidth

        this.leftBarX = leftBarXPercentage * totalLength
        this.rightBarX = rightBarXPercentage * totalLength

        this.leftBarXPercentage = -1f
        this.rightBarXPercentage = -1f
    }

    private fun restoreThumb() {
        val totalLength = width - barWidth

        this.thumbX = thumbXPercentage * totalLength

        this.thumbXPercentage = -1f
    }

    /* -------------------------------------------------------------------------------------------*/
    /* Listener */
    interface Listener {
        fun onDragRangeBarStart()
        fun onDragRangeBar(left: Float, right: Float): Boolean
        fun onDragRangeBarEnd(left: Float, right: Float)


        fun onProgressEnd(percentage: Float)
        fun onProgressStart()
        fun onDragProgressBar(percentage: Float): Boolean
    }
}