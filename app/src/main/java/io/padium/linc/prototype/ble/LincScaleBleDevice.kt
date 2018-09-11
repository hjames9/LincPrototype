package io.padium.linc.ble.prototype

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import java.util.UUID

class LincScaleBleDevice(context: Context) : BleDevice(context, "nutridisk") {
    companion object {
        private const val BLE_SERVICE_ID = "00001910-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF2 = "0000fff2-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF4 = "0000fff4-0000-1000-8000-00805f9b34fb"
    }

    private lateinit var gattService : BluetoothGattService
    private lateinit var ff2 : BluetoothGattCharacteristic
    private lateinit var ff4 : BluetoothGattCharacteristic

    private val scaleBluetoothGattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when(newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(javaClass.simpleName, "Connected to GATT server.")
                    Log.i(javaClass.simpleName, "Attempting to start service discovery: ${bluetoothGatt.discoverServices()}")
                    bluetoothAdapter.stopLeScan(leScan)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(javaClass.simpleName, "Disconnected from GATT server.")
                }
                else -> {
                    Log.e(javaClass.simpleName, "Unknown BLE connection new state: $newState")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(javaClass.simpleName, "Bluetooth LE services discovered")

                    if (null != gatt) {
                        setupScale(gatt)
                    } else {
                        Log.e(javaClass.simpleName, "Unable to setup scale as not bluetooth gatt handle unavailable")
                    }
                }
            }
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(javaClass.simpleName, "Bluetooth LE descriptor write: ${descriptor?.uuid.toString()}")
                }
                else -> {
                    Log.e(javaClass.simpleName, "Error with BLE descriptor write: ${descriptor?.uuid.toString()}")
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(javaClass.simpleName, "Bluetooth LE characteristic read: ${characteristic?.uuid.toString()}")
                }
                else -> {
                    Log.e(javaClass.simpleName, "Error with BLE characteristic read: ${characteristic?.uuid.toString()}")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(javaClass.simpleName, "Bluetooth LE characteristic write: ${characteristic?.uuid.toString()}")
                }
                else -> {
                    Log.e(javaClass.simpleName, "Error with BLE characteristic write: ${characteristic?.uuid.toString()}")
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            Log.i(javaClass.simpleName, "Bluetooth LE characteristic changed: ${characteristic?.uuid.toString()}")

            if (null != gatt) {
                val values = characteristic?.value
                if (null != values) {
                    var ss = "Read from scale: "
                    for (s in values) {
                        ss = StringBuilder(ss).append(Integer.toHexString(s.toInt())).append(", ").toString()
                    }
                    Log.i(javaClass.simpleName, ss)
                } else {
                    Log.i(javaClass.simpleName, "No value written from BT")
                }

                if (null != values) {
                    var intValue1 = Integer.valueOf(Integer.parseInt(Integer.toHexString(values[0].toInt() and 255).toString(), 16))
                    var intValue2 = Integer.valueOf(Integer.parseInt(Integer.toHexString(values[1].toInt() and 255).toString(), 16))

                    intValue2 += (intValue1 - (Math.floor(intValue1.toDouble() / 32.0) * 32.0).toInt()) * 256
                    if (intValue1 < 128) {
                        intValue2 *= -1
                    } else if (intValue2 > 5000 || intValue2 < 0) {
                        intValue2 = 5000
                    }

                    if (intValue2 == -1807) {
                        //Send some data....wtf
                        Log.i(javaClass.simpleName, "Sending some data to ${ff2.uuid}....")
                        val bArr = byteArrayOf((-96).toByte(), (-96).toByte(), (-96).toByte(), (-96).toByte())
                        ff2.value = bArr
                        gatt.writeCharacteristic(ff2)
                    } else {
                        Log.i(javaClass.simpleName, "Weight in grams is $intValue2")
                    }
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            Log.i(javaClass.simpleName, "Bluetooth LE remote rssi read: $rssi")
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(javaClass.simpleName, "Bluetooth LE remote rssi read success")
                }
                else -> {
                    Log.e(javaClass.simpleName, "Error with BLE remote rssi read")
                }
            }
        }
    }

    init {
        Log.i(javaClass.simpleName, "Creating Linc scale BLE device")
        bluetoothGattCb = scaleBluetoothGattCb
        bluetoothAdapter.startLeScan(leScan)
    }

    private fun setupScale(gatt : BluetoothGatt) {
        gattService = gatt.getService(UUID.fromString(BLE_SERVICE_ID))
        ff2 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF2))
        ff4 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF4))

        setCharacteristicNotification(gatt, ff4, true, DescriptorType.BOTH)
        Log.i(javaClass.simpleName, "Set ff4 characteristic notification done")

        setCharacteristicNotification(gatt, ff2, true, DescriptorType.NONE)
        Log.i(javaClass.simpleName, "Set ff2 characteristic notification done")
    }
}