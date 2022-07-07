package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationimplementation.SocketFactoryImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class DeviceFactoryImpl(
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val logger: CommunicationLogger
) : DeviceFactory {

    private val socketFactory: SocketFactory by lazy {
        SocketFactoryImpl(logger)
    }

    override fun createClient(deviceName: String, deviceIpAddress: String): ClientDevice {
        return ClientDeviceImpl(
            deviceName = deviceName,
            ipAddress = deviceIpAddress,
            socketFactory = socketFactory,
            logger = logger
        )
    }

    override fun createHost(): HostDevice {
        return HostDeviceImpl(coroutineScope, dispatcher, logger, socketFactory)
    }

}
