package de.inovex.recognizefoodwithtflite

/**
 * Recognition item object with fields for the label and the probability.
 * Changed to a 'data class' to automatically generate utility functions like .copy().
 */
data class Recognition(
    val label: String,
    val confidence: Float
) {
    val probabilityString = String.format("%.1f%%", confidence * 100.0f)

    override fun toString(): String {
        return "$label / $probabilityString"
    }
}