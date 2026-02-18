package com.example.signtranslator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class UiState(
    val confirmedLabel:  String? = null,   // letter/word accepted into sentence
    val rawLabel:        String? = null,   // live candidate (shows user what's being considered)
    val sentence:        String  = "",
    val processingMode:  String  = "CPU",
    val inferenceTime:   Long    = 0L,
    val confidence:      Float   = 0f,
    val fps:             Int     = 0,
    val mpGesture:       String  = "None"
)

class MainViewModel : ViewModel() {

    private val sentenceManager = SentenceManager()

    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> = _uiState

    // FPS tracking
    private var lastFpsTime = System.currentTimeMillis()
    private var frameCount  = 0
    private var currentFps  = 0

    // ── called once per confirmed letter/word ──────────────────────────────
    fun onConfirmedLabel(label: String) {
        sentenceManager.addLetter(label)
        _uiState.value = _uiState.value?.copy(
            confirmedLabel = label,
            sentence       = sentenceManager.getSentence()
        )
    }

    // ── called every frame with raw detection result ───────────────────────
    fun updateFrameState(
        confirmedLabel: String?,
        rawLabel:       String?,
        mpGesture:      String,
        inferenceTime:  Long,
        mode:           String,
        confidence:     Float
    ) {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000L) {
            currentFps  = frameCount
            frameCount  = 0
            lastFpsTime = now
        }

        _uiState.value = _uiState.value?.copy(
            confirmedLabel = confirmedLabel ?: _uiState.value?.confirmedLabel,
            rawLabel       = rawLabel,
            mpGesture      = mpGesture,
            inferenceTime  = inferenceTime,
            processingMode = mode,
            fps            = currentFps,
            confidence     = confidence
        )
    }

    fun clearSentence() {
        sentenceManager.clearSentence()
        _uiState.value = _uiState.value?.copy(
            sentence       = sentenceManager.getSentence(),
            confirmedLabel = null
        )
    }

    fun deleteLastCharacter() {
        sentenceManager.deleteLastCharacter()
        _uiState.value = _uiState.value?.copy(sentence = sentenceManager.getSentence())
    }

    fun addSpace() {
        sentenceManager.addSpace()
        _uiState.value = _uiState.value?.copy(sentence = sentenceManager.getSentence())
    }
}
