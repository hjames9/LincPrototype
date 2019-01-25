package io.padium.audionlp.android

import android.speech.SpeechRecognizer
import java.lang.Exception

private fun getMessage(code: Int): String {
    // these have been mapped from here:
    // https://developer.android.com/reference/android/speech/SpeechRecognizer.html
    return when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "$code - Audio recording error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "$code - Insufficient permissions. Request android.permission.RECORD_AUDIO"
        SpeechRecognizer.ERROR_CLIENT ->
            // http://stackoverflow.com/questions/24995565/android-speechrecognizer-when-do-i-get-error-client-when-starting-the-voice-reco
            "$code - Client side error. Maybe your internet connection is poor!"
        SpeechRecognizer.ERROR_NETWORK -> "$code - Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "$code - Network operation timed out"
        SpeechRecognizer.ERROR_NO_MATCH -> "$code - No recognition result matched. Try turning on partial results as a workaround."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "$code - RecognitionService busy"
        SpeechRecognizer.ERROR_SERVER -> "$code - Server sends error status"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "$code - No speech input"
        else -> "$code - Unknown exception"
    }
}

class SpeechRecognitionException : Exception {
    private var code: Int = 0

    constructor(code: Int): super(getMessage(code)) {
        this.code = code
    }

    constructor(message: String): super(message)
}