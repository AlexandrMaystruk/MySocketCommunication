package com.gmail.maystruks08.remotecommunication.devices

interface DeviceFactory {

    fun createClient(deviceIpAddress: String, port: Int): ClientDevice

    fun createHost(): HostDevice

}

