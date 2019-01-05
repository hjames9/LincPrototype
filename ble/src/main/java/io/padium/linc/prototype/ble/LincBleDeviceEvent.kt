package io.padium.linc.prototype.ble

interface LincBleDeviceEvent {
    fun onStartedDiscovery(device: LincBleDevice, deviceName: String)
    fun onFoundDevice(device: LincBleDevice, deviceName: String)
    fun onMissedDevice(device: LincBleDevice, deviceName: String)
    fun onConnectedDevice(device: LincBleDevice, deviceName: String)
    fun onDeviceEvent(device: LincBleDevice, deviceName: String, value : Int)
    fun onDisconnectedDevice(device: LincBleDevice, deviceName: String)
}