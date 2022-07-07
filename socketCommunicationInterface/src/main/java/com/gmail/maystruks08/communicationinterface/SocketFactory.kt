package com.gmail.maystruks08.communicationinterface

import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import java.net.ServerSocket
import java.net.Socket

interface SocketFactory {
    val localeIpAddress: String
    fun create(config: SocketConfiguration): Socket
    fun createServerSocket(port: Int): ServerSocket
}