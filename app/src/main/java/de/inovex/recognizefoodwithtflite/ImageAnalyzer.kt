package de.inovex.recognizefoodwithtflite

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import de.inovex.recognizefoodwithtflite.ml.LiteModelAiyVisionClassifierFoodV11
import de.inovex.recognizefoodwithtflite.utils.toBitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op


class ImageAnalyzer(
    private val ctx: Context,
    private val listener: RecognitionListener
) : ImageAnalysis.Analyzer {

    companion object {
        private var labelsPrinted = false
    }


    // Initialize the TFLite model
    private val model = LiteModelAiyVisionClassifierFoodV11.newInstance(ctx)

    // This function was from your original code and is safer for getting rotation.
    // I've restored it.
    private fun getRotationCompensation(): Int {
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation

        // The Rot90Op operator needs the rotation in multiples of 90 degrees.
        // Surface.ROTATION_0 = 0, ROTATION_90 = 1, ROTATION_180 = 2, ROTATION_270 = 3
        return rotation
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxy.image?.toBitmap()
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // Re-initialize the processor in each frame to get the latest rotation.
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(MainActivity.HEIGHT, MainActivity.WIDTH, ResizeOp.ResizeMethod.BILINEAR))
            // Use the safer rotation compensation function
            .add(Rot90Op(getRotationCompensation()))
            .build()

        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // Run inference
        val outputs = model.process(processedImage)
        val probability = outputs.probabilityAsCategoryList



        // ▼▼▼▼▼ all labels on logcat ▼▼▼▼▼
        if (probability.isNotEmpty()) {
            if (!labelsPrinted) {
                Log.d("ModelLabels", "--- All Supported Labels from Model ---")
                probability.sortedBy { it.label }.forEach { category ->
                    Log.d("ModelLabels", category.label)
                }
                labelsPrinted = true
            }
        }
        // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

        // Find the best result safely
        val bestResult = probability.maxByOrNull { it.score }
        bestResult?.let {
            listener(Recognition(it.label, it.score))
        }

        imageProxy.close()
    }
}