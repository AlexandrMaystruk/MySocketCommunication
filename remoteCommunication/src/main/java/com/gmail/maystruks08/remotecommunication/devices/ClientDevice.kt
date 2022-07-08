package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationinterface.entity.TransferData

interface ClientDevice {

    val ipAddress: String
    val port: Int

    suspend fun sendData(data: TransferData)

}
