package io.padium.linc.prototype.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import java.io.Closeable
import java.util.UUID

open class BleDevice(context: Context, event: BleDeviceEvent, deviceName : String) : Closeable {
    companion object {
        private const val BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID = "00002902-0000-1000-8000-00805f9b34fb"
        private val TAG = BleDevice::class.java.simpleName
    }

    protected val bluetoothAdapter : BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    protected val leScan : BluetoothAdapter.LeScanCallback
    protected lateinit var bluetoothGatt : BluetoothGatt
    protected lateinit var bluetoothGattCb : BluetoothGattCallback

    var ready = false

    enum class DescriptorType {
        READ, WRITE, BOTH, NONE
    }

    init {
        leScan = BluetoothAdapter.LeScanCallback { device, _, _ ->
            if(null != device) {
                Log.d(TAG,"Found device ${device.name} with address ${device.address}")

                if(device.name == deviceName)  {
                    Log.i(TAG, "Found device ${device.name} with address ${device.address}")
                    bluetoothGatt = device.connectGatt(context,false, bluetoothGattCb)
                    ready = true
                }
            } else {
                Log.e(TAG, "No device found...")
            }
        }
    }

    fun open() {
        bluetoothAdapter.startLeScan(leScan)
    }

    override fun close() {
        bluetoothAdapter.stopLeScan(leScan)
    }

    protected fun setCharacteristicNotification(gatt : BluetoothGatt, characteristic: BluetoothGattCharacteristic, enable : Boolean, descriptorType : DescriptorType) {
        Log.i(TAG, "Set $enable on characteristic ${characteristic.uuid} on descriptor $BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID")

        gatt.setCharacteristicNotification(characteristic, enable)

        val setupDescriptor : () -> BluetoothGattDescriptor = {
            val descriptor = characteristic.getDescriptor(UUID.fromString(BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID))

            if (null == descriptor) {
                Log.i(TAG,"Descriptor $BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID doesn't exist on ${characteristic.uuid}")
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