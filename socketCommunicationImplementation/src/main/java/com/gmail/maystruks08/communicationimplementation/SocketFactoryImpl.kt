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
       return runCatching {
           val address = InetSocketAddress(config.ipAddress, config.inputPort)
           logger.log("$TAG Start connect to: $address")
           var attempt = 0
           val socket = Socket()
           do {
               runCatching {
                   socket.bind(null)
                   socket.reuseAddress = true
                   socket.connect(address, config.connectTimeout)
               }.getOrElse {
                   val errorMessage = it.message ?: it.localizedMessage
                   logger.log("$TAG attempt $attempt: Create socket exception: $errorMessage")
                   if (attempt >= 10) throw it
                   attempt++
               }
           } while (!socket.isConnected && attempt < 10)
           if(!socket.isConnected) {
               logger.log("$TAG can't connect to socket")
           }
           return@runCatching socket
        }.getOrElse {
            val errorMessage = it.message ?: it.localizedMessage
            logger.log("$TAG Create socket exception: $errorMessage")
            logger.log("$TAG Ip address is reachable: ${ping(config.ipAddress)}")
            throw RemoteError.ConnectionError.CreateSocketError(errorMessage)
        }
    }

    override fun createServerSocket(port: Int): ServerSocket {
        return runCatching {
            ServerSocket()
                .apply {
                    reuseAddress = true
                    val ip = getLocalIpAddress()
                    logger.log("$TAG create on ip address: $ip")
                    bind(InetSocketAddress(ip, port), 55)
                }
        }.getOrElse {
            val errorMessage = it.message ?: it.localizedMessage
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
        runCatching {
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
        }.getOrElse {
            logger.log("$TAG -> getLocalIpAddress error: ${it.localizedMessage}")
        }
        logger.log("$TAG -> getLocalIpAddress return null")
        return null
    }

    companion object {
        private const val TAG = "SocketFactory"
    }

}