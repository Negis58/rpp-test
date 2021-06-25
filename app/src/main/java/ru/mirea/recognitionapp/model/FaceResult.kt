package ru.mirea.recognitionapp.model

import android.graphics.PointF

class FaceResult : Any() {


    var midEye = PointF(0.0f, 0.0f)
    private var eyeDist = 0.0f
    var confidence: Float = 0.4f
    var pose: Float = 0.0f
    var id = 0
    var time: Long = System.currentTimeMillis()

    fun setFace(
        id: Int,
        midEye: PointF,
        eyeDist: Float,
        confidence: Float,
        pose: Float,
        time: Long
    ) = set(id, midEye, eyeDist, confidence, pose, time)


    fun clear() = set(0, PointF(0.0f, 0.0f), 0.0f, 0.4f, 0.0f, System.currentTimeMillis())

    @Synchronized
    operator fun set(
        id: Int,
        midEye: PointF,
        eyeDist: Float,
        confidence: Float,
        pose: Float,
        time: Long
    ) {
        this.id = id
        this.midEye.set(midEye)
        this.eyeDist = eyeDist
        this.confidence = confidence
        this.pose = pose
        this.time = time
    }

    fun eyesDistance(): Float = eyeDist


    fun setEyeDist(eyeDist: Float) {
        this.eyeDist = eyeDist
    }

    fun getMidPoint(pt: PointF) = pt.set(midEye)

}