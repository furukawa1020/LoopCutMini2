package com.loopcut

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JNI bridge for Whisper.cpp integration
 * Handles audio transcription using local Whisper model
 */
class WhisperBridge private constructor() {
    companion object {
        private const val TAG = "WhisperBridge"
        private const val MODEL_FILENAME = "ggml-tiny.bin"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1
        
        @Volatile
        private var INSTANCE: WhisperBridge? = null
        
        fun getInstance(): WhisperBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WhisperBridge().also { INSTANCE = it }
            }
        }
        
        init {
            try {
                System.loadLibrary("whisper_android")
                Log.d(TAG, "Whisper native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load Whisper native library", e)
            }
        }
    }
    
    private var isInitialized = false
    private var modelPath: String? = null
    
    /**
     * Initialize Whisper with model from assets
     */
    fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Whisper already initialized")
            return true
        }
        
        try {
            // Extract model from assets to internal storage
            modelPath = extractModelFromAssets(context)
            if (modelPath == null) {
                Log.e(TAG, "Failed to extract model from assets")
                return false
            }
            
            // Initialize native Whisper
            val result = nativeInit(modelPath!!)
            if (result) {
                isInitialized = true
                Log.i(TAG, "Whisper initialized successfully with model: $modelPath")
            } else {
                Log.e(TAG, "Failed to initialize Whisper native")
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Whisper initialization", e)
            return false
        }
    }
    
    /**
     * Transcribe audio data to text
     * @param audioData PCM audio data (16kHz, mono, 16-bit)
     * @return transcribed text or null if failed
     */
    fun transcribe(audioData: FloatArray): String? {
        if (!isInitialized) {
            Log.e(TAG, "Whisper not initialized")
            return null
        }
        
        if (audioData.isEmpty()) {
            Log.w(TAG, "Empty audio data provided")
            return null
        }
        
        try {
            val result = nativeTranscribe(audioData)
            Log.d(TAG, "Transcription result: $result")
            return result?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during transcription", e)
            return null
        }
    }
      /**
     * Convert PCM byte array to float array for Whisper
     * @param pcmData PCM data (16-bit, little-endian)
     * @return normalized float array [-1.0, 1.0]
     */
    fun convertPcmToFloat(pcmData: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(pcmData)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        val floatArray = FloatArray(pcmData.size / 2)
        for (i in floatArray.indices) {
            // Convert 16-bit PCM to float [-1.0, 1.0]
            val sample = buffer.short
            floatArray[i] = sample / 32768.0f
        }
        
        return floatArray
    }
    
    /**
     * Resample audio to 16kHz if needed
     * @param audioData input audio data
     * @param inputSampleRate input sample rate
     * @return resampled audio data
     */
    fun resampleAudio(audioData: FloatArray, inputSampleRate: Int): FloatArray {
        if (inputSampleRate == SAMPLE_RATE) {
            return audioData
        }
        
        val ratio = inputSampleRate.toDouble() / SAMPLE_RATE.toDouble()
        val outputLength = (audioData.size / ratio).toInt()
        val resampled = FloatArray(outputLength)
        
        for (i in resampled.indices) {
            val sourceIndex = (i * ratio).toInt()
            if (sourceIndex < audioData.size) {
                resampled[i] = audioData[sourceIndex]
            }
        }
        
        return resampled
    }
    
    /**
     * Apply pre-emphasis filter to improve recognition quality
     * @param audioData input audio data
     * @return filtered audio data
     */
    fun applyPreEmphasis(audioData: FloatArray, alpha: Float = 0.97f): FloatArray {
        if (audioData.isEmpty()) return audioData
        
        val filtered = FloatArray(audioData.size)
        filtered[0] = audioData[0]
        
        for (i in 1 until audioData.size) {
            filtered[i] = audioData[i] - alpha * audioData[i - 1]
        }
        
        return filtered
    }
    
    /**
     * Check if audio data contains speech
     * Simple energy-based voice activity detection
     */
    fun containsSpeech(audioData: FloatArray, threshold: Float = 0.01f): Boolean {
        if (audioData.isEmpty()) return false
        
        // Calculate RMS energy
        var energy = 0.0
        for (sample in audioData) {
            energy += sample * sample
        }
        energy = kotlin.math.sqrt(energy / audioData.size)
        
        return energy > threshold
    }
    
    /**
     * Get model information
     */
    fun getModelInfo(): String? {
        return if (isInitialized) {
            nativeGetModelInfo()
        } else {
            null
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        if (isInitialized) {
            nativeRelease()
            isInitialized = false
            Log.i(TAG, "Whisper resources released")
        }
    }
    
    /**
     * Extract Whisper model from assets to internal storage
     */
    private fun extractModelFromAssets(context: Context): String? {
        return try {
            val internalDir = File(context.filesDir, "whisper")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            
            val modelFile = File(internalDir, MODEL_FILENAME)
            
            // Check if model already exists and is valid
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Model already exists: ${modelFile.absolutePath}")
                return modelFile.absolutePath
            }
            
            // Extract from assets
            val inputStream: InputStream = context.assets.open("models/$MODEL_FILENAME")
            modelFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            Log.i(TAG, "Model extracted to: ${modelFile.absolutePath}")
            modelFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract model from assets", e)
            null
        }
    }
    
    // Native method declarations
    private external fun nativeInit(modelPath: String): Boolean
    private external fun nativeTranscribe(audioData: FloatArray): String?
    private external fun nativeGetModelInfo(): String?
    private external fun nativeRelease()
}
