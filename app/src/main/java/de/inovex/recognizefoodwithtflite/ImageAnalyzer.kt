package de.inovex.recognizefoodwithtflite

import android.annotation.SuppressLint
import android.content.Context
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import de.inovex.recognizefoodwithtflite.MainActivity.Companion.HEIGHT
import de.inovex.recognizefoodwithtflite.MainActivity.Companion.WIDTH
import de.inovex.recognizefoodwithtflite.ml.LiteModelAiyVisionClassifierFoodV11
import de.inovex.recognizefoodwithtflite.utils.toBitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op

class ImageAnalyzer(
    private val ctx: Context,
    private val listener: RecognitionListener
) : ImageAnalysis.Analyzer {

    // Tensorflow Lite Model Instance
    private val model = LiteModelAiyVisionClassifierFoodV11.newInstance(ctx)

    /**
     * Calculate what rotation of an image is necessary before passing it to the model so as to
     * compensate for the device rotation.
     */
    private fun calculateNecessaryRotation(): Int {
        return when ((ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_270 -> 2
            Surface.ROTATION_180 -> 4
            Surface.ROTATION_0 -> 3
            else -> 3
        }
    }

    /**
     * Analyze images from the camera stream using a Tensorflow Lite Model which performs
     * image classification of food in images.
     * Takes an ImageProxy as argument and returns the recognition results to a listener.
     */
    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val items: MutableList<Recognition> = mutableListOf()

        // IMAGE PREPROCESSING
        val imageProcessor = ImageProcessor.Builder()
            // Center crop the image
            .add(ResizeWithCropOrPadOp(HEIGHT, WIDTH))
            // Rotate
            .add(Rot90Op(calculateNecessaryRotation()))
            .build()
        var tImage = TensorImage(DataType.FLOAT32)
        val bitmap = imageProxy.image!!.toBitmap()
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)

        // INFERENCE
        // Process the model
        val outputs = model.process(tImage)
        // Extract the recognition results
        val food = outputs.probabilityAsCategoryList
        for (f in food)
            items.add(Recognition(f.label, f.score))

        // Sort the results by their confidence and return the three with the highest
        listener(items.apply {
            sortByDescending { it.confidence }
        }.toList()[0])

        // Close the image. This tells CameraX to feed the next image to the analyzer
        imageProxy.close()
    }
}