package io.padium.linc.prototype

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import io.padium.linc.prototype.ble.LincScaleBleDevice
import io.padium.linc.prototype.ble.LincThermometerBleDevice

/**
 * Main activity for Linc bluetooth le test application.
 *
 */
class MainActivity : Activity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    private lateinit var lincBleScale : LincScaleBleDevice
    private lateinit var lincBleThermometer : LincThermometerBleDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val lincScaleBluetoothButton : Button = findViewById(R.id.lincScaleBluetoothButton)
        lincScaleBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE scale...")
            lincBleScale = LincScaleBleDevice(this)
            lincScaleBluetoothButton.isEnabled = false
        }

        val lincThermometerBluetoothButton : Button = findViewById(R.id.lincThermometerBluetoothButton)
        lincThermometerBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE thermometer...")
            lincBleThermometer = LincThermometerBleDevice(this)
            lincThermometerBluetoothButton.isEnabled = false
        }
    }
}