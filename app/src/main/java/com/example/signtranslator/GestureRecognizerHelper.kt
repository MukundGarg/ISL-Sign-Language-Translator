package com.example.signtranslator

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.Executors

class GestureRecognizerHelper(
    val context: Context,
    val gestureRecognizerListener: GestureRecognizerListener,
    @Volatile var currentDelegate: Int = DELEGATE_GPU
) {

    private var gestureRecognizer: GestureRecognizer? = null
    private val landmarkClassifier = LandmarkAlphabetClassifier()

    // ── Frame-stability ring buffer ────────────────────────────────────────
    private val STABLE_FRAMES   = 14          // frames a label must persist
    private val COOLDOWN_MS     = 350L        // min gap between confirmed outputs
    private val NONE_CLEAR_FRAMES = 20        // blank frames before resetting

    private var stableLabel: String? = null
    private var stableCount: Int = 0
    private var noneCount:   Int = 0
    private var lastConfirmedTime: Long = 0L
    private var lastConfirmedLabel: String? = null

    // ── GPU-init executor (background) ────────────────────────────────────
    private val initExecutor = Executors.newSingleThreadExecutor()

    init {
        initRecognizerAsync(currentDelegate)
    }

    // ──────────────────────────── setup ───────────────────────────────────

    private fun initRecognizerAsync(delegate: Int) {
        initExecutor.execute {
            val success = trySetupRecognizer(delegate)
            if (!success && delegate == DELEGATE_GPU) {
                Log.w(TAG, "GPU init failed — falling back to CPU")
                currentDelegate = DELEGATE_CPU
                trySetupRecognizer(DELEGATE_CPU)
            }
        }
    }

    @Synchronized
    private fun trySetupRecognizer(delegate: Int): Boolean {
        closeRecognizerInternal()
        return try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(if (delegate == DELEGATE_GPU) Delegate.GPU else Delegate.CPU)
                .setModelAssetPath(MP_RECOGNIZER_TASK)
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.6f)
                .setMinHandPresenceConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
            currentDelegate  = delegate
            Log.d(TAG, "Recognizer ready on ${if (delegate == DELEGATE_GPU) "GPU" else "CPU"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Recognizer init error (${if (delegate == DELEGATE_GPU) "GPU" else "CPU"}): ${e.message}")
            false
        }
    }

    private fun closeRecognizerInternal() {
        runCatching { gestureRecognizer?.close() }
        gestureRecognizer = null
    }

    fun clearGestureRecognizer() {
        initExecutor.execute { closeRecognizerInternal() }
    }

    fun switchDelegate(newDelegate: Int) {
        if (newDelegate == currentDelegate && gestureRecognizer != null) return
        Log.d(TAG, "Switching delegate to ${if (newDelegate == DELEGATE_GPU) "GPU" else "CPU"}")
        initRecognizerAsync(newDelegate)
    }

    // ──────────────────────────── live stream ─────────────────────────────

    fun recognizeLiveStream(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val bitmap    = imageProxy.toBitmap()
        if (bitmap != null) {
            val mpImage = BitmapImageBuilder(bitmap).build()
            synchronized(this) { gestureRecognizer }?.recognizeAsync(mpImage, frameTime)
        }
    }

    // ──────────────────── stability + cooldown logic ──────────────────────

    private fun returnLivestreamResult(result: GestureRecognizerResult, input: MPImage) {
        val finishTime    = SystemClock.uptimeMillis()
        val inferenceTime = finishTime - result.timestampMs()

        val landmarks      = result.landmarks()
        val mpGestureScore = result.gestures().firstOrNull()?.firstOrNull()?.score() ?: 0f
        val mpGestureName  = result.gestures().firstOrNull()?.firstOrNull()?.categoryName() ?: "None"

        // ── Classify from landmarks ──────────────────────────────────────
        val raw = if (landmarks.isNotEmpty()) {
            landmarkClassifier.classify(landmarks[0], mpGestureScore)
        } else {
            PredictionResult(null, 0f)
        }

        val currentLabel = raw.label

        // ── Frame stability accumulation ─────────────────────────────────
        when {
            currentLabel == null -> {
                noneCount++
                if (noneCount >= NONE_CLEAR_FRAMES) {
                    // Hand left the frame long enough — fully reset
                    stableLabel = null
                    stableCount = 0
                }
            }
            currentLabel == stableLabel -> {
                stableCount++
                noneCount = 0
            }
            else -> {
                stableLabel = currentLabel
                stableCount = 1
                noneCount   = 0
            }
        }

        // ── Confirm only when stable AND past cooldown ────────────────────
        val now = SystemClock.uptimeMillis()
        val confirmedLabel: String? = when {
            stableCount  >= STABLE_FRAMES &&
            stableLabel  != null          &&
            (now - lastConfirmedTime) > COOLDOWN_MS -> {
                // Avoid emitting the same label continuously in one "hold"
                if (stableLabel != lastConfirmedLabel || (now - lastConfirmedTime) > COOLDOWN_MS * 3) {
                    lastConfirmedTime  = now
                    lastConfirmedLabel = stableLabel
                    stableLabel
                } else null
            }
            else -> null
        }

        gestureRecognizerListener.onResults(
            ResultBundle(
                results        = result,
                alphabetResult = PredictionResult(confirmedLabel, raw.confidence),
                rawLabel       = currentLabel,
                rawConfidence  = raw.confidence,
                mpGesture      = mpGestureName,
                inferenceTime  = inferenceTime,
                activeDelegate = if (currentDelegate == DELEGATE_GPU) "GPU" else "CPU"
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        Log.e(TAG, "Livestream error: ${error.message}")
        if (currentDelegate == DELEGATE_GPU) {
            gestureRecognizerListener.onError("GPU error — switching to CPU", GPU_ERROR)
            switchDelegate(DELEGATE_CPU)
        } else {
            gestureRecognizerListener.onError(error.message ?: "Unknown error", OTHER_ERROR)
        }
    }

    // ──────────────────────────── interface ───────────────────────────────

    interface GestureRecognizerListener {
        fun onError(error: String, errorCode: Int)
        fun onResults(resultBundle: ResultBundle)
    }

    data class ResultBundle(
        val results:        GestureRecognizerResult,
        val alphabetResult: PredictionResult,   // confirmed (non-null only once per cooldown)
        val rawLabel:       String?,             // current instantaneous label (for display)
        val rawConfidence:  Float,
        val mpGesture:      String,
        val inferenceTime:  Long,
        val activeDelegate: String
    )

    companion object {
        const val DELEGATE_CPU  = 0
        const val DELEGATE_GPU  = 1
        const val OTHER_ERROR   = 2
        const val GPU_ERROR     = 1
        const val MP_RECOGNIZER_TASK = "gesture_recognizer.task"
        const val TAG = "GestureRecognizerHelper"
    }
}
