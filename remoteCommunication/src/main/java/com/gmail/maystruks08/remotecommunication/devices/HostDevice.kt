package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationinterface.entity.TransferData
import kotlinx.coroutines.flow.Flow


interface HostDevice {

    fun listenRemoteData(): Flow<TransferData>

}
