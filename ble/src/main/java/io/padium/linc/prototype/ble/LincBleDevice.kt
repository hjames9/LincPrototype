package io.padium.linc.prototype.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import java.io.Closeable
import java.util.UUID

abstract class LincBleDevice(context: Context, val event: LincBleDeviceEvent, val deviceName : String) : Closeable {
    companion object {
        private const val BLE_DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIG_ID = "00002902-0000-1000-8000-00805f9b34fb"
        private val TAG = LincBleDevice::class.java.simpleName
    }

    protected val bluetoothAdapter : BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    protected val leScan : ScanCallback
    protected val bluetoothScanner : BluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    protected lateinit var bluetoothGatt : BluetoothGatt
    protected lateinit var bluetoothGattCb : BluetoothGattCallback

    protected var ready = false
    private var starting = false

    enum class DescriptorType {
        READ, WRITE, BOTH, NONE
    }

    init {
        leScan = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device
                if(null != device) {
                    Log.d(TAG,"Found device ${device.name} with address ${device.address}")

                    if(device.name == deviceName)  {
                        Log.i(TAG, "Found device ${device.name} with address ${device.address}")
                        bluetoothGatt = device.connectGatt(context,false, bluetoothGattCb)
                        starting = false
                        event.onFoundDevice(TAG)
                    }
                } else {
                    Log.e(TAG, "No device found...")
                    event.onMissedDevice(TAG)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed, no device found...")
                event.onMissedDevice(TAG)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Log.i(TAG, "onBatchScanResults")
            }
        }
    }

    protected fun getScanFilter(): ScanFilter {
        val scanFilter = ScanFilter.Builder()
        return scanFilter.build()
    }

    protected fun getScanSettings(): ScanSettings {
        val scanSettings = ScanSettings.Builder()
        return scanSettings.build()
    }

    fun open() {
        if(!ready && !starting) {
            starting = true
            bluetoothScanner.startScan(listOf(getScanFilter()), getScanSettings(), leScan)
            event.onStartedDiscovery(TAG)
        }
    }

    override fun close() {
        bluetoothScanner.stopScan(leScan)
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