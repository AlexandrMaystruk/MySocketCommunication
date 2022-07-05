package com.gmail.maystruks08.remotecommunication

import android.annotation.SuppressLint
import android.content.Context
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationimplementation.SocketFactoryImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactory
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

@SuppressLint("MissingPermission")
class CommunicationManagerImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: CommunicationLogger
) : CommunicationManager {

    var isSender = false
    var isTest = false

    private val incomingDataFlow = MutableSharedFlow<TransferData>()
    private val p2pManager by lazy { P2pManagerImpl(context, coroutineScope, dispatcher, logger) }
    private val deviceFactory: DeviceFactory by lazy { DeviceFactoryImpl(coroutineScope, dispatcher, logger) }


    @Suppress("DEPRECATION")
    override fun onStart() {
        logger.log("$TAG onResume")
        p2pManager.startWork()
        coroutineScope.launch {
            p2pManager.p2pBroadcastCommandFlow.collect { p2pBroadcastCommand ->
                logger.log("$TAG command: ${p2pBroadcastCommand.javaClass.simpleName}")
                when (p2pBroadcastCommand) {
                    is P2pBroadcastCommand.CurrentDeviceChanged -> {
                        logger.log("$TAG ${p2pBroadcastCommand.wifiP2pDevice.deviceName}")
                    }
                    P2pBroadcastCommand.DeviceConnected -> {
                        if (isSender) {
                            logger.log("$TAG host device not started because device send command")
                            return@collect
                        }
                        val hostDevice = deviceFactory.createHost()
                        hostDevice.listenRemoteData().collect {
                            logger.log("$TAG HostDevice receive data $it")
                            incomingDataFlow.emit(it)
                        }
                    }
                    P2pBroadcastCommand.DeviceDisconnected -> {
                        //handle this case
                    }
                }
            }
        }
    }

    override suspend fun discoverPeers() {
        p2pManager.getWifiP2pDevices()
    }

    override suspend fun sendBroadcast(data: TransferData) {
        logger.log("$TAG sendBroadcast")

        if (isTest) {
            testCommunication(data)
            return
        }
        if (!isSender) {
            logger.log("$TAG change to sender!")
            return
        }

        p2pManager.getWifiP2pDevices().forEach {
            val isConnected = runCatching {
                p2pManager.sendConnectRequest(it)
            }.getOrDefault(false)

            if (isConnected) {
                val wifiP2pInfo = p2pManager.getConnectionInfo()
                //TODO groupOwnerAddress is always 192.168.49.1 WTF ??????????
                if (wifiP2pInfo.isGroupOwner && wifiP2pInfo.groupOwnerAddress != null) {
                    val client = deviceFactory.createClient()
                    client.sendData(wifiP2pInfo.groupOwnerAddress.hostName, data)
                } else {
                    val hostDevice = deviceFactory.createHost()
                    hostDevice.listenRemoteData().collect {
                        logger.log("$TAG HostDeviceImpl read data $it")
                        incomingDataFlow.emit(it)
                    }
                }
            }
        }
    }

    override fun observeBroadcast(): Flow<TransferData> {
        logger.log("$TAG observeBroadcast")
        return incomingDataFlow
    }


    override fun onStop() {
        logger.log("$TAG onPause")
        p2pManager.stopWork()
    }


    //todo move this to socket factory
    /**
     * This method get local ip and thy to ping in parallel all ips in network
     * This is a resource-intensive call, need to avoid often using this method!
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getAllIpsInLocaleNetwork(): List<InetAddress> {
        return withContext(Dispatchers.IO) {
            val listOfIpAddressesInLocalNetwork = mutableListOf<InetAddress>()
            val ipString = getLocalIpAddress().orEmpty()
            val prefix = ipString.substring(0, ipString.lastIndexOf(".") + 1)
            val deferredList = mutableListOf<Deferred<Any>>()
            for (i in 0..254) {
                deferredList.add(
                    async {
                        val testIp = "$prefix$i"
                        val inetAddress = InetAddress.getByName(testIp)
                        if (inetAddress.isReachable(300)) {
                            logger.log("testIp: $testIp isReachable = true")
                            listOfIpAddressesInLocalNetwork.add(inetAddress)
                        }
                    }
                )
            }
            deferredList.awaitAll()
            return@withContext listOfIpAddressesInLocalNetwork
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val enumeration = NetworkInterface.getNetworkInterfaces()
            while (enumeration.hasMoreElements()) {
                val networkInterface = enumeration.nextElement()
                val enumIpAddress = networkInterface.inetAddresses
                while (enumIpAddress.hasMoreElements()) {
                    val internetAddress = enumIpAddress.nextElement()
                    logger.log("Communication nextElement -> internetAddress.hostAddress ${internetAddress.hostAddress}")
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

    private suspend fun testCommunication(data: TransferData) {
        if (isSender) {
            val socketFactory = SocketFactoryImpl(logger)
            val defIp = "192.168.0.106" //for text only
            val config = SocketConfiguration(defIp, SERVER_PORT, 1000, 5 * 1000)
            val socket = socketFactory.create(config)
            ClientImpl(socket, logger).also {
                it.write(data)
                it.close()
            }
        } else {
            val hostDevice = deviceFactory.createHost()
            coroutineScope.launch {
                hostDevice.listenRemoteData().collect {
                    logger.log("$TAG HostDeviceImpl read data $it")
                    incomingDataFlow.emit(it)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CommunicationManager"
        const val SERVER_PORT = 8080
    }

}