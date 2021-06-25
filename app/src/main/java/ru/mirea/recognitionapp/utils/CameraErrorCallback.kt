package ru.mirea.recognitionapp.utils

import android.hardware.Camera
import android.hardware.Camera.ErrorCallback
import android.util.Log

class CameraErrorCallback : ErrorCallback {

    companion object {
        private const val TAG = "CameraErrorCallback"
    }

    override fun onError(error: Int, camera: Camera) {
        Log.e(TAG, "Encountered an unexpected camera error: $error")
    }
}