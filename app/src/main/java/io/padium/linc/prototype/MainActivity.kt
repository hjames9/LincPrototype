package io.padium.linc.prototype

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import io.padium.audionlp.*
import io.padium.audionlp.android.SpeechDelegate
import io.padium.audionlp.android.SpeechToText
import io.padium.linc.prototype.ble.LincBleDevice
import io.padium.linc.prototype.ble.LincBleDeviceEvent
import io.padium.linc.prototype.ble.ScaleLincBleDevice
import io.padium.linc.prototype.ble.ThermometerLincBleDevice
import io.padium.utils.http.HttpMethod
import io.padium.utils.http.HttpUtils
import io.padium.utils.tcp.TcpCallback
import io.padium.utils.tcp.TcpConnection
import io.padium.utils.tcp.TcpUtils
import org.jetbrains.anko.doAsync
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Main activity for Linc bluetooth le test application.
 *
 */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val BLE_SCALE_PERMISSION = 1000
        private const val BLE_THERMOMETER_PERMISSION = 1001
        private const val MICROPHONE_PERMISSION = 1002

        private const val KEY =
                "-----BEGIN PRIVATE KEY-----\n" +
                "MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDD/DQsDO4AMBQVQXBgr\n" +
                "yj9mVMGwIB5yZFZ8UP8KoJY5RRg449nywkaEknKZCU39bC6hZANiAASjmEGpF7hg\n" +
                "t2VFG3H4/9Lg1Hzn5jysVJR1cVj5McxaAnzcZooSCejwFdWd1Di50sNlIk97NvWF\n" +
                "wsIERRB0zWvbGQ/fMa0T3z7qEND4vblsRwuj5fTTJzola5pXMvqSO78=\n" +
                "-----END PRIVATE KEY-----"

        private const val CERT =
                "-----BEGIN CERTIFICATE-----\n" +
                "MIICnjCCAiSgAwIBAgIJAJ5PgZylmuUkMAoGCCqGSM49BAMCMIGlMQswCQYDVQQG\n" +
                "EwJVUzETMBEGA1UECAwKTmV3IEplcnNleTEQMA4GA1UEBwwHSG9ib2tlbjEYMBYG\n" +
                "A1UECgwPUGFkaXVtIExhYnMgTExDMRkwFwYDVQQLDBBMaW5jIEVuZ2luZWVyaW5n\n" +
                "MRkwFwYDVQQDDBBoYXlkZW4ucGFkaXVtLmlvMR8wHQYJKoZIhvcNAQkBFhBoYXlk\n" +
                "ZW5AcGFkaXVtLmlvMB4XDTE5MDUwMjExMTQ1NVoXDTIwMDUwMTExMTQ1NVowgaUx\n" +
                "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApOZXcgSmVyc2V5MRAwDgYDVQQHDAdIb2Jv\n" +
                "a2VuMRgwFgYDVQQKDA9QYWRpdW0gTGFicyBMTEMxGTAXBgNVBAsMEExpbmMgRW5n\n" +
                "aW5lZXJpbmcxGTAXBgNVBAMMEGhheWRlbi5wYWRpdW0uaW8xHzAdBgkqhkiG9w0B\n" +
                "CQEWEGhheWRlbkBwYWRpdW0uaW8wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAASjmEGp\n" +
                "F7hgt2VFG3H4/9Lg1Hzn5jysVJR1cVj5McxaAnzcZooSCejwFdWd1Di50sNlIk97\n" +
                "NvWFwsIERRB0zWvbGQ/fMa0T3z7qEND4vblsRwuj5fTTJzola5pXMvqSO7+jHjAc\n" +
                "MBoGA1UdEQQTMBGCCWxvY2FsaG9zdIcEwKgBpDAKBggqhkjOPQQDAgNoADBlAjEA\n" +
                "7Bk1BTVa/Q4OVikWKH0xskog+6J2gbER1591/b4hSNbH+ZnhVpAhaZk0goaJDaLM\n" +
                "AjAZYnVAt6OIlJs4lvQ3K0iqF9keJJEdd4j9ktMEbF49G11VxkDtTNHLFhXqdHCd\n" +
                "XOs=\n" +
                "-----END CERTIFICATE-----"

        private const val CA =
                "-----BEGIN CERTIFICATE-----\n" +
                "MIICnTCCAiSgAwIBAgIJAMVJvuCa14m4MAoGCCqGSM49BAMCMIGlMQswCQYDVQQG\n" +
                "EwJVUzETMBEGA1UECAwKTmV3IEplcnNleTEQMA4GA1UEBwwHSG9ib2tlbjEYMBYG\n" +
                "A1UECgwPUGFkaXVtIExhYnMgTExDMRkwFwYDVQQLDBBMaW5jIEVuZ2luZWVyaW5n\n" +
                "MRkwFwYDVQQDDBBoYXlkZW4ucGFkaXVtLmlvMR8wHQYJKoZIhvcNAQkBFhBoYXlk\n" +
                "ZW5AcGFkaXVtLmlvMB4XDTE5MDUwMjExMTYwNVoXDTIwMDUwMTExMTYwNVowgaUx\n" +
                "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApOZXcgSmVyc2V5MRAwDgYDVQQHDAdIb2Jv\n" +
                "a2VuMRgwFgYDVQQKDA9QYWRpdW0gTGFicyBMTEMxGTAXBgNVBAsMEExpbmMgRW5n\n" +
                "aW5lZXJpbmcxGTAXBgNVBAMMEGhheWRlbi5wYWRpdW0uaW8xHzAdBgkqhkiG9w0B\n" +
                "CQEWEGhheWRlbkBwYWRpdW0uaW8wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARJTcAM\n" +
                "/YtzLNnkqsKud8OmqnuuT4qcs3tVVjW8MyJ3vRni2YRSZy9llkSQfe76sz4xLiWw\n" +
                "2Dz9X9HZPZGoo/XOfWlj2Tke+i1stNl6R8ZqpyxSIbb06opFt3nqtkT0VB6jHjAc\n" +
                "MBoGA1UdEQQTMBGCCWxvY2FsaG9zdIcEwKgB6jAKBggqhkjOPQQDAgNnADBkAjB4\n" +
                "p0Wmhw261uOQIbzcz6q6k5x8CekMGVSxl0pD2nCe+dwblHpG4vL4xLuwOmPtoIcC\n" +
                "MA16LeytiVqUKrWxqDgZ6ButPWBIsrgGJNisaKBPpC/0EGF1R6f9XDdfydrNV2rC\n" +
                "ZA==\n" +
                "-----END CERTIFICATE-----\n"
    }

    private val bleDeviceEvent  = object : LincBleDeviceEvent {
        override fun onStartedDiscovery(device: LincBleDevice, deviceName: String) {
            Log.i(TAG, "Started discovery on $deviceName")
        }
        override fun onFoundDevice(device: LincBleDevice, deviceName: String) {
            Log.i(TAG, "Found $deviceName")
        }
        override fun onMissedDevice(device: LincBleDevice, deviceName: String) {
            Log.i(TAG, "Didn't find $deviceName")
        }
        override fun onConnectedDevice(device: LincBleDevice, deviceName: String) {
            Log.i(TAG, "Connected to $deviceName")
        }
        override fun onDisconnectedDevice(device: LincBleDevice, deviceName: String) {
            Log.i(TAG, "Disconnected from $deviceName")
        }
        override fun onDeviceEvent(device: LincBleDevice, deviceName: String, value: Int) {
            when(deviceName) {
                ScaleLincBleDevice::class.java.simpleName ->
                    Log.i(TAG, "Scale weight is ${value}g")
                ThermometerLincBleDevice::class.java.simpleName ->
                    Log.i(TAG, "Thermometer temperature is ${value}C")
                else ->
                    Log.e(TAG, "Unknown device $deviceName with value $value")
            }
        }
    }

    private val speechToText = SpeechToText(this, object : SpeechDelegate {
        override fun onStartup() {
        }
        override fun onShutdown() {
        }
        override fun onSpeechPartialResults(results: List<String>) {
        }
        override fun onSpeechResult(result: String) {
            if(!TextUtils.isEmpty(result)) {
                Toast.makeText(this@MainActivity, result, Toast.LENGTH_SHORT).show()
            }
        }
        override fun onSpeechRmsChanged(value: Float) {
        }
        override fun onStartOfSpeech() {
        }
    })

    private lateinit var lincBleScale : LincBleDevice
    private lateinit var lincBleThermometer : LincBleDevice
    private lateinit var audioToText : AudioToText
    private lateinit var textToSpeech : TextToSpeech

    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            //stopService(Intent(this@MainActivity, SpeechToTextService::class.java))
            speechToText.shutdown()
        }

        override fun onDone(utteranceId: String?) {
            //startService(Intent(this@MainActivity, SpeechToTextService::class.java))
            speechToText.startup()
        }

        override fun onError(utteranceId: String?) {
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "Text to speech error on $utteranceId with code $errorCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lincBleScale = ScaleLincBleDevice(this, bleDeviceEvent)
        lincBleThermometer = ThermometerLincBleDevice(this, bleDeviceEvent)

        val lincScaleBluetoothButton : Button = findViewById(R.id.lincScaleBluetoothButton)
        lincScaleBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE scale...")
            if(isThingsDevice()) {
                lincBleScale.close()
                lincBleScale.open()
            } else {
                Toast.makeText(this, "This app needs to access Bluetooth", Toast.LENGTH_SHORT).show()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), BLE_SCALE_PERMISSION)
            }
        }
        lincScaleBluetoothButton.isEnabled = false

        val lincThermometerBluetoothButton : Button = findViewById(R.id.lincThermometerBluetoothButton)
        lincThermometerBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE thermometer...")
            if(isThingsDevice()) {
                lincBleThermometer.close()
                lincBleThermometer.open()
            } else {
                Toast.makeText(this, "This app needs to access Bluetooth", Toast.LENGTH_SHORT).show()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), BLE_THERMOMETER_PERMISSION)
            }
        }
        lincThermometerBluetoothButton.isEnabled = false

        val audioNlpButton: Button = findViewById(R.id.audioNlpButton)
        audioNlpButton.setOnClickListener {
            Log.i(TAG, "Audio NLP starting...")
            if (isThingsDevice()) {
                doAudioNlpService()
            } else {
                Toast.makeText(this, "This app needs to record audio through the microphone", Toast.LENGTH_SHORT).show()
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION)
            }
        }

        val tcpButton : Button = findViewById(R.id.tcpButton)
        tcpButton.setOnClickListener {
            doTcpTest(false)
            doTcpTest(true)
        }

        val httpButton : Button = findViewById(R.id.httpButton)
        httpButton.setOnClickListener {
            doHttpTest()
        }

        //Attempt to start scans at startup
        lincBleThermometer.open()
        lincBleScale.open()

        val playMusicButton : Button = findViewById(R.id.playMusicButton)
        playMusicButton.setOnClickListener {
            try {
                val dsFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "LincPrototype"))
                val uri = Uri.parse("https://ia802508.us.archive.org/5/items/testmp3testfile/mpthreetest.mp3")
                val videoSource = ExtractorMediaSource.Factory(dsFactory).createMediaSource(uri)

                val exoPlayer = ExoPlayerFactory.newSimpleInstance(this)

                val audioAttributesBuilder = AudioAttributes.Builder()
                audioAttributesBuilder.setUsage(C.USAGE_ALARM)
                exoPlayer.audioAttributes = audioAttributesBuilder.build()

                val playbackStateString = { playbackState: Int ->
                    when(playbackState) {
                        Player.STATE_IDLE -> "idle"
                        Player.STATE_BUFFERING -> "buffering"
                        Player.STATE_READY -> "ready"
                        Player.STATE_ENDED -> "ended"
                        else -> "unknown"
                    }
                }

                exoPlayer.addListener(object : Player.EventListener {
                    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        Log.i(TAG, "Media should be in ready: $playWhenReady in state ${playbackStateString(playbackState)}")
                        when(playbackState) {
                            Player.STATE_READY -> speechToText.shutdown()
                            else -> speechToText.startup()
                        }
                    }
                })
                exoPlayer.prepare(videoSource)
                exoPlayer.playWhenReady = true
            } catch(exc: Exception) {
                Log.e(TAG, exc.message, exc)
            }
        }

        val ttsButton : Button = findViewById(R.id.ttsButton)
        ttsButton.setOnClickListener {
            if(!::textToSpeech.isInitialized) {
                textToSpeech = TextToSpeech(this, this)
            } else {
                textToSpeech.speak("Say it loud.  I am black and I'm proud", TextToSpeech.QUEUE_ADD, null, "31337")
            }
        }
    }

    override fun onInit(status: Int) {
        when(status) {
            TextToSpeech.SUCCESS -> {
                Log.i(TAG, "Successfully configured text to speech")
                val audioAttributesBuilder = android.media.AudioAttributes.Builder()
                audioAttributesBuilder.setLegacyStreamType(AudioManager.STREAM_ALARM)
                textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener)
                textToSpeech.setAudioAttributes(audioAttributesBuilder.build())
                textToSpeech.speak("Say it loud.  I am black and I'm proud", TextToSpeech.QUEUE_ADD, null, "31337")
            }
            else -> {
                Log.e(TAG, "Unsuccessfully configured text to speech: $status")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(null != grantResults && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                BLE_SCALE_PERMISSION -> {
                    lincBleScale.close()
                    lincBleScale.open()
                }
                BLE_THERMOMETER_PERMISSION -> {
                    lincBleThermometer.close()
                    lincBleThermometer.open()
                }
                MICROPHONE_PERMISSION -> {
                    doAudioNlpService()
                    //doAudioNlp()
                }
            }
        }
    }

    private fun createAudioNlp() {
        try {
            audioToText = AudioToText(this, object : AudioToTextListener {
                override fun onStart(processorLocation: AudioProcessorLocation) {
                    Log.i(TAG, "Started NLP processing on $processorLocation")
                }

                override fun onResult(processorLocation: AudioProcessorLocation, result: AudioTextResult) {
                    Log.i(TAG, "Finished text from NLP processing on $processorLocation has score[${result.score}], prob[${result.probability}] and is \"${result.phrase}\"")
                }

                override fun onPartialResult(processorLocation: AudioProcessorLocation, result: AudioTextResult) {
                    Log.i(TAG, "Partial text from NLP processing on $processorLocation has score[${result.score}], prob[${result.probability}] and is \"${result.phrase}\"")
                }

                override fun onEnd(processorLocation: AudioProcessorLocation) {
                    Log.i(TAG, "Finished NLP processing on $processorLocation")
                }

                override fun onError(processorLocation: AudioProcessorLocation, exp: Exception) {
                    Log.e(TAG, "Error in NLP processing on $processorLocation with ${exp.message}", exp)
                }
            })
        } catch(e: AudioException) {
            Log.e(TAG, e.message, e)
        }
    }

    private fun isThingsDevice(): Boolean {
        val pm = applicationContext.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
    }

    private fun doAudioNlp() {
        createAudioNlp()
        doAsync {
            try {
                audioToText.getWavFileText(File("${this@MainActivity.filesDir.absoluteFile}/sample_8mhz.wav"))
            } catch(e: Exception) {
                Log.e(TAG, e.message, e)
            }
        }

        //No asynchronous as the Google API complains..
        audioToText.startMicrophoneTextTimed(5, TimeUnit.SECONDS)
    }

    private fun doAudioNlpService() {
        //startService(Intent(this, SpeechToTextService::class.java))
        speechToText.startup()
    }

    private fun doTcpTest(tls : Boolean = false) {
        val callback = object : TcpCallback {
            private var counter = 0
            private var readAndWrite = 0
            override fun onError(connection: TcpConnection, th: Throwable) {
                Log.e(TAG, th.message, th)
                connection.close()
            }

            override fun onConnect(connection: TcpConnection, success: Boolean) {
                if(success) {
                    Log.i(TAG, "Successfully created ${if(tls) "encrypted" else "clear"} connection")
                    connection.write("hayden was here".toByteArray())
                    counter++
                } else {
                    Log.e(TAG, "Connection could not be created")
                }
            }

            override fun onClose(connection: TcpConnection) {
                Log.i(TAG, "Successfully closed connection")
            }

            override fun onWrite(connection: TcpConnection) {
                Log.i(TAG, "Iteration $counter. Written bytes")
                if(++readAndWrite == 2) {
                    onReadAndWrite(connection)
                }
            }

            override fun onRead(connection: TcpConnection, byteArray: ByteArray) {
                Log.i(TAG, "Iteration $counter. Received ${byteArray.size} bytes")
                if(++readAndWrite == 2) {
                    onReadAndWrite(connection)
                }
            }

            fun onReadAndWrite(connection: TcpConnection) {
                if(counter++ < 2) {
                    readAndWrite = 0
                    connection.write("hayden was here".toByteArray())
                } else {
                    connection.close()
                }
            }
        }

        doAsync {
            if (tls) {
                TcpUtils.doTlsConnection(this@MainActivity.filesDir.absoluteFile,
                        "192.168.1.234", 7000, callback)
            } else {
                TcpUtils.doTcpConnection("192.168.1.234", 7, callback)
            }
        }
    }

    private fun doHttpTest() {
        try {
            val result = HttpUtils.requestText("https://192.168.1.234:31337/devices", HttpMethod.GET,
                    ca = CA, cert = CERT, key = KEY)
            val response = result.get(10, TimeUnit.SECONDS)
            Toast.makeText(this, response.second, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Response code: ${response.first} data: ${response.second}")
        } catch(th: Throwable) {
            Toast.makeText(this, th.message, Toast.LENGTH_LONG).show()
        }
    }
}
