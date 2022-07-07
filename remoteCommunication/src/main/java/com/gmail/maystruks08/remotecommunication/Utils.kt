package com.gmail.maystruks08.remotecommunication

import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import kotlinx.coroutines.*
import java.net.*


/**
 * This method get local ip and thy to ping in parallel all ips in network
 * This is a resource-intensive call, need to avoid often using this method!
 */
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun getAllIpsInLocaleNetwork(localeIp: String): List<InetAddress> {
    return withContext(Dispatchers.IO) {
        val listOfIpAddressesInLocalNetwork = mutableListOf<InetAddress>()
        val prefix = localeIp.substring(0, localeIp.lastIndexOf(".") + 1)
        val deferredList = mutableListOf<Deferred<Any>>()
        for (i in 0..254) {
            deferredList.add(
                async {
                    val testIp = "$prefix$i"
                    val inetAddress = InetAddress.getByName(testIp)
                    if (inetAddress.isReachable(300)) {
                        listOfIpAddressesInLocalNetwork.add(inetAddress)
                    }
                }
            )
        }
        deferredList.awaitAll()
        return@withContext listOfIpAddressesInLocalNetwork
    }
}

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
        logger.log("get ip address error: ${e.localizedMessage}")
    }
    logger.log("return null ip")
    return null
}