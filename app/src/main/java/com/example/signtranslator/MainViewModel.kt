package com.example.signtranslator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class UiState(
    val confirmedLabel:  String? = null,
    val rawLabel:        String? = null,
    val sentence:        String  = "",
    val processingMode:  String  = "CPU",
    val inferenceTime:   Long    = 0L,
    val confidence:      Float   = 0f,
    val fps:             Int     = 0,
    val mpGesture:       String  = "None",
    val aiOutput:        String  = "" // Real-time LLM output
)

class MainViewModel(private val runAnywhereHelper: RunAnywhereHelper) : ViewModel() {

    private val sentenceManager = SentenceManager()

    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> = _uiState

    private var lastFpsTime = System.currentTimeMillis()
    private var frameCount  = 0
    private var currentFps  = 0

    fun onConfirmedLabel(label: String) {
        sentenceManager.addLetter(label)
        val currentSentence = sentenceManager.getSentence()
        _uiState.value = _uiState.value?.copy(
            confirmedLabel = label,
            sentence       = currentSentence
        )
        
        // Trigger real-time streaming translation whenever a new letter is confirmed
        if (currentSentence.isNotEmpty()) {
            streamTranslate(currentSentence)
        }
    }

    private fun streamTranslate(gestures: String) {
        viewModelScope.launch {
            var accumulatedText = ""
            runAnywhereHelper.streamTranslation(gestures).collect { token ->
                accumulatedText += token
                _uiState.value = _uiState.value?.copy(aiOutput = accumulatedText)
            }
        }
    }

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
            confirmedLabel = null,
            aiOutput       = ""
        )
    }

    fun deleteLastCharacter() {
        sentenceManager.deleteLastCharacter()
        val currentSentence = sentenceManager.getSentence()
        _uiState.value = _uiState.value?.copy(sentence = currentSentence)
        if (currentSentence.isNotEmpty()) {
            streamTranslate(currentSentence)
        } else {
            _uiState.value = _uiState.value?.copy(aiOutput = "")
        }
    }

    fun addSpace() {
        sentenceManager.addSpace()
        _uiState.value = _uiState.value?.copy(sentence = sentenceManager.getSentence())
    }
}
