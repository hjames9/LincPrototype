package io.padium.linc.prototype.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors

class LincThermometerBleDevice(context: Context, event: BleDeviceEvent) : BleDevice(context, event,"iBBQ") {
    companion object {
        private const val BLE_SERVICE_ID = "0000fff0-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF1 = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF2 = "0000fff2-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF3 = "0000fff3-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF4 = "0000fff4-0000-1000-8000-00805f9b34fb"
        private const val BLE_CHARACTERISTIC_FF5 = "0000fff5-0000-1000-8000-00805f9b34fb"
        private val TAG = LincThermometerBleDevice::class.java.simpleName
    }

    private lateinit var gattService : BluetoothGattService
    private lateinit var ff1 : BluetoothGattCharacteristic
    private lateinit var ff2 : BluetoothGattCharacteristic
    private lateinit var ff3 : BluetoothGattCharacteristic
    private lateinit var ff4 : BluetoothGattCharacteristic
    private lateinit var ff5 : BluetoothGattCharacteristic

    var celsius = false
    private var descriptorWriteCount = 0
    private val queue = ArrayBlockingQueue<Runnable>(10)
    private val threadPool = Executors.newSingleThreadScheduledExecutor()
    private val handler = InnerHandler()

    private class InnerHandler : Handler() {
        override fun handleMessage(message : Message) {
            Log.i(TAG, "Processing message ${message.what}")
        }
    }

