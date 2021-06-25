package ru.mirea.recognitionapp.utils

import android.app.Activity
import android.graphics.Matrix
import android.graphics.Point
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import kotlin.math.abs
import kotlin.math.min

object Util {

    private const val ORIENTATION_HYSTERESIS = 5
    private const val TAG = "Util"

    fun getDisplayRotation(activity: Activity): Int {
        val rotation = activity.windowManager.defaultDisplay
            .rotation
        when (rotation) {
            Surface.ROTATION_0 -> return 0
            Surface.ROTATION_90 -> return 90
            Surface.ROTATION_180 -> return 180
            Surface.ROTATION_270 -> return 270
        }
        return 0
    }

    fun getDisplayOrientation(
        degrees: Int,
        cameraId: Int
    ): Int {
        val info = CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        var result: Int
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360
        } else {
            result = (info.orientation - degrees + 360) % 360
        }
        return result
    }

    fun prepareMatrix(
        matrix: Matrix, mirror: Boolean, displayOrientation: Int,
        viewWidth: Int, viewHeight: Int
    ) {
        matrix.setScale(if (mirror) (-1).toFloat() else 1.toFloat(), 1f)
        matrix.postRotate(displayOrientation.toFloat())
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f)
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f)
    }

    fun roundOrientation(orientation: Int, orientationHistory: Int): Int {
        var changeOrientation = false
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true
        } else {
            var dist = Math.abs(orientation - orientationHistory)
            dist = Math.min(dist, 360 - dist)
            changeOrientation = dist >= 45 + ORIENTATION_HYSTERESIS
        }
        return if (changeOrientation) {
            (orientation + 45) / 90 * 90 % 360
        } else orientationHistory
    }

    fun getOptimalPreviewSize(
        currentActivity: Activity,
        sizes: List<Camera.Size>?, targetRatio: Double
    ): Camera.Size? {
        val aspectTolerance = 0.001
        if (sizes == null) return null
        var optimalSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE
        val point =
            getDefaultDisplaySize(currentActivity, Point())
        val targetHeight = min(point.x, point.y)
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > aspectTolerance) continue
            if (abs(size.height - targetHeight) < minDiff) {
                optimalSize = size
                minDiff = abs(size.height - targetHeight).toDouble()
            }
        }
        if (optimalSize == null) {
            Log.w(TAG, "No preview size match the aspect ratio")
            minDiff = Double.MAX_VALUE
            for (size in sizes) {
                if (abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - targetHeight).toDouble()
                }
            }
        }
        return optimalSize
    }

    private fun getDefaultDisplaySize(
        activity: Activity,
        size: Point
    ): Point {
        activity.windowManager.defaultDisplay.getSize(size)
        return size
    }
}
