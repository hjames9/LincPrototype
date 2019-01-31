package io.padium.linc.prototype

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import io.padium.audionlp.android.SpeechDelegate
import io.padium.audionlp.android.SpeechRecognitionException
import io.padium.audionlp.android.SpeechToText
import java.util.*

class SpeechToTextService : Service(), SpeechDelegate {
    companion object {
        private val TAG = SpeechToTextService::class.java.simpleName
    }

    private val speechToText = SpeechToText(this, this)

    override fun onCreate() {
        Log.i(TAG, "Creating SpeechToText service")
        super.onCreate()
        speechToText.startup()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting SpeechToText service")

        try {
            speechToText.startListening()
        } catch(exc: SpeechRecognitionException) {
            Log.e(TAG, exc.message, exc)
            stopSelfResult(startId)
        }
        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        //TODO for communication return IBinder implementation
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        speechToText.shutdown()
    }

    override fun onStartup() {
    }

    override fun onShutdown() {
    }

    override fun onStartOfSpeech() {
    }

    override fun onSpeechRmsChanged(value: Float) {
    }

    override fun onSpeechPartialResults(results: List<String>) {
        for (partial in results) {
            Log.d("Result", partial + "")
        }
    }

    override fun onSpeechResult(result: String) {
        Log.d("Result", result + "")
        if (!TextUtils.isEmpty(result)) {
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        //Restarting the service if it is removed.
        val service = PendingIntent.getService(applicationContext, Random().nextInt(),
                Intent(applicationContext, SpeechToTextService::class.java), PendingIntent.FLAG_ONE_SHOT)

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, service)
        super.onTaskRemoved(rootIntent)
    }
}