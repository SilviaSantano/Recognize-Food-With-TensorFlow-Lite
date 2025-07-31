package de.inovex.recognizefoodwithtflite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.inovex.recognizefoodwithtflite.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.Executors

typealias RecognitionListener = (recognition: Recognition) -> Unit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var preview: Preview
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var camera: Camera
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var binding: ActivityMainBinding

    private val recognitionListViewModel: RecognitionViewModel by viewModels()

    private lateinit var tts: TextToSpeech
    private var lastRecognizedLabel: String = ""
    private var lastRecognitionTime: Long = 0L
    private var hasSpoken: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_RecognizeFoodWithTFLite)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.lifecycleOwner = this
        binding.viewmodel = recognitionListViewModel

        // Initialize TextToSpeech engine.
        tts = TextToSpeech(this, this)

        // Request camera permissions if not already granted.
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    /**
     * Check if all required permissions are granted.
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Handle the result of the permission request.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, getString(R.string.permission_deny_text), Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    /**
     * Initialize and start the camera preview and image analysis use cases.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase: ImageAnalysis ->
                    analysisUseCase.setAnalyzer(
                        cameraExecutor, ImageAnalyzer(this) { recognition ->
                            // Switch to the main thread to handle the recognition result.
                            runOnUiThread {
                                handleRecognition(recognition)
                            }
                        }
                    )
                }

            val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind all use cases before rebinding.
                cameraProvider.unbindAll()
                // Bind the use cases to the camera.
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(getString(R.string.app_name), "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Initialize the TextToSpeech engine.
     * This is called when the TTS service is connected.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set the TTS language to Korean.
            val result = tts.setLanguage(Locale.KOREAN)

            // Set the speech rate. 1.0f is the default. Lower is slower.
            tts.setSpeechRate(0.8f)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Korean language is not supported!")
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    /**
     * Clean up resources when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Shutdown the TTS engine.
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        // Shutdown the camera executor.
        cameraExecutor.shutdown()
    }

    /**
     * Speak the given text using the TTS engine.
     */
    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    /**
     * Handle the recognition result from the ImageAnalyzer.
     * This function translates the label to Korean and updates the UI and TTS.
     */
    private fun handleRecognition(recognition: Recognition) {
        // Translate the English label to Korean using the function from foodlist.kt.
        val koreanName = getKoreanFoodName(recognition.label)
        // Create a new recognition object with the translated Korean label for the UI.
        val koreanRecognition = recognition.copy(label = koreanName)
        // Update the ViewModel with the recognition data containing the Korean name.
        recognitionListViewModel.updateData(koreanRecognition)

        // Compare with the original English label to decide if the recognized object has changed.
        if (lastRecognizedLabel != recognition.label) {
            lastRecognizedLabel = recognition.label // Store the original English label.
            lastRecognitionTime = System.currentTimeMillis()
            hasSpoken = false
            return
        }

        // Speak the Korean name only if the same object has been recognized for at least 2 seconds.
        if (!hasSpoken) {
            val currentTime = System.currentTimeMillis()
            val duration = currentTime - lastRecognitionTime

            if (duration >= 2000) {
                speak(koreanName) // Speak the translated Korean name.
                hasSpoken = true
            }
        }
    }

    companion object {
        const val WIDTH = 224
        const val HEIGHT = 224
        const val REQUEST_CODE_PERMISSIONS = 123
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}