    private val thermometerBluetoothGattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when(newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")
                    Log.i(TAG, "Attempting to start service discovery: ${bluetoothGatt.discoverServices()}")
                    bluetoothAdapter.stopLeScan(leScan)
                    ready = true
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                    ready = false
                }
                else -> {
                    Log.e(TAG, "Unknown BLE connection new state: $newState")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Bluetooth LE services discovered")

                    if (null != gatt) {
                        setupThermometer(gatt)
                    } else {
                        Log.e(TAG, "Unable to setup thermometer as not bluetooth gatt handle unavailable")
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Bluetooth LE descriptor write: ${descriptor?.uuid.toString()}")
                    if(null != gatt) {
                        when(++descriptorWriteCount) {
                            1 -> {
                                autoThermometerPair(gatt)
                            }
                            2 -> {
                                Log.i(TAG, "Descriptor write called again, getting versions...")
                                readThermometerVersions(gatt)
                            }
                            else -> {
                                Log.e(TAG, "Descriptor write called again, wasn't expecting this.")
                            }
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "Error with BLE descriptor write: ${descriptor?.uuid.toString()}")
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Bluetooth LE characteristic read: ${characteristic?.uuid.toString()}")
                }
                else -> {
                    Log.e(TAG, "Error with BLE characteristic read: ${characteristic?.uuid.toString()}")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Bluetooth LE characteristic write: ${characteristic?.uuid.toString()}")
                    threadPool.submit {
                        if(!queue.isEmpty()) {
                            val runnable = queue.poll()
                            runnable.run()
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "Error with BLE characteristic write: ${characteristic?.uuid.toString()}")
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            Log.i(TAG, "Bluetooth LE characteristic changed: ${characteristic?.uuid.toString()}")

            if(null != characteristic) {
                var ss = characteristic.uuid.toString() + ":"
                for (s in characteristic.value) {
                    ss = StringBuilder(ss).append(Integer.toHexString(s.toInt())).append(", ").toString()
                }

                Log.i(TAG, ss)
            } else {
                Log.e(TAG, "Unexpected null of characteristic")
                return
            }

            val buffer = characteristic?.value
            if(null != buffer) {
                Log.d(TAG, "Received buffer sized ${buffer.size}")
            } else {
                Log.e(TAG, "Empty byte array received")
                return
            }

            if(null == gatt) {
                Log.e(TAG, "Unexpected null of gatt")
                return
            }

            if(characteristic.uuid == ff1.uuid) {
                //Start reading temperatures...
                when(buffer[0]) {
                    0x21.toByte() -> {
                        //init queue
                        asyncExecution(Runnable{readThermometerVersions(gatt)})
                        asyncExecution(Runnable{getTemperatureData(gatt)})
                        asyncExecution(Runnable{readVoltage(gatt)})
                        asyncExecution(Runnable{setUnit(gatt)})
                        asyncExecution(Runnable{coolOff(gatt)})

                        //open data
                        handler.post {
                            setCharacteristicNotification(gatt, ff4, true, DescriptorType.WRITE)
                        }
                    }
                    0x23.toByte() -> {
                        Log.i(TAG, "main versions: ${buffer[1]}")
                        Log.i(TAG, "second versions: ${buffer[2]}")
                        Log.i(TAG, "firmware type: ${buffer[3]}")
                    }
                    0x20.toByte() -> {
                        asyncExecution(Runnable{autoThermometerPair(gatt)})
                    }
                    (-1).toByte() -> {
                        Log.e(TAG, "Bad service...")
                    }
                }
            } else if(characteristic.uuid == ff4.uuid) {
                val temperatures = processTemperatures(characteristic.value)
                for(temperature in temperatures) {
                    event.onEvent(TAG, temperature)
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            Log.i(TAG, "Bluetooth LE remote rssi read: $rssi")
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "Bluetooth LE remote rssi read success")
                }
                else -> {
                    Log.e(TAG, "Error with BLE remote rssi read")
                }
            }
        }
    }

    init {
        Log.i(TAG, "Creating Linc thermometer BLE device")
        bluetoothGattCb = thermometerBluetoothGattCb
    }

    private fun setupThermometer(gatt : BluetoothGatt) {
        gattService = gatt.getService(UUID.fromString(BLE_SERVICE_ID))
        ff1 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF1))
        ff2 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF2))
        ff3 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF3))
        ff4 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF4))
        ff5 = gattService.getCharacteristic(UUID.fromString(BLE_CHARACTERISTIC_FF5))

        setCharacteristicNotification(gatt, ff1, true, DescriptorType.WRITE)
    }

    private fun asyncExecution(run : Runnable) {
        queue.add(run)
    }

    private fun processTemperatures(temperatures : ByteArray) : List<Int> {
        val temps = mutableListOf<Int>()

        val getShort = { b: ByteArray, index: Int -> ((b[index + 1].toInt() shl 8) or (b[index + 0].toInt() and MotionEvent.ACTION_MASK)).toShort() }
        for(it in 0..temperatures.size step 2) {
            if(it > temperatures.size - 1)
                break
            val temperature = (getShort(temperatures, it).toDouble() / 10.0 + 0.5).toInt().toShort()
            if (temperature != 0.toShort()) {
                temps.add(temperature.toInt())
            }
        }

        return temps
    }

    private fun autoThermometerPair(gatt : BluetoothGatt) {
        Log.i(TAG, "Auto pairing this thermometer")
        val yz = byteArrayOf(33.toByte(), 7.toByte(), 6.toByte(), 5.toByte(), 4.toByte(), 3.toByte(), 2.toByte(), 1.toByte(), (-72).toByte(), 34.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff2.value = yz
        gatt.writeCharacteristic(ff2)
    }

    private fun handThermometerPair(gatt : BluetoothGatt) {
        Log.i(TAG, "Hand pairing this thermometer")
        val yz = byteArrayOf(32.toByte(), 7.toByte(), 6.toByte(), 5.toByte(), 4.toByte(), 3.toByte(), 2.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff2.value = yz
        gatt.writeCharacteristic(ff2)
    }

    private fun readThermometerVersions(gatt : BluetoothGatt) {
        Log.i(TAG, "Reading thermometer versions")
        val by = byteArrayOf(8.toByte(), 35.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff5.value = by
        gatt.writeCharacteristic(ff5)
    }

    private fun getTemperatureData(gatt : BluetoothGatt) {
        Log.i(TAG, "Reading initial thermometer data...")
        val op = byteArrayOf(11.toByte(), 1.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff5.value = op
        gatt.writeCharacteristic(ff5)
    }

    private fun readVoltage(gatt : BluetoothGatt) {
        Log.i(TAG, "Reading voltage")
        val by = byteArrayOf(8.toByte(), 36.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
        ff5.value = by
        gatt.writeCharacteristic(ff5)
    }

    private fun setUnit(gatt : BluetoothGatt) {
        Log.i(TAG, "Set temperature unit")

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

    private fun coolOff(gatt : BluetoothGatt) {
        Log.i(TAG, "Cooling off")
        val lqq = 60 * 10
        val bu = byteArrayOf(3.toByte(), (-1).toByte(), (lqq and MotionEvent.ACTION_MASK).toByte(), lqq.ushr(8).toByte(), 0.toByte(), 0.toByte())
        ff5.value = bu
        gatt.writeCharacteristic(ff5)
    }

    private fun readRemoteRssi(gatt : BluetoothGatt) {
        gatt.readRemoteRssi()
    }
}
