package com.gmail.maystruks08.remotecommunication

import com.gmail.maystruks08.communicationinterface.entity.TransferData
import kotlinx.coroutines.flow.Flow

interface CommunicationManager {

    /**
     * Lifecycle fun, used for init component
     */
    fun onStart()

    /**
     * Data which we receive from remote devices
     */
    fun getRemoteClientsTransferDataFlow(): Flow<TransferData>

    /**
     * Data which we send to remote devices
     */
    fun sendToRemoteClients(data: TransferData)

    /**
     * Lifecycle fun, used to stop using component
     */
    fun onStop()

}
