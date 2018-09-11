package io.padium.linc.ble.prototype

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import java.util.UUID

open class BleDevice(context: Context, deviceName : String) {
    companion object {
        private const val BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    protected var bluetoothAdapter : BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    protected var leScan : BluetoothAdapter.LeScanCallback
    protected lateinit var bluetoothGatt : BluetoothGatt
    protected lateinit var bluetoothGattCb : BluetoothGattCallback

    var ready = false

    enum class DescriptorType {
        READ, WRITE, BOTH, NONE
    }

    init {
        leScan = object : BluetoothAdapter.LeScanCallback {
            override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
                if(null != device) {
                    Log.i(javaClass.simpleName,
                            String.format("Found device %s with address %s",
                                    device.name, device.address))

                    if(device.name == deviceName)  {
                        Log.i(javaClass.simpleName,
                                String.format("Found device %s with address %s",
                                        device.name, device.address))
                        bluetoothGatt = device.connectGatt(context,false, bluetoothGattCb)
                        ready = true
                    }
                } else {
                    Log.e(javaClass.simpleName, "No device found...")
                }
            }
        }
    }

    protected fun setCharacteristicNotification(gatt : BluetoothGatt, characteristic: BluetoothGattCharacteristic, enable : Boolean, descriptorType : DescriptorType) {
        Log.i(javaClass.simpleName, String.format("Set %b on characteristic %s on descriptor %s", enable, characteristic.uuid.toString(),
                BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID))

        gatt.setCharacteristicNotification(characteristic, enable)

        val setupDescriptor : () -> BluetoothGattDescriptor = {
            val descriptor = characteristic.getDescriptor(UUID.fromString(BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID))

            if (null == descriptor) {
                Log.i(javaClass.simpleName,
                        "Descriptor $BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID doesn't exist on ${characteristic.uuid}")
            } else {
                if (enable) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
            }

            descriptor
        }

        when(descriptorType) {
            DescriptorType.READ -> gatt.readDescriptor(setupDescriptor())
            DescriptorType.WRITE -> gatt.writeDescriptor(setupDescriptor())
            DescriptorType.BOTH -> {
                gatt.writeDescriptor(setupDescriptor())
                gatt.readDescriptor(setupDescriptor())
            }
            DescriptorType.NONE -> {
            }
        }
    }
}