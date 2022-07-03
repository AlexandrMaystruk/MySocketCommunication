package com.gmail.maystruks08.communicationinterface

import com.gmail.maystruks08.communicationinterface.entity.TransferData
import java.io.Closeable

interface Client : Closeable {
    fun read(): TransferData
    fun write(data: TransferData)
}
