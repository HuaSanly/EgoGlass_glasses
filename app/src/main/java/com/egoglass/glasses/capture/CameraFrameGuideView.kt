package com.egoglass.glasses.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Draws the capture aspect window without relying on density-scaled dp dimensions. */
class CameraFrameGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        strokeCap = Paint.Cap.BUTT
    }
    private var cameraWidth = 640
    private var cameraHeight = 480
    private var recording = false

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setCameraSize(width: Int, height: Int) {
        require(width > 0 && height > 0)
        if (cameraWidth == width && cameraHeight == height) return
        cameraWidth = width
        cameraHeight = height
        invalidate()
    }

    fun setRecording(active: Boolean) {
        if (recording == active) return
        recording = active
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val bounds = captureBounds(width, height, cameraWidth, cameraHeight)
        paint.color = if (recording) Color.argb(230, 255, 82, 82) else Color.argb(180, 220, 235, 245)
        paint.strokeWidth = if (recording) 2f else 1f
        val halfStroke = paint.strokeWidth / 2f
        val rect = RectF(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke,
        )
        canvas.drawRect(rect, paint)
    }

    companion object {
        fun captureBounds(
            viewWidth: Int,
            viewHeight: Int,
            cameraWidth: Int,
            cameraHeight: Int,
        ): CaptureFrameBounds {
            require(viewWidth > 0 && viewHeight > 0)
            require(cameraWidth > 0 && cameraHeight > 0)
            val cameraAspect = cameraWidth.toFloat() / cameraHeight.toFloat()
            val frameWidth = min(viewWidth.toFloat(), viewHeight.toFloat() * cameraAspect)
            val frameHeight = frameWidth / cameraAspect
            val left = (viewWidth - frameWidth) / 2f
            val top = (viewHeight - frameHeight) / 2f
            return CaptureFrameBounds(left, top, left + frameWidth, top + frameHeight)
        }
    }
}

data class CaptureFrameBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
