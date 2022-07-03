package com.gmail.maystruks08.communicationimplementation

import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import java.net.*


class SocketFactoryImpl(
    private val logger: CommunicationLogger
) : SocketFactory {

    override fun create(config: SocketConfiguration): Socket {
        return try {
            val address = InetSocketAddress(config.ipAddress, config.inputPort)
            logger.log("$TAG Start connect to: $address")
            Socket().apply {
                bind(null)
                connect(address, config.connectTimeout)
            }
        } catch (e: Exception) {
            val errorMessage = e.message ?: e.localizedMessage
            logger.log("$TAG Create socket exception: $errorMessage")
            logger.log("$TAG Ip address is reachable: ${ping(config.ipAddress)}")
            throw RemoteError.ConnectionError.CreateSocketError(errorMessage)
        }
    }

    override fun createServerSocket(): ServerSocket {
        return try {
            ServerSocket()
                .apply {
                    reuseAddress = true
                    val localIp = getLocalIpAddress()
                    logger.log("$TAG Locale ip address: $localIp")
                    bind(InetSocketAddress(localIp, LOCAL_SERVER_PORT), 55)
                }
        } catch (e: Exception) {
            val errorMessage = e.message ?: e.localizedMessage
            logger.log("$TAG Create server socket exception: $errorMessage")
            throw RemoteError.ConnectionError.CreateSocketError(errorMessage)
        }
    }

    private fun ping(ipAddress: String): Boolean {
        val runtime = Runtime.getRuntime()
        return runCatching {
            val ipProcess = runtime.exec("/system/bin/ping -c 1 $ipAddress")
            val exitValue = ipProcess.waitFor()
            exitValue == 0
        }.getOrDefault(false)
    }

    private fun getLocalIpAddress(): String? {
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
            logger.log("$TAG -> getLocalIpAddress error: ${e.localizedMessage}")
        }
        logger.log("$TAG -> getLocalIpAddress return null")
        return null
    }

    companion object {
        private const val TAG = "SocketFactory"
        private const val LOCAL_SERVER_PORT = 8080
    }

}