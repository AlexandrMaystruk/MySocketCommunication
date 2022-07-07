package com.gmail.maystruks08.remotecommunication.devices

interface DeviceFactory {

    fun createClient(deviceName: String, deviceIpAddress: String): ClientDevice

    fun createHost(): HostDevice

}

