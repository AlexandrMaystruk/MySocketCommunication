package com.gmail.maystruks08.remotecommunication.devices

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationinterface.Client
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.CommunicationManagerImpl.Companion.SERVER_PORT
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


@SuppressLint("MissingPermission")
class ClientDeviceImpl(
    private val deviceMacAddress: String,
    private val wifiP2pManager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val socketFactory: SocketFactory,
    private val dispatcher: CoroutineDispatcher,
    private val logger: CommunicationLogger,
    private val context: Context
) : ClientDevice {

    private var client: Client? = null
    private var macAndInetAddress: Map<String, InetAddress>? = null

    private var isConnected = false

    override suspend fun connect() {
        suspendCoroutine<Unit> {
            val config = WifiP2pConfig().apply {
                deviceAddress = deviceMacAddress
                wps.setup = WpsInfo.PBC
                groupOwnerIntent = 0
            }
            logger.log("Start send pair command")
            wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    logger.log("pair command success")
                    it.resume(Unit)
                }

                override fun onFailure(code: Int) {
                    logger.log("pair command onFailure")
                    it.resumeWithException(RemoteError.ConnectionError.ConnectToRemoteError(code.toString()))
                }
            })
        }
        logger.log("waitConnectionBroadcast")
        if(isConnected) {
            logger.log("already connected")
            return
        }
        isConnected = waitConnectionBroadcast()
        logger.log("waitConnectionBroadcast result $isConnected")
    }

    override suspend fun sendData(data: TransferData) {
        logger.log("start sendData: $data")
        val wifiP2pInfo = getConnectionInfo()
        logger.log("isGroupOwner: ${wifiP2pInfo.isGroupOwner}")

//        val isGroupOwner = wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner
//
//
//        val inetAddress = wifiP2pInfo.groupOwnerAddress ?: run {
//            logger.log("sendData error. groupOwnerAddress is null")
//            return
//        }
        val config = SocketConfiguration("192.168.0.106", SERVER_PORT, 1000, 5 * 1000)
        val socket = socketFactory.create(config)
        ClientImpl(socket, logger).also {
            client = it
            it.write(data)
            it.close()
        }
    }

    private suspend fun getConnectionInfo() = suspendCoroutine<WifiP2pInfo> { continuation ->
        runCatching {
            wifiP2pManager.requestConnectionInfo(channel) {
                continuation.resume(it)
            }
        }.onFailure {
            continuation.resumeWithException(it)
        }
    }

    fun getMacAddress(address: InetAddress?): String? {
        try {
            val network = NetworkInterface.getByInetAddress(address)
            val macArray = network?.hardwareAddress ?: return null
            val str = StringBuilder()
            for (i in macArray.indices) {
                str.append(
                    String.format("%02X%s", macArray[i], if (i < macArray.size - 1) " " else "")
                )
                return str.toString()
            }
        } catch (e: Exception) {
            logger.log(e.stackTrace.toString())
        }
        return null
    }

    suspend fun getHasMapWithMacAndIp(): Map<String, InetAddress> {
        return hashMapOf<String, InetAddress>().apply {
            getAllIpsInLocaleNetwork().forEach {
                val macAddress = getMacAddress(it) ?: return@forEach
                put(macAddress, it)
            }
        }.also { macAndInetAddress = it }
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

    override suspend fun disconnect() {
        //TODO not sure that this is correct
//        return suspendCoroutine {
//            client?.close()
//            wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
//                override fun onSuccess() {
//                    it.resume(Unit)
//                }
//
//                override fun onFailure(code: Int) {
//                    it.resumeWithException(RemoteError.ConnectToRemoteError(code.toString()))
//                }
//            })
//        }
    }


    private suspend fun waitConnectionBroadcast(): Boolean {
        var receiver : BroadcastReceiver? = null
        return try {
            withTimeout(60 * 1000) {
                suspendCoroutine { continuation ->
                    val connectionChangeReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            when (intent?.action) {
                                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                                    logger.log("onReceive Respond to connection changed")
                                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                                    unregisterReceiver(this)
                                    continuation.resume(networkInfo!!.isConnected)
                                }
                                else -> {
                                    logger.log("onReceive Respond else case")
                                    continuation.resume(false)
                                }
                            }
                        }
                    }.also { receiver = it }
                    registerReceiver(connectionChangeReceiver)
                }
            }
        } catch (e: Exception){
            e.printStackTrace()
            logger.log("waitConnectionBroadcast error: ${e.localizedMessage}")
            receiver?.let { unregisterReceiver(it) }
            false
        }
    }

    private fun registerReceiver(broadcastReceiver: BroadcastReceiver) {
        logger.log("registerReceiver")
        val intentFilter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    private fun unregisterReceiver(broadcastReceiver: BroadcastReceiver) {
        logger.log(" unregisterReceiver")
        runCatching {
            context.unregisterReceiver(broadcastReceiver)
        }.getOrElse {
            logger.log(it.message ?: it.localizedMessage)
        }
    }
}
