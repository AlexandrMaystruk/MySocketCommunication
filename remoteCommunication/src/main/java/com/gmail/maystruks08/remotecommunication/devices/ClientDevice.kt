package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationinterface.entity.TransferData

interface ClientDevice {

    val deviceName: String
    val ipAddress: String

    suspend fun sendData(data: TransferData)

}
