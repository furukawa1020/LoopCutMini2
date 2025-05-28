package com.loopcut

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * React Native TurboModule for LoopCut functionality
 */
class LoopCutModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val TAG = "LoopCutModule"
        const val NAME = "LoopCutModule"
        
        // Event names
        const val EVENT_LOOP_DETECTED = "onLoopDetected"
        const val EVENT_TRANSCRIPTION_UPDATE = "onTranscriptionUpdate"
        const val EVENT_SERVICE_STATUS_CHANGED = "onServiceStatusChanged"
    }
    
    private var loopCutService: LoopCutService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as LoopCutService.LocalBinder
            loopCutService = binder.getService()
            serviceBound = true
            
            // Set up callbacks
            loopCutService?.setCallbacks(
                onLoopDetected = { message ->
                    sendEvent(EVENT_LOOP_DETECTED, Arguments.createMap().apply {
                        putString("message", message)
                        putDouble("timestamp", System.currentTimeMillis().toDouble())
                    })
                },
                onTranscriptionUpdate = { transcription ->
                    sendEvent(EVENT_TRANSCRIPTION_UPDATE, Arguments.createMap().apply {
                        putString("transcription", transcription)
                        putDouble("timestamp", System.currentTimeMillis().toDouble())
                    })
                }
            )
            
            // Notify React Native
            sendEvent(EVENT_SERVICE_STATUS_CHANGED, Arguments.createMap().apply {
                putBoolean("connected", true)
                putBoolean("monitoring", loopCutService?.isMonitoring() ?: false)
            })
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            loopCutService = null
            serviceBound = false
            
            // Notify React Native
            sendEvent(EVENT_SERVICE_STATUS_CHANGED, Arguments.createMap().apply {
                putBoolean("connected", false)
                putBoolean("monitoring", false)
            })
        }
    }
    
    override fun getName(): String = NAME
    
    override fun initialize() {
        super.initialize()
        Log.d(TAG, "Module initialized")
        
        // Bind to service
        bindToService()
    }
    
    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        Log.d(TAG, "Module destroyed")
        
        // Unbind from service
        unbindFromService()
    }
    
    /**
     * Start monitoring for negative loops
     */
    @ReactMethod
    fun startMonitoring(promise: Promise) {
        try {
            Log.d(TAG, "Starting monitoring requested")
            
            if (!serviceBound) {
                bindToService()
                // Wait a bit for service to connect
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (serviceBound) {
                        loopCutService?.startMonitoring()
                        promise.resolve(true)
                    } else {
                        promise.reject("SERVICE_ERROR", "Failed to connect to service")
                    }
                }, 1000)
                return
            }
            
            loopCutService?.startMonitoring()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring", e)
            promise.reject("START_ERROR", e.message, e)
        }
    }
    
    /**
     * Stop monitoring
     */
    @ReactMethod
    fun stopMonitoring(promise: Promise) {
        try {
            Log.d(TAG, "Stopping monitoring requested")
            loopCutService?.stopMonitoring()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring", e)
            promise.reject("STOP_ERROR", e.message, e)
        }
    }
    
    /**
     * Check if currently monitoring
     */
    @ReactMethod
    fun isMonitoring(promise: Promise) {
        try {
            val monitoring = loopCutService?.isMonitoring() ?: false
            promise.resolve(monitoring)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking monitoring status", e)
            promise.reject("STATUS_ERROR", e.message, e)
        }
    }
    
    /**
     * Get Whisper model information
     */
    @ReactMethod
    fun getModelInfo(promise: Promise) {
        try {
            val whisperBridge = WhisperBridge.getInstance()
            if (!whisperBridge.initialize(reactApplicationContext)) {
                promise.reject("MODEL_ERROR", "Failed to initialize Whisper")
                return
            }
            
            val modelInfo = whisperBridge.getModelInfo()
            if (modelInfo != null) {
                promise.resolve(modelInfo)
            } else {
                promise.resolve("Model information not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model info", e)
            promise.reject("MODEL_INFO_ERROR", e.message, e)
        }
    }
    
    /**
     * Test vibration with specified strength
     */
    @ReactMethod
    fun testVibration(strengthName: String, promise: Promise) {
        try {
            val strength = when (strengthName.uppercase()) {
                "WEAK" -> Strength.WEAK
                "MEDIUM" -> Strength.MEDIUM
                "STRONG" -> Strength.STRONG
                else -> {
                    promise.reject("INVALID_STRENGTH", "Invalid strength: $strengthName")
                    return
                }
            }
            
            // Start service temporarily to test vibration
            val intent = Intent(reactApplicationContext, LoopCutService::class.java)
            reactApplicationContext.startService(intent)
            
            // Wait for service to be ready, then test vibration
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (serviceBound && loopCutService != null) {
                    // Use reflection to access private method for testing
                    try {
                        val method = LoopCutService::class.java.getDeclaredMethod("triggerVibration", Strength::class.java)
                        method.isAccessible = true
                        method.invoke(loopCutService, strength)
                        promise.resolve(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error triggering test vibration", e)
                        promise.reject("VIBRATION_ERROR", e.message, e)
                    }
                } else {
                    promise.reject("SERVICE_ERROR", "Service not available for vibration test")
                }
            }, 500)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing vibration", e)
            promise.reject("TEST_ERROR", e.message, e)
        }
    }
    
    /**
     * Get statistics from loop detector
     */
    @ReactMethod
    fun getStatistics(promise: Promise) {
        try {
            // Create dummy statistics for now
            val stats = Arguments.createMap().apply {
                putInt("totalProcessedTexts", 0)
                putInt("negativeLoopsDetected", 0)
                putDouble("lastDetectionTime", 0.0)
                putString("status", if (loopCutService?.isMonitoring() == true) "monitoring" else "idle")
            }
            promise.resolve(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting statistics", e)
            promise.reject("STATS_ERROR", e.message, e)
        }
    }
    
    /**
     * Get app configuration
     */
    @ReactMethod
    fun getConfiguration(promise: Promise) {
        try {
            val config = Arguments.createMap().apply {
                putString("version", "1.0.0")
                putString("whisperModel", "ggml-tiny.bin")
                putInt("windowSizeSeconds", 30)
                putInt("negativeWordsThreshold", 3)
                putInt("cooldownMinutes", 5)
                putBoolean("transcriptionEnabled", true)
                putBoolean("vibrationsEnabled", true)
                putBoolean("notificationsEnabled", true)
            }
            promise.resolve(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting configuration", e)
            promise.reject("CONFIG_ERROR", e.message, e)
        }
    }
    
    /**
     * Start the service
     */
    @ReactMethod
    fun startService(promise: Promise) {
        try {
            Log.d(TAG, "Starting LoopCut service")
            val intent = Intent(reactApplicationContext, LoopCutService::class.java)
            reactApplicationContext.startForegroundService(intent)
            bindToService()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service", e)
            promise.reject("SERVICE_START_ERROR", e.message, e)
        }
    }
    
    /**
     * Stop the service
     */
    @ReactMethod
    fun stopService(promise: Promise) {
        try {
            Log.d(TAG, "Stopping LoopCut service")
            loopCutService?.stopMonitoring()
            unbindFromService()
            val intent = Intent(reactApplicationContext, LoopCutService::class.java)
            reactApplicationContext.stopService(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
            promise.reject("SERVICE_STOP_ERROR", e.message, e)
        }
    }
    
    /**
     * Get service status
     */
    @ReactMethod
    fun getServiceStatus(promise: Promise) {
        try {
            val status = Arguments.createMap().apply {
                putBoolean("isRunning", loopCutService?.isMonitoring() ?: false)
                putInt("detectedLoops", loopCutService?.getDetectedLoopsCount() ?: 0)
                putString("lastDetection", loopCutService?.getLastDetectionTime())
            }
            promise.resolve(status)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting service status", e)
            promise.reject("STATUS_ERROR", e.message, e)
        }
    }
    
    /**
     * Reset statistics
     */
    @ReactMethod
    fun resetStats(promise: Promise) {
        try {
            loopCutService?.resetStats()
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting stats", e)
            promise.reject("RESET_ERROR", e.message, e)
        }
    }
    
    /**
     * Request necessary permissions
     */
    @ReactMethod
    fun requestPermissions(promise: Promise) {
        try {
            // Check if we have record audio permission
            val hasAudioPermission = reactApplicationContext.checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasAudioPermission) {
                promise.resolve(true)
            } else {
                // Request permission through current activity
                val activity = currentActivity
                if (activity != null) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        activity as androidx.appcompat.app.AppCompatActivity,
                        arrayOf(android.Manifest.permission.RECORD_AUDIO),
                        100
                    )
                    promise.resolve(false) // Will need to check again later
                } else {
                    promise.reject("PERMISSION_ERROR", "No activity available for permission request")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions", e)
            promise.reject("PERMISSION_ERROR", e.message, e)
        }
    }

    /**
     * Bind to LoopCutService
     */
    private fun bindToService() {
        if (serviceBound) return
        
        try {
            val intent = Intent(reactApplicationContext, LoopCutService::class.java)
            reactApplicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Binding to service")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to service", e)
        }
    }
    
    /**
     * Unbind from LoopCutService
     */
    private fun unbindFromService() {
        if (!serviceBound) return
        
        try {
            reactApplicationContext.unbindService(serviceConnection)
            serviceBound = false
            loopCutService = null
            Log.d(TAG, "Unbound from service")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding from service", e)
        }
    }
    
    /**
     * Send event to React Native
     */
    private fun sendEvent(eventName: String, params: WritableMap) {
        try {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event: $eventName", e)
        }
    }
    
    /**
     * Return constants available to React Native
     */
    override fun getConstants(): MutableMap<String, Any> {
        return hashMapOf(
            "VIBRATION_STRENGTHS" to mapOf(
                "WEAK" to "WEAK",
                "MEDIUM" to "MEDIUM", 
                "STRONG" to "STRONG"
            ),
            "EVENTS" to mapOf(
                "LOOP_DETECTED" to EVENT_LOOP_DETECTED,
                "TRANSCRIPTION_UPDATE" to EVENT_TRANSCRIPTION_UPDATE,
                "SERVICE_STATUS_CHANGED" to EVENT_SERVICE_STATUS_CHANGED
            )
        )
    }
}
