package com.example.edgedetectviewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.hardware.camera2.*
import android.opengl.GLSurfaceView

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val REQ_CAMERA = 100

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var fpsText: TextView
    private lateinit var toggleButton: ImageButton
    private lateinit var renderer: GLRenderer

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var imageReader: ImageReader
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    private var showRaw = false
    private var frames = 0
    private var lastFpsTs = System.nanoTime()

    // orientation fields
    private var sensorOrientationDegrees = 0
    private var totalRotationDegrees = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        fpsText = findViewById(R.id.fpsText)
        toggleButton = findViewById(R.id.toggleButton)

        renderer = GLRenderer()
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        toggleButton.setOnClickListener {
            showRaw = !showRaw
        }

        startCameraThread()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Log.w(TAG, "Camera permission denied")
            }
        }
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread")
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)
    }

    private fun stopCameraThread() {
        cameraThread.quitSafely()
        try {
            cameraThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        try {
            val camId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(camId)

            // get sensor orientation (0/90/180/270)
            sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // compute device/display rotation in degrees
            val rotation = windowManager.defaultDisplay.rotation
            val deviceRotationDegrees = when (rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            // total rotation to apply to sensor frames so they appear upright on screen
            totalRotationDegrees = (sensorOrientationDegrees - deviceRotationDegrees + 360) % 360

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: arrayOf(Size(640, 480))
            val chosen = chooseSize(sizes, 640, 480)

            imageReader = ImageReader.newInstance(chosen.width, chosen.height, ImageFormat.YUV_420_888, 2)
            imageReader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            cameraManager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(chosen)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera error: ${e.message}")
        }
    }

    private fun createCaptureSession(chosenSize: Size) {
        val surfaceTexture = android.graphics.SurfaceTexture(10)
        surfaceTexture.setDefaultBufferSize(chosenSize.width, chosenSize.height)
        val previewSurface = Surface(surfaceTexture)
        val readerSurface = imageReader.surface

        try {
            cameraDevice?.createCaptureSession(listOf(previewSurface, readerSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(readerSurface)
                    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession error: ${e.message}")
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        val width = image.width
        val height = image.height

        val nv21 = yuv420ToNV21(image)
        // close image as soon as possible (we already copied the bytes)
        image.close()

        if (nv21 != null) {
            var rgba = NativeBridge.processFrameNV21(nv21, width, height)
            if (rgba != null) {
                if (totalRotationDegrees != 0) {
                    // rotate the RGBA bytes on CPU, swap dims for 90/270
                    val (rotated, newW, newH) = rotateRGBAKeepDims(rgba, width, height, totalRotationDegrees)
                    renderer.updateFrame(rotated, newW, newH)
                } else {
                    renderer.updateFrame(rgba, width, height)
                }
            }

            frames++
            val now = System.nanoTime()
            if (now - lastFpsTs >= 1_000_000_000L) {
                runOnUiThread { fpsText.text = "FPS: $frames" }
                frames = 0
                lastFpsTs = now
            }
        }
    }

    /**
     * Convert YUV_420_888 Image -> NV21 byte array
     */
    private fun yuv420ToNV21(image: Image): ByteArray? {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val rowStrideY = yPlane.rowStride
        val rowStrideU = uPlane.rowStride
        val rowStrideV = vPlane.rowStride
        val pixelStrideU = uPlane.pixelStride
        val pixelStrideV = vPlane.pixelStride

        var pos = 0
        if (rowStrideY == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            val row = ByteArray(rowStrideY)
            for (i in 0 until height) {
                yBuffer.get(row, 0, rowStrideY)
                System.arraycopy(row, 0, nv21, pos, width)
                pos += width
            }
        }

        val uvHeight = height / 2
        val uvWidth = width / 2

        vBuffer.position(0)
        uBuffer.position(0)
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * rowStrideV + col * pixelStrideV
                val uIndex = row * rowStrideU + col * pixelStrideU
                val v = vBuffer.get(vIndex)
                val u = uBuffer.get(uIndex)
                nv21[pos++] = v
                nv21[pos++] = u
            }
        }

        return nv21
    }

    private fun chooseSize(sizes: Array<Size>, reqW: Int, reqH: Int): Size {
        var best: Size? = null
        for (s in sizes) {
            if (s.width == reqW && s.height == reqH) return s
            if (s.width >= reqW && s.height >= reqH) {
                if (best == null) best = s
                else if (s.width * s.height < best.width * best.height) best = s
            }
        }
        return best ?: sizes[0]
    }

    /**
     * Rotate RGBA byte array (4 bytes per pixel).
     * Returns Triple(rotatedByteArray, newWidth, newHeight).
     * Supports 90/180/270 degrees. If degrees == 0 returns original.
     */
    private fun rotateRGBAKeepDims(src: ByteArray, width: Int, height: Int, degrees: Int): Triple<ByteArray, Int, Int> {
        if (degrees == 0) return Triple(src, width, height)

        when (degrees) {
            180 -> {
                val dst = ByteArray(src.size)
                val rowStride = width * 4
                for (y in 0 until height) {
                    val srcRow = y * rowStride
                    val dstRow = (height - 1 - y) * rowStride
                    for (x in 0 until rowStride) {
                        dst[dstRow + (rowStride - 1 - x)] = src[srcRow + x]
                    }
                }
                return Triple(dst, width, height)
            }
            90, 270 -> {
                val newW = height
                val newH = width
                val dst = ByteArray(src.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val srcIndex = (y * width + x) * 4
                        val (dstX, dstY) = if (degrees == 90) {
                            // (x,y) -> (height - 1 - y, x)
                            Pair(height - 1 - y, x)
                        } else {
                            // 270: (x,y) -> (y, width - 1 - x)
                            Pair(y, width - 1 - x)
                        }
                        val dstIndex = (dstY * newW + dstX) * 4
                        dst[dstIndex]     = src[srcIndex]
                        dst[dstIndex + 1] = src[srcIndex + 1]
                        dst[dstIndex + 2] = src[srcIndex + 2]
                        dst[dstIndex + 3] = src[srcIndex + 3]
                    }
                }
                return Triple(dst, newW, newH)
            }
            else -> {
                // unsupported rotation; fallback
                return Triple(src, width, height)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureSession?.close()
        cameraDevice?.close()
        if (::imageReader.isInitialized) imageReader.close()
        stopCameraThread()
    }
}
