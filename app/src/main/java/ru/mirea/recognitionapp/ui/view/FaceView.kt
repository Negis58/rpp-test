package ru.mirea.recognitionapp.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import ru.mirea.recognitionapp.model.FaceResult
import java.util.*
import kotlin.math.min

class FaceView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var mBitmap: Bitmap? = null
    private var mFaces: ArrayList<FaceResult>? = null

    fun setContent(bitmap: Bitmap?, faces: ArrayList<FaceResult>?) {
        mBitmap = bitmap
        mFaces = faces
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBitmap != null && mFaces != null) {
            val scale = drawBitmap(canvas)
            drawFaceBox(canvas, scale)
        }
    }


    private fun drawBitmap(canvas: Canvas): Double {
        val viewWidth = canvas.width.toDouble()
        val viewHeight = canvas.height.toDouble()
        val imageWidth = mBitmap!!.width.toDouble()
        val imageHeight = mBitmap!!.height.toDouble()
        val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
        val destBounds = Rect(0, 0, (imageWidth * scale).toInt(), (imageHeight * scale).toInt())
        canvas.drawBitmap(mBitmap!!, null, destBounds, null)
        return scale
    }

    private fun drawFaceBox(canvas: Canvas, scale: Double) {
        val paint = Paint()
        paint.color = Color.GREEN
        paint.style = Paint.Style.STROKE
        val metrics = resources.displayMetrics
        val stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, metrics).toInt()
        paint.strokeWidth = stroke.toFloat()
        val mIdPaint = Paint()
        mIdPaint.color = Color.GREEN
        var textSize = (ID_TEXT_SIZE * scale).toFloat()
        if (textSize > 50) textSize = 50f
        mIdPaint.textSize = textSize
        for (i in mFaces!!.indices) {
            val face: FaceResult = mFaces!![i]
            val mid = PointF()
            face.getMidPoint(mid)
            val rectF = RectF()
            if (mid.x != 0.0f && mid.y != 0.0f) {
                val eyesDis: Float = face.eyesDistance()
                rectF.set(
                    RectF(
                        (mid.x - eyesDis * 1.2f) * scale.toFloat(),
                        (mid.y - eyesDis * 0.55f) * scale.toFloat(),
                        (mid.x + eyesDis * 1.2f) * scale.toFloat(),
                        (mid.y + eyesDis * 1.85f) * scale.toFloat()
                    )
                )
                canvas.drawRect(rectF, paint)
            }
        }
    }


    fun reset() {
        if (mBitmap != null) mBitmap!!.recycle()
    }

    companion object {
        private const val ID_TEXT_SIZE = 60.0f
    }
}
