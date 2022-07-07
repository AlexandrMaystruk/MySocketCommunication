package com.gmail.maystruks08.remotecommunication.devices

import android.annotation.SuppressLint
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationimplementation.ServerImpl.Companion.LOCAL_SERVER_PORT
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData


@SuppressLint("MissingPermission")
class ClientDeviceImpl(
    override val deviceName: String,
    override val ipAddress: String,
    private val socketFactory: SocketFactory,
    private val logger: CommunicationLogger
) : ClientDevice {

    override suspend fun sendData(data: TransferData) {
        logger.log("start sendData: $data to $deviceName : $ipAddress")
        val config = SocketConfiguration(ipAddress, LOCAL_SERVER_PORT, 1000, 5 * 1000)
        val socket = socketFactory.create(config)
        ClientImpl(socket, logger).also {
            it.write(data)
            it.close()
        }
    }

}
