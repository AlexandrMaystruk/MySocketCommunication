package com.gmail.maystruks08.communicationinterface

import com.gmail.maystruks08.communicationinterface.entity.TransferData
import java.io.Closeable

interface Server : Closeable {
    fun readFromClients(onNewDataReceived: (TransferData) -> Unit)
}