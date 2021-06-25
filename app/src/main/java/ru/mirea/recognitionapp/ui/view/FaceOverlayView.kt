package ru.mirea.recognitionapp.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.View
import ru.mirea.recognitionapp.model.FaceResult
import java.text.DecimalFormat

class FaceOverlayView(context: Context?) : View(context) {

    init {
        initialize()
    }

    private var paint: Paint? = null
    private var textPaint: Paint? = null
    private var displayOrientation = 0
    private var orientation = 0
    private var previewWidth = 0
    private var previewHeight = 0
    private var faces: Array<FaceResult>? = arrayOf()
    private var fps = 0.0
    private var isFront = false

    private fun initialize() { // We want a green box around the face:
        val metrics = resources.displayMetrics
        val stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, metrics).toInt()
        paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            color = Color.GREEN
            strokeWidth = stroke.toFloat()
            style = Paint.Style.STROKE
        }

        textPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15f, metrics).toInt().toFloat()
            color = Color.GREEN
            style = Paint.Style.FILL
        }

    }

    fun setFPS(fps: Double) {
        this.fps = fps
    }

    fun setFaces(faces: Array<FaceResult>?) {
        this.faces = faces
        invalidate()
    }

    fun setOrientation(orientation: Int) {
        this.orientation = orientation
    }

    fun setDisplayOrientation(displayOrientation: Int) {
        this.displayOrientation = displayOrientation
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces != null && faces!!.isNotEmpty()) {
            var scaleX = width.toFloat() / previewWidth.toFloat()
            var scaleY = height.toFloat() / previewHeight.toFloat()
            when (displayOrientation) {
                90, 270 -> {
                    scaleX = width.toFloat() / previewHeight.toFloat()
                    scaleY = height.toFloat() / previewWidth.toFloat()
                }
            }
            canvas.save()
            canvas.rotate(-orientation.toFloat())
            val rectF = RectF()
            for (face in faces!!) {
                val mid = PointF()
                face.getMidPoint(mid)
                if (mid.x != 0.0f && mid.y != 0.0f) {
                    val eyesDis: Float = face.eyesDistance()
                    rectF.set(
                        RectF(
                            (mid.x - eyesDis * 1.2f) * scaleX,
                            (mid.y - eyesDis * 0.65f) * scaleY,
                            (mid.x + eyesDis * 1.2f) * scaleX,
                            (mid.y + eyesDis * 1.75f) * scaleY
                        )
                    )
                    if (isFront) {
                        val left = rectF.left
                        val right = rectF.right
                        rectF.left = width - right
                        rectF.right = width - left
                    }
                    canvas.drawRect(rectF, paint!!)
                    canvas.drawText(
                        "ID " + face.id,
                        rectF.left,
                        rectF.bottom + textPaint!!.textSize,
                        textPaint!!
                    )
                    canvas.drawText(
                        "Confidence " + face.confidence,
                        rectF.left,
                        rectF.bottom + textPaint!!.textSize * 2,
                        textPaint!!
                    )
                    canvas.drawText(
                        "EyesDistance " + face.eyesDistance(),
                        rectF.left,
                        rectF.bottom + textPaint!!.textSize * 3,
                        textPaint!!
                    )
                }
            }
            canvas.restore()
        }
        val df2 = DecimalFormat(".##")
        canvas.drawText(
            "Detected_Frame/s: " + df2.format(fps) + " @ " + previewWidth + "x" + previewHeight,
            textPaint!!.textSize,
            textPaint!!.textSize,
            textPaint!!
        )
    }

    fun setPreviewWidth(previewWidth: Int) {
        this.previewWidth = previewWidth
    }

    fun setPreviewHeight(previewHeight: Int) {
        this.previewHeight = previewHeight
    }

    fun setFront(front: Boolean) {
        isFront = front
    }

}