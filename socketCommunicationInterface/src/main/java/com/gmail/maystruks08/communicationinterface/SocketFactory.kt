package com.gmail.maystruks08.communicationinterface

import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import java.net.ServerSocket
import java.net.Socket

interface SocketFactory {
    fun create(config: SocketConfiguration): Socket
    fun createServerSocket(): ServerSocket
}