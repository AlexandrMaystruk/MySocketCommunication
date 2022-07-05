package com.gmail.maystruks08.communicationinterface

import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import java.io.Closeable

interface Client : Closeable {

    @Throws(RemoteError.CommunicationError.ReadFromClientSocketError::class)
    fun read(): TransferData

    @Throws(RemoteError.CommunicationError.WriteToClientSocketError::class)
    fun write(data: TransferData)

}
