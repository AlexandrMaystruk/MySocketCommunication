package com.gmail.maystruks08.remotecommunication

import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException


//fun getFreePort(): Int = ServerSocket(0).use {
//    return@use it.localPort
//}

fun getLocalIpAddress(logger: CommunicationLogger): String? {
    try {
        val enumeration = NetworkInterface.getNetworkInterfaces()
        while (enumeration.hasMoreElements()) {
            val networkInterface = enumeration.nextElement()
            val enumIpAddress = networkInterface.inetAddresses
            while (enumIpAddress.hasMoreElements()) {
                val internetAddress = enumIpAddress.nextElement()
                if (!internetAddress.isLoopbackAddress && internetAddress is Inet4Address) {
                    return internetAddress.hostAddress
                }
            }
        }
    } catch (e: SocketException) {
        logger.log("Communication -> getLocalIpAddress error: ${e.localizedMessage}")
    }
    logger.log("Communication -> getLocalIpAddress return null")
    return null
}