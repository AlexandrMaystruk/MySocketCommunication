package com.gmail.maystruks08.remotecommunication

import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.devices.ClientDevice
import kotlinx.coroutines.flow.Flow

interface CommunicationManager {

    /**
     * Lifecycle fun, used for init component
     */
    fun onStart()

    suspend fun discoverPeers()

    /**
     * Data which we send to remote devices
     */
    suspend fun sendBroadcast(data: TransferData)

    /**
     * Data which we receive from remote devices
     */
    fun observeBroadcast(): Flow<TransferData>

    /**
     * Lifecycle fun, used to stop using component
     */
    fun onStop()

}
