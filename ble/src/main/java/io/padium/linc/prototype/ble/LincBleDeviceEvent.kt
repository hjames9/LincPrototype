package io.padium.linc.prototype.ble

interface LincBleDeviceEvent {
    fun onStartedDiscovery(device : String)
    fun onFoundDevice(device : String)
    fun onMissedDevice(device : String)
    fun onConnectedDevice(device : String)
    fun onDeviceEvent(device : String, value : Int)
    fun onDisconnectedDevice(device : String)
}