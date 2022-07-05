package com.gmail.maystruks08.remotecommunication.devices

import android.annotation.SuppressLint
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationinterface.Client
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.CommunicationManagerImpl.Companion.SERVER_PORT


@SuppressLint("MissingPermission")
class ClientDeviceImpl(
    private val socketFactory: SocketFactory,
    private val logger: CommunicationLogger,
) : ClientDevice {

    private var client: Client? = null

    override suspend fun sendData(ipAddress: String, data: TransferData) {
        logger.log("start sendData: $data")
        val config = SocketConfiguration(ipAddress, SERVER_PORT, 1000, 5 * 1000)
        val socket = socketFactory.create(config)
        ClientImpl(socket, logger).also {
            client = it
            it.write(data)
            it.close()
        }
    }

}
