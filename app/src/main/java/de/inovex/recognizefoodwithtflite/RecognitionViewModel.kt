package de.inovex.recognizefoodwithtflite

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel, where the recognition results will be stored and updated with each new image
 * analyzed by the Tensorflow Lite Model.
 */
class RecognitionViewModel : ViewModel() {

    val recognition = MutableLiveData<Recognition>()

    fun updateData(recognitionResult: Recognition) {
        recognition.postValue(recognitionResult)
    }
}