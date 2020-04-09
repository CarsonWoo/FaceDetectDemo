package com.example.facedetectdemo

import android.Manifest
import android.graphics.Point
import android.hardware.camera2.CameraDevice
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.ViewTreeObserver
import android.widget.Button
import com.baidu.aip.util.Base64Util
import com.carson.gdufs_sign_system.utils.CameraHelper
import com.carson.gdufs_sign_system.utils.CameraListener
import com.example.facedetectdemo.bean.DetectFaceBean
import com.example.facedetectdemo.util.AipFaceObject
import com.example.facedetectdemo.util.PermissionUtils
import com.example.facedetectdemo.widget.CircleTextureBorderView
import com.example.facedetectdemo.widget.RoundTextureView
import com.google.gson.Gson
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class MainActivity : AppCompatActivity(), CameraListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_ID = CameraHelper.CAMERA_ID_FRONT
    }

    private lateinit var mTextureView: RoundTextureView
    private lateinit var mBorderView: CircleTextureBorderView
    private lateinit var mBtnSubmit: Button

    private var mCameraHelper: CameraHelper? = null

    private var mIsTakingPhoto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
    }

    private fun initViews() {
        mTextureView = findViewById(R.id.round_texture_view)
        mBorderView = findViewById(R.id.border_view)

        mBtnSubmit = findViewById(R.id.btn_submit)

        mBtnSubmit.setOnClickListener {
            mIsTakingPhoto = true
            mBorderView.setTipsText("提交数据中...")
            mCameraHelper?.takePhoto()
            mBtnSubmit.isEnabled = false
        }

        mTextureView.setOnClickListener {
            mBtnSubmit.isEnabled = true
            setResumePreview()
        }

        mTextureView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mTextureView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val params = mTextureView.layoutParams
                val sideLength = min(mTextureView.width, mTextureView.height * 3 / 4)
                params.width = sideLength
                params.height = sideLength
                mTextureView.layoutParams = params
                mTextureView.turnRound()
                mBorderView.setCircleTextureWidth(sideLength)
                if (PermissionUtils.isGranted(Manifest.permission.CAMERA, applicationContext)) {
                    initCamera()
                } else {
                    PermissionUtils.getInstance().with(this@MainActivity).permissions(Manifest.permission.CAMERA)
                        .requestCode(PermissionUtils.CODE_CAMERA)
                        .request(object : PermissionUtils.PermissionCallback {
                            override fun denied() {
                                PermissionUtils.getInstance().showDialog()
                            }

                            override fun granted() {
                                initCamera()
                            }
                        })
                }
            }
        })
    }

    fun setResumePreview() {
        this.mIsTakingPhoto = false
        switchText("请点击按钮拍照")
        // 先stop再start 重置一下参数
        mCameraHelper?.stop()
        mCameraHelper?.start()
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "main currentThread = ${Thread.currentThread().name}")
        if (!mIsTakingPhoto) {
            mCameraHelper?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!mIsTakingPhoto) {
            mCameraHelper?.stop()
        }
    }

    override fun onDestroy() {
        mCameraHelper?.release()
        PermissionUtils.getInstance().destroy()
        super.onDestroy()
    }

    private fun initCamera() {
        mTextureView ?: return

        mCameraHelper = CameraHelper.Companion.Builder()
            .cameraListener(this)
            .specificCameraId(CAMERA_ID)
            .mContext(applicationContext)
            .previewOn(mTextureView)
            .previewViewSize(
                Point(
                    mTextureView.layoutParams.width,
                    mTextureView.layoutParams.height
                )
            )
            .rotation(windowManager?.defaultDisplay?.rotation ?: 0)
            .build()
        mCameraHelper?.start()
        switchText("请点击按钮拍照")
    }

    override fun onCameraClosed() {

    }

    override fun onCameraError(e: Exception) {

    }

    override fun onCameraOpened(
        cameraDevice: CameraDevice,
        cameraId: String,
        previewSize: Size,
        displayOrientation: Int,
        isMirror: Boolean
    ) {
        Log.i(TAG, "onCameraOpened:  previewSize = ${previewSize.width}  x  ${previewSize.height}")
        // 相机打开时，添加右上角的view用于显示原始数据和预览数据
        runOnUiThread {
            // 将预览控件和预览尺寸比例保持一致 避免拉伸
            val params = mTextureView.layoutParams
            // 横屏
            if (displayOrientation % 180 == 0) {
                params.height = params.width * previewSize.height / previewSize.width
            }
            // 竖屏
            else {
                params.height = params.width * previewSize.width / previewSize.height
            }
            mTextureView.layoutParams = params
        }

    }

    override fun onPreview(byteArray: ByteArray) {
        Log.i(TAG, "onPreview: ")
        runOnUiThread {
            switchText("检测人脸中..")
        }

        // 这里通过Base64转换类将图像数据转换格式 因为SDK检测的都用BASE64的图片
        val postImage = Base64Util.encode(byteArray)

        Thread(Runnable {
            if (detectFace(postImage)) {
                // 检测成功 可以进一步进行比对人脸
            }
        }).start()
    }

    private fun switchText(shadowContent: String) {
        runOnUiThread {
            if (shadowContent.isNotEmpty()) {
                mBorderView.setTipsText(shadowContent)
            }
        }
    }

    private fun detectFace(postImage: String): Boolean {
        var bSuccess = false
        // 人脸检测
        val detectOptions = HashMap<String, String>()
        detectOptions["face_field"] = "age,gender,race,expression,beauty"
        detectOptions["face_type"] = "LIVE"

        val detectRes = AipFaceObject.getClient().detect(postImage, "BASE64", detectOptions)

        val mShadowText: String

        if (detectRes.getInt("error_code") == 0) {
            // 检测成功
            bSuccess = true
            val detectBean = Gson().fromJson<DetectFaceBean>(
                detectRes.getJSONObject("result").toString(), DetectFaceBean::class.java)
            Log.e(TAG, "detect beauty=${detectBean.face_list[0].beauty} and" +
                    " expression=${detectBean.face_list[0].expression.type} and" +
                    " age = ${detectBean.face_list[0].age}")
            mShadowText = "成功检测到人脸"
        } else {
            mShadowText = "检测失败"
        }

        runOnUiThread {
            switchText(mShadowText)
        }
        return bSuccess
    }
}
