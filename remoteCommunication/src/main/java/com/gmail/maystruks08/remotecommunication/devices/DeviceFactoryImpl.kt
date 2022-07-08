package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationimplementation.SocketFactoryImpl
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.remotecommunication.Configuration

class DeviceFactoryImpl(
    private val configuration: Configuration
) : DeviceFactory {

    private val socketFactory: SocketFactory by lazy {
        SocketFactoryImpl(configuration.app.logger)
    }

    override fun createClient(deviceIpAddress: String, port: Int): ClientDevice {
        return ClientDeviceImpl(
            ipAddress = deviceIpAddress,
            port = port,
            socketFactory = socketFactory,
            logger = configuration.app.logger
        )
    }

    override fun createHost(): HostDevice {
        return HostDeviceImpl(
            configuration = configuration,
            socketFactory = socketFactory
        )
    }

}
