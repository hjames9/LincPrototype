package io.padium.linc.prototype

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import io.padium.linc.prototype.ble.BleDeviceEvent
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

    private val bleDeviceEvent  = object : BleDeviceEvent {
        override fun onEvent(device: String, value: Int) {
            when(device) {
                LincScaleBleDevice::class.java.simpleName ->
                    Log.i(TAG, "Scale weight is ${value}g")
                LincThermometerBleDevice::class.java.simpleName ->
                    Log.i(TAG, "Thermometer temperature is ${value}C")
                else ->
                    Log.e(TAG, "Unknown device $device with value $value")
            }
        }
    }

    private val lincBleScale = LincScaleBleDevice(this, bleDeviceEvent)
    private val lincBleThermometer = LincThermometerBleDevice(this, bleDeviceEvent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val lincScaleBluetoothButton : Button = findViewById(R.id.lincScaleBluetoothButton)
        lincScaleBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE scale...")
            lincBleScale.open()
        }

        val lincThermometerBluetoothButton : Button = findViewById(R.id.lincThermometerBluetoothButton)
        lincThermometerBluetoothButton.setOnClickListener {
            Log.i(TAG, "Looking for Linc BLE thermometer...")
            lincBleThermometer.open()
        }
    }
}