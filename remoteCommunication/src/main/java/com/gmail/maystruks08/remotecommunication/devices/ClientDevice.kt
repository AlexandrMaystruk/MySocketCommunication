package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationinterface.entity.TransferData

interface ClientDevice {

    suspend fun sendData(ipAddress: String, data: TransferData)

}
