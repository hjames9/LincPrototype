package io.padium.linc.prototype.ble

interface LincBleDeviceEvent {
    fun onEvent(device : String, value : Int)
}