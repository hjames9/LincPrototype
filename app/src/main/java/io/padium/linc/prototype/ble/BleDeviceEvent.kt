package io.padium.linc.prototype.ble

interface BleDeviceEvent {
    fun onEvent(device : String, value : Int)
}