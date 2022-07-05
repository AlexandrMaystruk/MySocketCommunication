package com.gmail.maystruks08.remotecommunication.devices

interface DeviceFactory {

    fun createClient(): ClientDevice

    fun createHost(): HostDevice

}

