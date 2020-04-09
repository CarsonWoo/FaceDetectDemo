package com.carson.gdufs_sign_system.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.lang.NullPointerException
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.math.abs

class CameraHelper private constructor(builder: Builder) {
    companion object {
        private const val TAG = "CameraHelper"

        private const val MAX_PERVIEW_WIDTH = 2560
        private const val MAX_PREVIEW_HEIGHT = 2560
        private const val MIN_PREVIEW_WIDTH = 720
        private const val MIN_PREVIEW_HEIGHT = 720

        const val CAMERA_ID_FRONT = "1"
        const val CAMERA_ID_BACK = "0"

        class Builder {
            internal var previewDisplayView: TextureView? = null

            internal var isMirror = false

            internal lateinit var specificCameraId: String

            internal var cameraListener: CameraListener? = null

            internal var previewViewSize: Point? = null

            internal var rotation: Int = 0

            internal var previewSize: Point? = null

            internal var mContext: Context? = null

            fun previewOn(value: TextureView): Builder {
                previewDisplayView = value
                return this
            }

            fun isMirror(value: Boolean): Builder {
                isMirror = value
                return this
            }

            fun previewSize(value: Point): Builder {
                previewSize = value
                return this
            }

            fun previewViewSize(value: Point): Builder {
                previewViewSize = value
                return this
            }

            fun rotation(value: Int): Builder {
                rotation = value
                return this
            }

            fun specificCameraId(value: String): Builder {
                specificCameraId = value
                return this
            }

            fun cameraListener(value: CameraListener): Builder {
                cameraListener = value
                return this
            }

            fun mContext(value: Context): Builder {
                mContext = value
                return this
            }

            fun build(): CameraHelper {
                previewViewSize?: Log.e(TAG, "previewViewSize is null, now use default previewSize.")

                cameraListener?: Log.e(TAG, "cameraListener is null, callback will not be called.")

                previewDisplayView?: throw RuntimeException("you must preview on a textureView or a surfaceView.")

                return CameraHelper(this)
            }
        }
    }

    private lateinit var mCameraId: String
    private var specificCameraId: String
    private var cameraListener: CameraListener? = null
    private var mTextureView: TextureView? = null
    private var rotation: Int = 0
    private var previewViewSize: Point? = null
    private var specificPreviewSize: Point? = null
    private var isMirror = false
    private var mContext: Context? = null

    /**
     * A {@link CameraCaptureSession} for camera preview.
     */
    private var mCaptureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened {@link CameraDevice}
     */
    private var mCameraDevice: CameraDevice? = null

    private var mPreviewSize: Size? = null

    /**
     * Orientation of the camera sensor
     */
    private var mSensorOrientation: Int = 0

    init {
        mTextureView = builder.previewDisplayView
        specificCameraId = builder.specificCameraId
        cameraListener = builder.cameraListener
        rotation = builder.rotation
        previewViewSize = builder.previewViewSize
        specificPreviewSize = builder.previewSize
        isMirror = builder.isMirror
        mContext = builder.mContext
        if (isMirror) mTextureView?.scaleX = -1F
    }

    private fun getCameraOri(rotation: Int, cameraId: String): Int {
        var degrees = rotation * 90
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        val result = if (CAMERA_ID_FRONT == cameraId) {
            (360 - (mSensorOrientation + degrees) % 360) % 360
        } else {
            (mSensorOrientation - degrees + 360) % 360
        }
        Log.i(TAG, "getCameraOri: $rotation $result $mSensorOrientation")
        return result
    }

