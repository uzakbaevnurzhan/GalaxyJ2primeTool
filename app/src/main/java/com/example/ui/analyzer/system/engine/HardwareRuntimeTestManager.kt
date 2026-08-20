package com.example.ui.analyzer.system.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.ui.analyzer.system.models.ComponentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HardwareRuntimeTestResult(
    val testName: String,
    val status: ComponentStatus,
    val message: String,
    val evidence: String,
    val rawData: Map<String, String> = emptyMap()
)

object HardwareRuntimeTestManager {

    fun testSpeaker(context: Context): HardwareRuntimeTestResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                return HardwareRuntimeTestResult(
                    "Speaker",
                    ComponentStatus.FAILED,
                    "AudioManager service unavailable",
                    "getSystemService(AUDIO_SERVICE) returned null"
                )
            }
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
            HardwareRuntimeTestResult(
                "Speaker",
                ComponentStatus.WORKING,
                "Audible test tone triggered on STREAM_MUSIC",
                "ToneGenerator tone TONE_PROP_BEEP played successfully, volume=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}"
            )
        } catch (e: Exception) {
            HardwareRuntimeTestResult(
                "Speaker",
                ComponentStatus.FAILED,
                "Speaker test failed: ${e.message}",
                "Exception: ${e.stackTraceToString()}"
            )
        }
    }

    fun testVibrator(context: Context): HardwareRuntimeTestResult {
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) {
                return HardwareRuntimeTestResult(
                    "Vibrator",
                    ComponentStatus.UNAVAILABLE,
                    "Vibrator hardware not detected",
                    "vibrator.hasVibrator() is false or null"
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }

            HardwareRuntimeTestResult(
                "Vibrator",
                ComponentStatus.WORKING,
                "Vibration impulse (300ms) executed",
                "Vibrator triggered with DEFAULT_AMPLITUDE, hasAmplitudeControl=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.hasAmplitudeControl() else "N/A"}"
            )
        } catch (e: Exception) {
            HardwareRuntimeTestResult(
                "Vibrator",
                ComponentStatus.FAILED,
                "Vibration failed: ${e.message}",
                "Exception: ${e.stackTraceToString()}"
            )
        }
    }

    fun testCameraSensors(context: Context): HardwareRuntimeTestResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return HardwareRuntimeTestResult(
                    "Camera",
                    ComponentStatus.FAILED,
                    "CameraManager service is missing",
                    "getSystemService(CAMERA_SERVICE) == null"
                )

            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) {
                return HardwareRuntimeTestResult(
                    "Camera",
                    ComponentStatus.UNAVAILABLE,
                    "No camera devices enumerated by CameraService",
                    "cameraManager.cameraIdList is empty"
                )
            }

            val details = mutableMapOf<String, String>()
            cameraIds.forEach { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                    else -> "UNKNOWN"
                }
                val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY (HAL1)"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                    else -> "UNKNOWN"
                }
                details["Camera ID $id"] = "Facing: $facing, HW Level: $hwLevel"
            }

            HardwareRuntimeTestResult(
                "Camera",
                ComponentStatus.WORKING,
                "Found ${cameraIds.size} camera sensors (${cameraIds.joinToString(", ")})",
                "CameraManager enumerated IDs: ${details.entries.joinToString("; ") { "${it.key}: ${it.value}" }}",
                details
            )
        } catch (e: Exception) {
            HardwareRuntimeTestResult(
                "Camera",
                ComponentStatus.FAILED,
                "Camera inquiry failed: ${e.message}",
                "Exception: ${e.stackTraceToString()}"
            )
        }
    }

    fun testFlashlight(context: Context, enable: Boolean): HardwareRuntimeTestResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return HardwareRuntimeTestResult("Flashlight", ComponentStatus.FAILED, "CameraManager unavailable", "No camera service")

            val backCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (backCameraId == null) {
                return HardwareRuntimeTestResult("Flashlight", ComponentStatus.UNAVAILABLE, "No camera with flash detected", "FLASH_INFO_AVAILABLE is false")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(backCameraId, enable)
                HardwareRuntimeTestResult(
                    "Flashlight",
                    ComponentStatus.WORKING,
                    "Torch mode set to ${if (enable) "ON" else "OFF"} on camera $backCameraId",
                    "CameraManager.setTorchMode($backCameraId, $enable) succeeded"
                )
            } else {
                HardwareRuntimeTestResult("Flashlight", ComponentStatus.UNKNOWN, "setTorchMode requires Android 6.0+", "SDK_INT < 23")
            }
        } catch (e: Exception) {
            HardwareRuntimeTestResult("Flashlight", ComponentStatus.FAILED, "Torch error: ${e.message}", "Exception: ${e.stackTraceToString()}")
        }
    }

    suspend fun testMicrophone(context: Context): HardwareRuntimeTestResult = withContext(Dispatchers.IO) {
        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return@withContext HardwareRuntimeTestResult(
                    "Microphone",
                    ComponentStatus.FAILED,
                    "AudioRecord buffer size query returned invalid parameter",
                    "getMinBufferSize error"
                )
            }

            val buffer = ShortArray(minBufferSize)
            var maxAmp = 0
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufferSize)

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext HardwareRuntimeTestResult(
                    "Microphone",
                    ComponentStatus.FAILED,
                    "AudioRecord failed to initialize (HAL or permission issue)",
                    "recorder.state != STATE_INITIALIZED"
                )
            }

            recorder.startRecording()
            val readCount = recorder.read(buffer, 0, minBufferSize)
            recorder.stop()
            recorder.release()

            if (readCount > 0) {
                for (i in 0 until readCount) {
                    val abs = Math.abs(buffer[i].toInt())
                    if (abs > maxAmp) maxAmp = abs
                }
                HardwareRuntimeTestResult(
                    "Microphone",
                    ComponentStatus.WORKING,
                    "Audio stream captured: read $readCount samples, peak amplitude=$maxAmp",
                    "PCM 16-bit 44.1kHz capture verified, peak amplitude: $maxAmp / 32767"
                )
            } else {
                HardwareRuntimeTestResult(
                    "Microphone",
                    ComponentStatus.FAILED,
                    "Read 0 audio samples from MIC",
                    "AudioRecord.read returned $readCount"
                )
            }
        } catch (e: SecurityException) {
            HardwareRuntimeTestResult(
                "Microphone",
                ComponentStatus.UNAVAILABLE,
                "Microphone permission not granted (RECORD_AUDIO)",
                "SecurityException: ${e.message}"
            )
        } catch (e: Exception) {
            HardwareRuntimeTestResult(
                "Microphone",
                ComponentStatus.FAILED,
                "Microphone capture error: ${e.message}",
                "Exception: ${e.stackTraceToString()}"
            )
        }
    }

    fun testSensorsPresence(context: Context): HardwareRuntimeTestResult {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return HardwareRuntimeTestResult("Sensors", ComponentStatus.FAILED, "SensorManager unavailable", "getSystemService returned null")

            val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            val sensorMap = mutableMapOf<String, String>()
            allSensors.forEach { sensor ->
                sensorMap[sensor.name] = "Type: ${sensor.stringType}, Vendor: ${sensor.vendor}, Power: ${sensor.power}mA"
            }

            val hasAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            val hasLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null
            val hasProx = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null

            HardwareRuntimeTestResult(
                "Sensors",
                if (allSensors.isNotEmpty()) ComponentStatus.WORKING else ComponentStatus.UNAVAILABLE,
                "Found ${allSensors.size} sensors (Accel: $hasAccel, Light: $hasLight, Proximity: $hasProx)",
                "Sensors list: ${sensorMap.entries.take(10).joinToString("; ") { "${it.key} (${it.value})" }}",
                sensorMap
            )
        } catch (e: Exception) {
            HardwareRuntimeTestResult("Sensors", ComponentStatus.FAILED, "Sensor test failed: ${e.message}", "Exception: ${e.stackTraceToString()}")
        }
    }
}
