package com.gmail.maystruks08.remotecommunication.devices

import android.annotation.SuppressLint
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData


@SuppressLint("MissingPermission")
class ClientDeviceImpl(
    override val ipAddress: String,
    override val port: Int,
    private val socketFactory: SocketFactory,
    private val logger: CommunicationLogger
) : ClientDevice {

    override suspend fun sendData(data: TransferData) {
        logger.log("try to send data to $ipAddress:$port")
        val config = SocketConfiguration(ipAddress, port, 1000, 5 * 1000)
        val socket = socketFactory.create(config)
        ClientImpl(socket, logger).also { clientImpl ->
            clientImpl.use {
                it.write(data)
            }
        }
    }

}
