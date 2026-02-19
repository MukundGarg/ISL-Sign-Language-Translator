package com.example.signtranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.isltranslator.ui.ISLTranslatorScreen
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), GestureRecognizerHelper.GestureRecognizerListener {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private var previewView: PreviewView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        // GPU is attempted first; helper falls back to CPU automatically
        gestureRecognizerHelper = GestureRecognizerHelper(
            context                  = this,
            gestureRecognizerListener = this,
            currentDelegate          = GestureRecognizerHelper.DELEGATE_GPU
        )

        setContent {
            ISLTranslatorScreen(
                viewModel = viewModel,
                cameraPreview = { modifier ->
                    CameraPreview(modifier)
                }
            )
        }

        // ── Camera permission ──────────────────────────────────────────────
        if (allPermissionsGranted()) {
            // Camera will be started when PreviewView is created in CameraPreview Composable
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @Composable
    private fun CameraPreview(modifier: Modifier) {
        AndroidView(
            factory = { context ->
                PreviewView(context).also {
                    previewView = it
                    if (allPermissionsGranted()) {
                        startCamera()
                    }
                }
            },
            modifier = modifier
        )
    }

    // ── Camera setup ───────────────────────────────────────────────────────

    private fun startCamera() {
        val viewFinder = previewView ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        gestureRecognizerHelper.recognizeLiveStream(imageProxy)
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera binding failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── GestureRecognizerListener ──────────────────────────────────────────

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        runOnUiThread {
            val confirmed = resultBundle.alphabetResult.label
            if (confirmed != null) {
                viewModel.onConfirmedLabel(confirmed)
            }
            viewModel.updateFrameState(
                confirmedLabel = confirmed,
                rawLabel       = resultBundle.rawLabel,
                mpGesture      = resultBundle.mpGesture,
                inferenceTime  = resultBundle.inferenceTime,
                mode           = resultBundle.activeDelegate,
                confidence     = resultBundle.rawConfidence
            )
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            if (errorCode == GestureRecognizerHelper.GPU_ERROR) {
                gestureRecognizerHelper.switchDelegate(GestureRecognizerHelper.DELEGATE_CPU)
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        gestureRecognizerHelper.clearGestureRecognizer()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else finish() }
}
