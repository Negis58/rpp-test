package ru.mirea.recognitionapp.utils

import android.content.Context
import android.database.Cursor
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import ru.mirea.recognitionapp.model.FaceResult
import java.io.IOException

object ImageUtils {

    fun calculateInSampleSize(
        options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize > reqHeight
                && halfWidth / inSampleSize > reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun getRealPathFromURI(
        context: Context,
        contentUri: Uri?
    ): String {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(
                MediaStore.Images.Media.DATA
            )
            cursor = context.contentResolver.query(contentUri!!, proj, null, null, null)
            val column_index = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(column_index)
        } finally {
            cursor?.close()
        }
    }

    fun rotate(b: Bitmap, degrees: Float): Bitmap {
        var b = b
        if (degrees != 0f && b != null) {
            val m = Matrix()
            m.setRotate(
                degrees, b.width.toFloat() / 2,
                b.height.toFloat() / 2
            )
            val b2 = Bitmap.createBitmap(
                b, 0, 0, b.width,
                b.height, m, true
            )
            if (b != b2) {
                b.recycle()
                b = b2
            }
        }
        return b
    }

    fun getBitmap(filePath: String?, width: Int, height: Int): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(filePath, options)
        options.inSampleSize = calculateInSampleSize(options, width, height)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        var bitmap = BitmapFactory.decodeFile(filePath, options)
        if (bitmap != null) {
            try {
                val ei = ExifInterface(filePath!!)
                val orientation = ei.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> bitmap = rotate(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> bitmap = rotate(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> bitmap = rotate(bitmap, 270f)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        return bitmap
    }

    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        val ret = Bitmap.createBitmap(w, h, bitmap.config)
        val canvas = Canvas(ret)
        canvas.drawBitmap(bitmap, -rect.left.toFloat(), -rect.top.toFloat(), null)
        bitmap.recycle()
        return ret
    }

    fun cropFace(face: FaceResult, bitmap: Bitmap, rotate: Int): Bitmap {
        var bmp: Bitmap
        val eyesDis: Float = face.eyesDistance()
        val mid = PointF()
        face.getMidPoint(mid)
        val rect = Rect(
            (mid.x - eyesDis * 1.20f).toInt(),
            (mid.y - eyesDis * 0.55f).toInt(),
            (mid.x + eyesDis * 1.20f).toInt(),
            (mid.y + eyesDis * 1.85f).toInt()
        )
        var config: Bitmap.Config? = Bitmap.Config.RGB_565
        if (bitmap.config != null) config = bitmap.config
        bmp = bitmap.copy(config, true)
        when (rotate) {
            90 -> bmp = rotate(bmp, 90f)
            180 -> bmp = rotate(bmp, 180f)
            270 -> bmp = rotate(bmp, 270f)
        }
        bmp = cropBitmap(bmp, rect)
        return bmp
    }
}

