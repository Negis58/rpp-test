package ru.mirea.recognitionapp.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.media.FaceDetector
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.set
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_camera_viewer.*
import ru.mirea.recognitionapp.R
import ru.mirea.recognitionapp.adapter.HTTPRequest
import ru.mirea.recognitionapp.adapter.ImagePreviewAdapter
import ru.mirea.recognitionapp.model.FaceResult
import ru.mirea.recognitionapp.ui.view.FaceOverlayView
import ru.mirea.recognitionapp.utils.CameraErrorCallback
import ru.mirea.recognitionapp.utils.ImageUtils
import ru.mirea.recognitionapp.utils.Util
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and

class FaceDetectActivity : AppCompatActivity(),
    SurfaceHolder.Callback, PreviewCallback {

    companion object {
        val TAG = FaceDetectActivity::class.java.simpleName
        private const val MAX_FACE = 10
        private const val BUNDLE_CAMERA_ID = "camera"
    }

    private val processor: HTTPRequest = HTTPRequest()
    private var numberOfCameras = 0
    private var camera: Camera? = null
    private var cameraId = 0
    private var mDisplayRotation = 0
    private var displayOrientation = 0
    private var previewWidth = 0
    private var previewHeight = 0
    private var faceView: FaceOverlayView? = null
    private val mErrorCallback: CameraErrorCallback = CameraErrorCallback()
    private var isThreadWorking = false
    private var handler: Handler? = null
    private var detectThread: FaceDetectThread? = null
    private var prevSettingWidth = 0
    private var prevSettingHeight = 0
    private var fdet: FaceDetector? = null
    private lateinit var grayBuff: ByteArray
    private var bufflen = 0
    private lateinit var rgbs: IntArray
    private lateinit var faces: Array<FaceResult?>
    private lateinit var facesPrevious: Array<FaceResult?>
    private var id = 0
    private val facesCount = SparseIntArray()
    private var imagePreviewAdapter: ImagePreviewAdapter? = null
    private var facesBitmap: ArrayList<Bitmap>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processor.start()
        setContentView(R.layout.activity_camera_viewer)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Now create the OverlayView:
        faceView = FaceOverlayView(this)
        addContentView(
            faceView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.itemAnimator = DefaultItemAnimator()

        handler = Handler()
        faces = arrayOfNulls(MAX_FACE)
        facesPrevious = arrayOfNulls(MAX_FACE)
        for (i in 0 until MAX_FACE) {
            faces[i] = FaceResult()
            facesPrevious[i] = FaceResult()
        }
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            title = "MIREA"
        }

        if (savedInstanceState != null) cameraId = savedInstanceState.getInt(BUNDLE_CAMERA_ID, 0)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val holder = surfaceview.holder
        holder.addCallback(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_camera, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.home -> {
                super.onBackPressed()
                true
            }
            R.id.switchCam -> {
                if (numberOfCameras == 1) {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle("Switch Camera").setMessage("Your device have one camera")
                        .setNeutralButton("Close", null)
                    val alert = builder.create()
                    alert.show()
                    return true
                }
                cameraId = (cameraId + 1) % numberOfCameras
                recreate()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Restarts the camera.
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
        startPreview()
    }

    /**
     * Stops the camera.
     */
    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
        if (camera != null) {
            camera!!.stopPreview()
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    override fun onDestroy() {
        super.onDestroy()
        resetData()
        processor.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(BUNDLE_CAMERA_ID, cameraId)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) { //Find the total number of cameras available
        resetData()
        numberOfCameras = Camera.getNumberOfCameras()
        val cameraInfo = CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                if (cameraId == 0) cameraId = i
            }
        }
        camera = Camera.open(cameraId)
        Camera.getCameraInfo(cameraId, cameraInfo)
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            faceView?.setFront(true)
        }
        try {
            camera!!.setPreviewDisplay(surfaceview.holder)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Could not preview the image.",
                e
            )
        }
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) { // We have no surface, return immediately:
        if (surfaceHolder.surface == null) {
            return
        }
        // Try to stop the current preview:
        try {
            camera!!.stopPreview()
        } catch (e: Exception) { // Ignore...
        }
        configureCamera(width, height)
        setDisplayOrientation()
        setErrorCallback()
        // Create media.FaceDetector
        val aspect = previewHeight.toFloat() / previewWidth.toFloat()
        fdet = FaceDetector(
            prevSettingWidth,
            (prevSettingWidth * aspect).toInt(),
            MAX_FACE
        )
        bufflen = previewWidth * previewHeight
        grayBuff = ByteArray(bufflen)
        rgbs = IntArray(bufflen)
        // Everything is configured! Finally start the camera preview again:
        startPreview()
    }

    private fun setErrorCallback() {
        camera!!.setErrorCallback(mErrorCallback)
    }

    private fun setDisplayOrientation() { // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(this@FaceDetectActivity)
        displayOrientation = Util.getDisplayOrientation(mDisplayRotation, cameraId)
        camera!!.setDisplayOrientation(displayOrientation)
        if (faceView != null) {
            faceView!!.setDisplayOrientation(displayOrientation)
        }
    }

    private fun configureCamera(width: Int, height: Int) {
        val parameters = camera!!.parameters
        // Set the PreviewSize and AutoFocus:
        setOptimalPreviewSize(parameters, width, height)
        setAutoFocus(parameters)
        // And set the parameters:
        camera!!.parameters = parameters
    }

    private fun setOptimalPreviewSize(
        cameraParameters: Camera.Parameters,
        width: Int,
        height: Int
    ) {
        val previewSizes = cameraParameters.supportedPreviewSizes
        val targetRatio = width.toFloat() / height
        val previewSize: Camera.Size = Util.getOptimalPreviewSize(this, previewSizes, targetRatio.toDouble())!!
        previewWidth = previewSize.width
        previewHeight = previewSize.height
        Log.e(TAG, "previewWidth$previewWidth")
        Log.e(TAG, "previewHeight$previewHeight")
        /**
         * Calculate size to scale full frame bitmap to smaller bitmap
         * Detect face in scaled bitmap have high performance than full bitmap.
         * The smaller image size -> detect faster, but distance to detect face shorter,
         * so calculate the size follow your purpose
         */
        when {
            previewWidth / 4 > 360 -> {
                prevSettingWidth = 360
                prevSettingHeight = 270
            }
            previewWidth / 4 > 320 -> {
                prevSettingWidth = 320
                prevSettingHeight = 240
            }
            previewWidth / 4 > 240 -> {
                prevSettingWidth = 240
                prevSettingHeight = 160
            }
            else -> {
                prevSettingWidth = 160
                prevSettingHeight = 120
            }
        }
        cameraParameters.setPreviewSize(previewSize.width, previewSize.height)
        faceView?.setPreviewWidth(previewWidth)
        faceView?.setPreviewHeight(previewHeight)
    }

    private fun setAutoFocus(cameraParameters: Camera.Parameters) {
        val focusModes =
            cameraParameters.supportedFocusModes
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) cameraParameters.focusMode =
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
    }

    private fun startPreview() {
        if (camera != null) {
            isThreadWorking = false
            camera!!.startPreview()
            camera!!.setPreviewCallback(this)
            counter = 0
        }
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        camera!!.setPreviewCallbackWithBuffer(null)
        camera!!.setErrorCallback(null)
        camera!!.release()
        camera = null
    }

    // fps detect face (not FPS of camera)
    var start: Long = 0
    var end: Long = 0
    var counter = 0
    var fps = 0.0
    override fun onPreviewFrame(
        _data: ByteArray,
        _camera: Camera
    ) {
        if (!isThreadWorking) {
            if (counter == 0) start = System.currentTimeMillis()
            isThreadWorking = true
            waitForFdetThreadComplete()
            detectThread = FaceDetectThread(handler!!)
            detectThread!!.setData(_data)
            detectThread!!.start()
        }
    }

    private fun waitForFdetThreadComplete() {
        if (detectThread == null) {
            return
        }
        if (detectThread!!.isAlive) {
            try {
                detectThread!!.join()
                detectThread = null
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Do face detect in thread
     */
    private inner class FaceDetectThread(private val handler: Handler) :
        Thread() {
        private var data: ByteArray? = null
        private var faceCroped: Bitmap? = null
        fun setData(data: ByteArray?) {
            this.data = data
        }

        override fun run() {
            val aspect = previewHeight.toFloat() / previewWidth.toFloat()
            val w = prevSettingWidth
            val h = (prevSettingWidth * aspect).toInt()
            val bbuffer = ByteBuffer.wrap(data)
            bbuffer[grayBuff, 0, bufflen]
            gray8toRGB32(grayBuff, previewWidth, previewHeight, rgbs)
            val bitmap =
                Bitmap.createBitmap(rgbs, previewWidth, previewHeight, Bitmap.Config.RGB_565)
            var bmp = Bitmap.createScaledBitmap(bitmap, w, h, false)
            var xScale =
                previewWidth.toFloat() / prevSettingWidth.toFloat()
            var yScale = previewHeight.toFloat() / h.toFloat()
            val info = CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            var rotate = displayOrientation
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT && mDisplayRotation % 180 == 0) {
                rotate = if (rotate + 180 > 360) {
                    rotate - 180
                } else rotate + 180
            }
            when (rotate) {
                90 -> {
                    bmp = ImageUtils.rotate(bmp, 90f)
                    xScale = previewHeight.toFloat() / bmp.width
                    yScale = previewWidth.toFloat() / bmp.height
                }
                180 -> bmp = ImageUtils.rotate(bmp, 180f)
                270 -> {
                    bmp = ImageUtils.rotate(bmp, 270f)
                    xScale = previewHeight.toFloat() / h.toFloat()
                    yScale = previewWidth.toFloat() / prevSettingWidth.toFloat()
                }
            }
            fdet = FaceDetector(
                bmp.width,
                bmp.height,
                MAX_FACE
            )
            val fullResults =
                arrayOfNulls<FaceDetector.Face>(MAX_FACE)
            fdet!!.findFaces(bmp, fullResults)
            for (i in 0 until MAX_FACE) {
                if (fullResults[i] == null) {
                    faces[i]?.clear()
                } else {
                    val mid = PointF()
                    fullResults[i]!!.getMidPoint(mid)
                    mid.x *= xScale
                    mid.y *= yScale
                    val eyesDis = fullResults[i]!!.eyesDistance() * xScale
                    val confidence = fullResults[i]!!.confidence()
                    val pose =
                        fullResults[i]!!.pose(FaceDetector.Face.EULER_Y)
                    var idFace = id
                    val rect = Rect(
                        (mid.x - eyesDis * 1.20f).toInt(),
                        (mid.y - eyesDis * 0.55f).toInt(),
                        (mid.x + eyesDis * 1.20f).toInt(),
                        (mid.y + eyesDis * 1.85f).toInt()
                    )
                    /**
                     * Only detect face size > 100x100
                     */
                    if (rect.height() * rect.width() > 100 * 100) { // Check this face and previous face have same ID?
                        for (j in 0 until MAX_FACE) {
                            val eyesDisPre: Float = facesPrevious[j]!!.eyesDistance()
                            val midPre = PointF()
                            facesPrevious[j]?.getMidPoint(midPre)
                            val rectCheck = RectF(
                                midPre.x - eyesDisPre * 1.5f,
                                midPre.y - eyesDisPre * 1.15f,
                                midPre.x + eyesDisPre * 1.5f,
                                midPre.y + eyesDisPre * 1.85f
                            )
                            if (rectCheck.contains(
                                    mid.x,
                                    mid.y
                                ) && System.currentTimeMillis() - facesPrevious[j]?.time!! < 1000
                            ) {
                                idFace = facesPrevious[j]?.id?.toLong()!!
                                break
                            }
                        }
                        if (idFace == id) this@FaceDetectActivity.id++
                        faces[i]?.setFace(
                            idFace.toInt(),
                            mid,
                            eyesDis,
                            confidence,
                            pose,
                            System.currentTimeMillis()
                        )
                        faces[i]?.let {
                            facesPrevious[i]?.set(
                                faces[i]!!.id,
                                faces[i]!!.midEye,
                                faces[i]!!.eyesDistance(),
                                faces[i]!!.confidence,
                                faces[i]!!.pose,
                                faces[i]!!.time
                            )
                        }
                        //

                        val count = facesCount[idFace.toInt()] + 1
                        if (count <= 5) facesCount[idFace.toInt()] = count
                        //
// Crop Face to display in RecylerView
                        if (count == 5) {
                            faceCroped = ImageUtils.cropFace(faces[i]!!, bitmap, rotate)
                            if (faceCroped != null) {
                                handler.post {
                                    imagePreviewAdapter?.add(faceCroped)
                                    Log.d(
                                        "FaceDetectGrayActivity",
                                        "bitmap offered to send queue: $faceCroped"
                                    )
                                    if (faceCroped != null) {
                                        processor.offerToSendQueue(faceCroped!!)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            handler.post {
                //send face to FaceView to draw rect
                faceView?.setFaces(faces.requireNoNulls())
                //Calculate FPS (Detect Frame per Second)
                end = System.currentTimeMillis()
                counter++
                val time = (end - start).toDouble() / 1000
                if (time != 0.0) fps = counter / time
                faceView?.setFPS(fps)
                if (counter == Int.MAX_VALUE - 1000) counter = 0
                isThreadWorking = false
            }
        }

        private fun gray8toRGB32(
            gray8: ByteArray,
            width: Int,
            height: Int,
            rgb_32s: IntArray
        ) {
            val endPtr = width * height
            var ptr = 0
            while (true) {
                if (ptr == endPtr) break
                val y: Int = (gray8[ptr] and 0xff.toByte()).toInt()
                rgb_32s[ptr] = -0x1000000 + (y shl 16) + (y shl 8) + y
                ptr++
            }
        }

    }

    /**
     * Release Memory
     */
    private fun resetData() {
        if (imagePreviewAdapter == null) {
            facesBitmap = ArrayList()
            imagePreviewAdapter = ImagePreviewAdapter(
                facesBitmap!!.toMutableList()
            )
            recycler_view.adapter = imagePreviewAdapter
        } else {
            imagePreviewAdapter!!.clearAll()
        }
    }

}
