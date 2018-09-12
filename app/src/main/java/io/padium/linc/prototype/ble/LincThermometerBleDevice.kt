package io.padium.linc.prototype.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import java.util.UUID

class LincThermometerBleDevice(context: Context) : BleDevice(context,"iBBQ") {
    companion object {
        private const val BLE_SERVICE_ID = "0000fff0-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF1 = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF2 = "0000fff2-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF3 = "0000fff3-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF4 = "0000fff4-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF5 = "0000fff5-0000-1000-8000-00805f9b34fb"
    }

    private lateinit var gattService : BluetoothGattService
    private lateinit var ff1 : BluetoothGattCharacteristic
    private lateinit var ff2 : BluetoothGattCharacteristic
    private lateinit var ff3 : BluetoothGattCharacteristic
    private lateinit var ff4 : BluetoothGattCharacteristic
    private lateinit var ff5 : BluetoothGattCharacteristic

    private var descriptorWriteCount = 0

    private val thermometerBluetoothGattCb = object : BluetoothGattCallback() {
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
                        setupThermometer(gatt)
                    } else {
                        Log.e(javaClass.simpleName, "Unable to setup thermometer as not bluetooth gatt handle unavailable")
                    }
                }
            }
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(javaClass.simpleName, "Bluetooth LE descriptor write: ${descriptor?.uuid.toString()}")
                    if(null != gatt) {
                        when(++descriptorWriteCount) {
                            1 -> {
                                autoThermometerPair(gatt) //TODO: might be able to skip hand pairing
                                //handThermometerPair(gatt)
                            }
                            2 -> {
                                Log.i(javaClass.simpleName, "Descriptor write called again, likely trying to get temps...")
                                readThermometerVersions(gatt)
                            }
                            else -> {
                                Log.e(javaClass.simpleName, "Descriptor write called again, wasn't expecting this.")
                            }
                        }
                    }
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

            if(null != characteristic) {
                var ss = characteristic.uuid.toString() + ":"
                for (s in characteristic.value) {
                    ss = StringBuilder(ss).append(Integer.toHexString(s.toInt())).append(", ").toString()
                }

                Log.i(javaClass.simpleName, ss)
            }

            val buffer = characteristic?.value
            if(null != buffer) {
                Log.i(javaClass.simpleName, String.format("Received buffer sized %d", buffer.size))
            } else {
                Log.e(javaClass.simpleName, "Empty byte array received")
                return
            }

            if(null == characteristic || null == gatt) {
                Log.e(javaClass.simpleName, "Unexpected null of characteristic or gatt")
                return
            }

            if(characteristic.uuid == ff1.uuid) {
                //Start reading temperatures...
                when(buffer[0]) {
                    0x21.toByte() -> {
                        //Initialize
                        //asyncExecution(readThermometerVersions, gatt)
                        //readThermometerVersions(gatt)
                        //getTemperatureData(gatt)
                        //readVoltage(gatt)
                        //setUnit(gatt)
                        //coolOff(gatt)
                        setCharacteristicNotification(gatt, ff4, true, DescriptorType.WRITE)
                        //Relative signal strength indicator
                        //gatt.readRemoteRssi()
                        //Dirty Diana
                        //readVoltage(gatt)
                    }
                    0x23.toByte() -> {
                        Log.i(javaClass.simpleName, "main versions: " + buffer[1])
                        Log.i(javaClass.simpleName, "second versions: " + buffer[2])
                        Log.i(javaClass.simpleName, "firmware type: " + buffer[3])
                    }
                    0x20.toByte() -> {
                        autoThermometerPair(gatt)
                    }
                    (-1).toByte() -> {
                        Log.e(javaClass.simpleName, "Bad service...")
                    }
                }
            } else if(characteristic.uuid == ff4.uuid) {
                processTemperatures(characteristic.value)
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
        Log.i(javaClass.simpleName, "Creating Linc thermometer BLE device")
        bluetoothGattCb = thermometerBluetoothGattCb
        bluetoothAdapter.startLeScan(leScan)
    }

    private fun setupThermometer(gatt : BluetoothGatt) {
        gattService = gatt.getService(UUID.fromString(BLE_SERVICE_ID))
        ff1 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF1))
        ff2 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF2))
        ff3 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF3))
        ff4 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF4))
        ff5 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF5))

        setCharacteristicNotification(gatt, ff1, true, DescriptorType.WRITE)
        Log.i(javaClass.simpleName, "Set ff1 characteristic notification done")
    }

    private fun processTemperatures(temperatures : ByteArray) {
        val getShort = { b: ByteArray, index: Int -> ((b[index + 1].toInt() shl 8) or (b[index + 0].toInt() and MotionEvent.ACTION_MASK)) as Short; }
        for(it in 0..temperatures.size step 2) {
            val temperature = (getShort(temperatures, it) as Double / 10.0 + 0.5).toInt().toShort()
            if (temperature != 0.toShort()) {
                Log.i(javaClass.simpleName, "Temperature $temperature")
            }
        }
    }

    private fun autoThermometerPair(gatt : BluetoothGatt) {
        Log.i(javaClass.simpleName, "Auto pairing this thermometer")
        val yz = byteArrayOf(33.toByte(), 7.toByte(), 6.toByte(), 5.toByte(), 4.toByte(), 3.toByte(), 2.toByte(), 1.toByte(), (-72).toByte(), 34.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff2.value = yz
        gatt.writeCharacteristic(ff2)
    }

    private fun handThermometerPair(gatt : BluetoothGatt) {
        Log.i(javaClass.simpleName, "Hand pairing this thermometer")
        val yz = byteArrayOf(32.toByte(), 7.toByte(), 6.toByte(), 5.toByte(), 4.toByte(), 3.toByte(), 2.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff2.value = yz
        gatt.writeCharacteristic(ff2)
    }

    private fun readThermometerVersions(gatt : BluetoothGatt) {
        Log.i(javaClass.simpleName, "Reading thermometer versions")
        val by = byteArrayOf(8.toByte(), 35.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff5.value = by
        gatt.writeCharacteristic(ff5)
    }

    private fun getTemperatureData(gatt : BluetoothGatt) {
        Log.i(javaClass.simpleName, "Reading initial thermometer data...")
        val op = byteArrayOf(11.toByte(), 1.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff5.value = op
        gatt.writeCharacteristic(ff5)
    }

    private fun readVoltage(gatt : BluetoothGatt) {
        Log.i(javaClass.simpleName, "Reading voltage")
        val by = byteArrayOf(8.toByte(), 36.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff5.value = by
        gatt.writeCharacteristic(ff5)
    }

    private fun setUnit(gatt : BluetoothGatt) {
        Log.i(javaClass.simpleName, "Set temperature unit")
        val celsius = false

        val bu = ByteArray(6)
        bu[0] = 2.toByte()

        if (celsius) {
            bu[1] = 1.toByte()
        } else {
            bu[1] = 0.toByte()
        }

        bu[2] = 0.toByte()
        bu[3] = 0.toByte()
        bu[4] = 0.toByte()
        bu[5] = 0.toByte()

        ff5.value = bu
        gatt.writeCharacteristic(ff5)
    }
}