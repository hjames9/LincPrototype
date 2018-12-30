package io.padium.linc.prototype

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import io.padium.audionlp.*
import io.padium.linc.prototype.ble.LincBleDevice
import io.padium.linc.prototype.ble.LincBleDeviceEvent
import io.padium.linc.prototype.ble.ScaleLincBleDevice
import io.padium.linc.prototype.ble.ThermometerLincBleDevice
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
class MainActivity : Activity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val BLE_SCALE_PERMISSION = 1000
        private const val BLE_THERMOMETER_PERMISSION = 1001
        private const val MICROPHONE_PERMISSION = 1002
    }

    private val bleDeviceEvent  = object : LincBleDeviceEvent {
        override fun onStartedDiscovery(device: String) {
            Log.i(TAG, "Started discovery on $device")
        }
        override fun onFoundDevice(device: String) {
            Log.i(TAG, "Found $device")
        }
        override fun onMissedDevice(device: String) {
            Log.i(TAG, "Didn't find $device")
        }
        override fun onConnectedDevice(device: String) {
            Log.i(TAG, "Connected to $device")
        }
        override fun onDisconnectedDevice(device: String) {
            Log.i(TAG, "Disconnected from $device")
        }
        override fun onDeviceEvent(device: String, value: Int) {
            when(device) {
                ScaleLincBleDevice::class.java.simpleName ->
                    Log.i(TAG, "Scale weight is ${value}g")
                ThermometerLincBleDevice::class.java.simpleName ->
                    Log.i(TAG, "Thermometer temperature is ${value}C")
                else ->
                    Log.e(TAG, "Unknown device $device with value $value")
            }
        }
    }

    private lateinit var lincBleScale : LincBleDevice
    private lateinit var lincBleThermometer : LincBleDevice
    private lateinit var audioToText : AudioToText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lincBleScale = ScaleLincBleDevice(this, bleDeviceEvent)
        lincBleThermometer = ThermometerLincBleDevice(this, bleDeviceEvent)

        val lincScaleBluetoothButton : Button = findViewById(R.id.lincScaleBluetoothButton)
        lincScaleBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE scale...")
            if(isThingsDevice()) {
                lincBleScale.open()
            } else {
                Toast.makeText(this, "This app needs to access Bluetooth", Toast.LENGTH_SHORT).show()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), BLE_SCALE_PERMISSION)
            }
        }

        val lincThermometerBluetoothButton : Button = findViewById(R.id.lincThermometerBluetoothButton)
        lincThermometerBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE thermometer...")
            if(isThingsDevice()) {
                lincBleThermometer.open()
            } else {
                Toast.makeText(this, "This app needs to access Bluetooth", Toast.LENGTH_SHORT).show()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), BLE_THERMOMETER_PERMISSION)
            }
        }

        val tcpButton : Button = findViewById(R.id.tcpButton)
        tcpButton.setOnClickListener {
            doTcpTest(false)
            doTcpTest(true)
        }

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

            val audioNlpButton: Button = findViewById(R.id.audioNlpButton)
            audioNlpButton.setOnClickListener {
                Log.i(TAG, "Audio NLP starting...")
                if (isThingsDevice()) {
                    doAudioNlp()
                } else {
                    Toast.makeText(this, "This app needs to record audio through the microphone", Toast.LENGTH_SHORT).show()
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION)
                }
            }
        } catch(e: AudioException) {
            Log.e(TAG, e.message, e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(null != grantResults && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                BLE_SCALE_PERMISSION -> lincBleScale.open()
                BLE_THERMOMETER_PERMISSION -> lincBleThermometer.open()
                MICROPHONE_PERMISSION -> doAudioNlp()
            }
        }
    }

    private fun isThingsDevice(): Boolean {
        val pm = applicationContext.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
    }

    private fun doAudioNlp() {
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
}