    private val mSurfaceTextureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
            Log.i(TAG, "onSurfaceTextureSizeChanged: ")
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            Log.i(TAG, "onSurfaceTextureDestroyed: ")
            return true
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable: ")
            openCamera()
        }

    }

    private val mDeviceStateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "onOpened: ")
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenLock.release()
            mCameraDevice = camera
            createCameraPreviewSession()
            mPreviewSize?.let {
                cameraListener?.onCameraOpened(camera, mCameraId, it, getCameraOri(rotation, mCameraId), isMirror)
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.i(TAG, "onDisconnected: ")
            mCameraOpenLock.release()
            camera.close()
            mCameraDevice = null
            cameraListener?.onCameraClosed()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.i(TAG, "onError: ")
            mCameraOpenLock.release()
            camera.close()
            mCameraDevice = null
            cameraListener?.onCameraError(Exception("error occurred, code is $error"))
        }

    }

    private val mCaptureStateCallback = object: CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.i(TAG, "onConfigureFailed: ")
            cameraListener?.onCameraError(Exception("configuredFailed"))
        }

        override fun onConfigured(session: CameraCaptureSession) {
            Log.i(TAG, "onConfigured: ")
            // The camera is already closed
            mCameraDevice?: return

            // When the session is ready, we start displaying the preview
            mCaptureSession = session
            try {
                mPreviewRequestBuilder?.let {
                    mCaptureSession?.setRepeatingRequest(it.build(),
                        object: CameraCaptureSession.CaptureCallback() {}, mBackgroundHandler)
                }
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null
    /**
     * A {@link Handler} for running tasks in the background
     */
    private var mBackgroundHandler: Handler? = null

    private var mImageReader: ImageReader? = null

    private val mOnImageAvailableListener = object: ImageReader.OnImageAvailableListener {

        private val lock = ReentrantLock()

        override fun onImageAvailable(reader: ImageReader?) {
            // 这里做保存工作
            Log.e(TAG, "onImageAvailable")
            val image = reader?.acquireNextImage()
            if (cameraListener != null && image?.format == ImageFormat.JPEG) {
                val planes = image.planes
                // 加锁保证y,u,v来源于同一个Image
                lock.lock()
                val byteBuffer = planes[0].buffer

                val byteArray = ByteArray(byteBuffer.remaining())
                byteBuffer.get(byteArray)

                cameraListener?.onPreview(byteArray)

                lock.unlock()
            }
            image?.close()
        }
    }

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private var mCameraOpenLock = Semaphore(1)

    private fun getBestSupportedSize(sizes: List<Size>): Size {
        val tempSizes = sizes.toTypedArray()
        Arrays.sort(tempSizes) { o1, o2 ->
            return@sort if (o1 != null && o2 != null) {
                if (o1.width > o2.width) {
                    -1
                } else if (o1.width == o2.width) {
                    if (o1.height > o2.height) -1 else 1
                } else {
                    1
                }
            } else {
                Log.e(TAG, "can't compare")
                -1
            }
        }
        val mSizes = tempSizes.asList()
        var bestSize = mSizes[0]
        var previewViewRatio = if (previewViewSize != null) {
            previewViewSize!!.x.toFloat() / previewViewSize!!.y
        } else {
            bestSize.width.toFloat() / bestSize.height
        }
        if (previewViewRatio > 1) {
            previewViewRatio = 1 / previewViewRatio
        }

        mSizes.forEach {
            if (specificPreviewSize != null && specificPreviewSize?.x == it.width && specificPreviewSize?.y == it.height) {
                return it
            }
            if (it.width > MAX_PERVIEW_WIDTH || it.height > MAX_PREVIEW_HEIGHT ||
                it.width < MIN_PREVIEW_WIDTH || it.height < MIN_PREVIEW_HEIGHT) {
                return@forEach
            }
            if (abs(it.height / it.width.toFloat() - previewViewRatio) <
                abs(bestSize.height / bestSize.width.toFloat() - previewViewRatio)) {
                bestSize = it
            }
        }
        return bestSize
    }

    @Synchronized
    fun start() {
        Log.i(TAG, "start")
        if (mCameraDevice != null) return
        startBackgroundThread()

        // When the screen is turned off the turned back on, the SurfaceTexture is already available,
        // and "onSurfaceTextureAvailable" will not be called. In that case, we can open a camera
        // and start preview from here (otherwise, we wait until the surface is ready in the
        // SurfaceTextureListener).
        if (mTextureView?.isAvailable == true) {
            openCamera()
        } else {
            mTextureView?.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    @Synchronized
    fun stop() {
        Log.i(TAG, "stop")
        mCameraDevice ?: return
        closeCamera()
        stopBackgroundThread()
    }

    fun release() {
        stop()
        mTextureView = null
        cameraListener = null
        mContext = null
    }

    private fun setUpCameraOutputs(cameraManager: CameraManager) {
        try {
            if (configCameraParams(cameraManager, specificCameraId)) {
                return
            }
            for (cameraId in cameraManager.cameraIdList) {
                if (configCameraParams(cameraManager, cameraId)) {
                    return
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            cameraListener?.onCameraError(e)
        }
    }

    @Throws(CameraAccessException::class)
    private fun configCameraParams(manager: CameraManager, cameraId: String): Boolean {
        val characteristics = manager.getCameraCharacteristics(cameraId)

        val map = characteristics.
            get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        map ?: return false
        mPreviewSize = getBestSupportedSize(ArrayList<Size>(map.getOutputSizes(SurfaceTexture::class.java).asList()))
        mImageReader = ImageReader.newInstance(mPreviewSize!!.width, mPreviewSize!!.height,
            ImageFormat.JPEG, 2)
        mImageReader?.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler)

        // noinspection ConstantConditions
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)?.let {
            mSensorOrientation = it
        }
        mCameraId = cameraId
        return true
    }

    /**
     * Opens the camera specified by {@link #mCameraId}
     */
    private fun openCamera() {
        val cameraManager = mContext?.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        cameraManager?: return
        Log.e(TAG, "openCamera")
        setUpCameraOutputs(cameraManager)
        mTextureView?.apply {
            configureTransform(width, height)
        }
        try {
            if (!mCameraOpenLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            mContext?.apply {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mDeviceStateCallback, mBackgroundHandler)
                }
            }
        } catch (e: CameraAccessException) {
            cameraListener?.onCameraError(e)
        } catch (e: InterruptedException) {
            cameraListener?.onCameraError(e)
        }
    }

    /**
     * Closes the current {@link CameraDevice}
     */
    private fun closeCamera() {
        try {
            mCameraOpenLock.acquire()

            mCaptureSession?.close()
            mCaptureSession = null

            mCameraDevice?.close()
            mCameraDevice = null

            cameraListener?.onCameraClosed()

            mImageReader?.close()
            mImageReader = null
        } catch (e: InterruptedException) {
            cameraListener?.onCameraError(e)
        } finally {
            mCameraOpenLock.release()
        }
    }

    /**
     * Starts a background thread and its {@link Handler}
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread?.start()
        mBackgroundHandler = Handler(mBackgroundThread?.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView?.surfaceTexture
            assert(texture != null)

            // We configure the size of default buffer to be the size of camera preview we want.
            mPreviewSize?.let {
                texture?.setDefaultBufferSize(it.width, it.height)
            }

            // This is the output Surface we need to start preview !!!
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface
            mPreviewRequestBuilder =
                mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            // 自动对焦
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            // 增加曝光度
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange())

            mPreviewRequestBuilder?.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice?.createCaptureSession(listOf(surface, mImageReader?.surface),
                mCaptureStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to 'mTextureView'.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of 'mTextureView' is fixed.
     *
     * @param viewWidth   The width of 'mTextureView'
     * @param viewHeight  The height of 'mTextureView'
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        mTextureView?: return
        mPreviewSize?: return
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0F, 0F, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(viewHeight.toFloat() / mPreviewSize!!.height,
                viewWidth.toFloat() / mPreviewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation.toFloat() - 2)) % 360, centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180F, centerX, centerY)
        }
        Log.i(TAG, "configureTransform: ${getCameraOri(rotation, mCameraId)}  ${rotation * 90}")
        mTextureView?.setTransform(matrix)
    }

    fun takePhoto() {
        // 一定要在这里加 不然回调不了 如果在上面加 会导致一直在捕获图像
        mImageReader?.let {
            mPreviewRequestBuilder?.addTarget(it.surface)
        }

        // 这里保存时要选sensorOrientation(照相机的方向)
        mPreviewRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation)

        // 设置对焦触发器为空闲状态
        mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)

        mCaptureSession?.stopRepeating()
        // 开始拍照
        mPreviewRequestBuilder?.let {
            mCaptureSession?.capture(it.build(), null, mBackgroundHandler)
        }
    }

    private fun getRange(): Range<Int>? {
        val mCameraManager = mContext?.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        var chars: CameraCharacteristics? = null
        try {
            chars = mCameraManager?.getCameraCharacteristics(mCameraId)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val ranges = chars?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)

        var result : Range<Int>? = null

        ranges?.let {
            it.forEach { range ->
                if (range.lower < 10) {
                    return@forEach
                }
                if (result == null) {
                    result = range
                } else if (range.lower <= 15 && (range.upper - range.lower) > result!!.upper.minus(result!!.lower)) {
                    result = range
                }
            }
        }
        return result
    }

}