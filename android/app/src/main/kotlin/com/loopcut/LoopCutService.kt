package com.loopcut

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service for continuous speech monitoring and negative loop detection
 */
class LoopCutService : Service() {
    companion object {
        private const val TAG = "LoopCutService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "loopcut_monitoring"
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        
        // Processing configuration
        private const val AUDIO_CHUNK_DURATION_MS = 3000 // 3 seconds
        private const val PROCESSING_INTERVAL_MS = 1000L // Process every 1 second
    }
    
    // Service components
    private val binder = LocalBinder()
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
      // AI components
    private val whisperBridge = WhisperBridge.getInstance()
    private lateinit var loopDetector: LoopDetector
    
    // System services
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator
    private lateinit var wakeLock: PowerManager.WakeLock
    
    // UI handlers
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Callbacks
    private var onLoopDetected: ((String) -> Unit)? = null
    private var onTranscriptionUpdate: ((String) -> Unit)? = null
    
    // Statistics
    private var detectedLoopsCount = 0
    private var lastDetectionTime: String? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): LoopCutService = this@LoopCutService
    }
      override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize LoopDetector with context
        loopDetector = LoopDetector(this)
        
        // Initialize system services
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Acquire wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LoopCut::MonitoringWakeLock"
        )
        
        // Initialize Whisper
        if (!whisperBridge.initialize(this)) {
            Log.e(TAG, "Failed to initialize Whisper")
            stopSelf()
            return
        }
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopMonitoring()
        whisperBridge.release()
        serviceScope.cancel()
        
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        super.onDestroy()
    }
    
    /**
     * Start audio monitoring
     */
    fun startMonitoring() {
        if (isRecording.get()) {
            Log.d(TAG, "Already monitoring")
            return
        }
        
        if (!checkPermissions()) {
            Log.e(TAG, "Missing required permissions")
            return
        }
        
        Log.i(TAG, "Starting audio monitoring")
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification("音声監視中..."))
        
        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L) // 10 minutes max
        }
        
        // Initialize audio recording
        initAudioRecord()
        
        if (audioRecord == null) {
            Log.e(TAG, "Failed to initialize audio recording")
            stopSelf()
            return
        }
        
        // Start recording
        isRecording.set(true)
        recordingJob = serviceScope.launch {
            processAudioStream()
        }
    }
    
    /**
     * Stop audio monitoring
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping audio monitoring")
        
        isRecording.set(false)
        recordingJob?.cancel()
        
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
        
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Set callbacks for UI updates
     */
    fun setCallbacks(
        onLoopDetected: ((String) -> Unit)? = null,
        onTranscriptionUpdate: ((String) -> Unit)? = null
    ) {
        this.onLoopDetected = onLoopDetected
        this.onTranscriptionUpdate = onTranscriptionUpdate
    }
    
    /**
     * Check if service is currently monitoring
     */
    fun isMonitoring(): Boolean = isRecording.get()
    
    /**
     * Initialize AudioRecord
     */
    private fun initAudioRecord() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_FACTOR
            
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "No RECORD_AUDIO permission")
                return
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
            } else {
                Log.d(TAG, "AudioRecord initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AudioRecord initialization", e)
            audioRecord?.release()
            audioRecord = null
        }
    }
    
    /**
     * Main audio processing loop
     */
    private suspend fun processAudioStream() {
        val audioRecord = this.audioRecord ?: return
        
        try {
            audioRecord.startRecording()
            Log.d(TAG, "Audio recording started")
            
            val chunkSize = (SAMPLE_RATE * AUDIO_CHUNK_DURATION_MS / 1000) * 2 // 16-bit = 2 bytes
            val buffer = ByteArray(chunkSize)
            
            while (isRecording.get()) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                
                if (bytesRead > 0) {
                    // Process audio chunk
                    processAudioChunk(buffer.copyOf(bytesRead))
                } else {
                    Log.w(TAG, "AudioRecord read error: $bytesRead")
                }
                
                // Control processing frequency
                delay(PROCESSING_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in audio processing", e)
        } finally {
            try {
                audioRecord.stop()
                Log.d(TAG, "Audio recording stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio recording", e)
            }
        }
    }
      /**
     * Process audio chunk for transcription and loop detection
     */
    private suspend fun processAudioChunk(audioData: ByteArray) {
        try {
            // Convert PCM to float
            val floatData = whisperBridge.convertPcmToFloat(audioData)
            
            // Apply pre-emphasis filter for better recognition
            val filteredData = whisperBridge.applyPreEmphasis(floatData)
            
            // Check if audio contains speech
            if (!whisperBridge.containsSpeech(filteredData)) {
                return
            }
            
            // Resample if needed (though we're already recording at 16kHz)
            val resampledData = whisperBridge.resampleAudio(filteredData, SAMPLE_RATE)
            
            // Transcribe audio
            val transcription = whisperBridge.transcribe(resampledData) ?: return
            
            Log.d(TAG, "Transcription: $transcription")
            
            // Update UI with transcription
            mainHandler.post {
                onTranscriptionUpdate?.invoke(transcription)
            }
            
            // Check for negative loops
            val loopDetected = loopDetector.processText(transcription)
            
            if (loopDetected) {
                Log.w(TAG, "Negative loop detected!")
                handleLoopDetection(transcription)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception processing audio chunk", e)
        }
    }
    
    /**
     * Handle loop detection - vibrate and show notification
     */
    private fun handleLoopDetection(transcription: String) {
        // Trigger vibration
        triggerVibration(Strength.MEDIUM)
        
        // Get random encouragement message
        val encouragement = getRandomEncouragement()
        
        // Show heads-up notification
        showEncouragementNotification(encouragement)
        
        // Notify UI
        mainHandler.post {
            onLoopDetected?.invoke(encouragement)
        }
        
        // Update statistics
        updateDetectionStatistics()
        
        Log.i(TAG, "Loop detection handled: $encouragement")
    }
    
    /**
     * Trigger vibration with specified strength
     */
    private fun triggerVibration(strength: Strength) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    strength.pattern.toLongArray(),
                    -1 // Don't repeat
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(strength.pattern.toLongArray(), -1)
            }
            Log.d(TAG, "Vibration triggered with strength: $strength")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger vibration", e)
        }
    }
    
    /**
     * Get random encouragement message
     */
    private fun getRandomEncouragement(): String {
        val messages = resources.getStringArray(R.array.encouragement_phrases)
        return messages.random()
    }
    
    /**
     * Show encouragement notification
     */
    private fun showEncouragementNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LoopCut")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()
        
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LoopCut Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ネガティブループ監視"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create ongoing notification for foreground service
     */
    private fun createNotification(contentText: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LoopCut")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Check required permissions
     */
    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
      /**
     * Update detection statistics
     */
    private fun updateDetectionStatistics() {
        detectedLoopsCount++
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
        lastDetectionTime = dateFormat.format(java.util.Date())
        
        Log.d(TAG, "Detection statistics updated: Count = $detectedLoopsCount, Last Time = $lastDetectionTime")
    }
    
    /**
     * Get the number of detected loops
     */
    fun getDetectedLoopsCount(): Int = detectedLoopsCount
    
    /**
     * Get the last detection time as formatted string
     */
    fun getLastDetectionTime(): String? = lastDetectionTime
    
    /**
     * Reset statistics
     */
    fun resetStats() {
        detectedLoopsCount = 0
        lastDetectionTime = null
        Log.d(TAG, "Statistics reset")
    }
}
