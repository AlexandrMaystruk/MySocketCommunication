package com.gmail.maystruks08.remotecommunication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationimplementation.SocketFactoryImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.devices.ClientDevice
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactory
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactoryImpl
import com.gmail.maystruks08.remotecommunication.devices.HostDeviceImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("MissingPermission")
class CommunicationManagerImpl(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    lifecycle: Lifecycle,
    private val logger: CommunicationLogger
) : CommunicationManager, BroadcastReceiver()/*, LifecycleEventObserver*/ {

    init {
//        lifecycle.addObserver(this)
    }

    private var isSender = false
    var deviceName = "No Name"
    var isTest = false

    lateinit var coroutineScope: CoroutineScope

    private val incomingDataFlow = MutableSharedFlow<TransferData>(replay = 0)

    private val devices = mutableListOf<ClientDevice>()

    private val wifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }

    private val deviceFactory: DeviceFactory by lazy {
        DeviceFactoryImpl(context, wifiP2pManager, channel!!, dispatcher, logger) //TODO remove !!
    }

    private val socketFactory: SocketFactory by lazy {
        SocketFactoryImpl(logger)
    }

    private val channelListener = WifiP2pManager.ChannelListener {
        logger.log("$TAG onChannelDisconnected")
    }

    private var channel: WifiP2pManager.Channel? = null


    override fun onResume() {
        coroutineScope = CoroutineScope(Job() + dispatcher)
        channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), channelListener)
        registerReceiver()
    }

    override fun setMode(isSender: Boolean) {
        this.isSender = isSender
    }

    override fun onPause() {
        unregisterReceiver()
        coroutineScope.cancel()
        logger.log("$TAG onPause")
        deletePersistentGroups()
        disconnectDevice()
    }
//TODO need to make initialisation related to lifecycle
//    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
//        when (event) {
//            Lifecycle.Event.ON_RESUME -> {
//                channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), channelListener)
//                registerReceiver()
//                discoverPeers()
//            }
//            Lifecycle.Event.ON_PAUSE -> {
//                unregisterReceiver()
//            }
//            else -> Unit
//        }
//    }

    private suspend fun testCommunication(data: TransferData) {
        if (isSender) {
            val socketFactory = SocketFactoryImpl(logger)
            val config = SocketConfiguration("192.168.0.106", SERVER_PORT, 1000, 5 * 1000)
            val socket = socketFactory.create(config)
            ClientImpl(socket, logger).also {
                it.write(data)
                it.close()
            }
        } else {
            val hostDevice = HostDeviceImpl(dispatcher, logger, socketFactory)
            coroutineScope.launch {
                hostDevice.listenRemoteData().collect {
                    logger.log("$TAG HostDeviceImpl read data $it")
                    incomingDataFlow.emit(it)
                }
            }
        }
    }


    override suspend fun sendBroadcast(data: TransferData) {
        logger.log("$TAG sendBroadcast")

        if (isTest){
            testCommunication(data)
            return
        }

        devices.toList().forEach {
            it.connect()
            it.sendData(data.copy(data = "From :${deviceName} -> ${data.data}"))
            it.disconnect()
        }
    }

    override fun observeBroadcast(): Flow<TransferData> {
        logger.log("$TAG observeBroadcast")
        return incomingDataFlow
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context?, intent: Intent?) {
        logger.log("$TAG onReceive ${intent?.action}")
        when (val action = intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        logger.log("$TAG onReceive Wifi P2P is enabled")
                    } else {
                        logger.log("$TAG onReceive Wi-Fi P2P is not enabled")
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                requestPeers()
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // Broadcast when a device's details have changed,
                // such as the device's name
                onPeerConnectionChanged(intent)
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val wifiP2pDevice =
                    intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                deviceName = wifiP2pDevice?.deviceName ?: "No Name"
                logger.log("$TAG onReceive Respond to this device's wifi state changing ${wifiP2pDevice?.deviceName}, ${wifiP2pDevice?.isGroupOwner}")
            }
        }
    }

    private fun onPeerConnectionChanged(intent: Intent) {
        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
        if (networkInfo!!.isConnected) {
            //Ccnnected new device
            logger.log("$TAG onReceive Respond to new connection")
            if (isSender) {
                logger.log("$TAG host device not started because device send command")
                return
            }
            val hostDevice = HostDeviceImpl(dispatcher, logger, socketFactory)
            coroutineScope.launch {
                hostDevice.listenRemoteData().collect {
                    logger.log("$TAG HostDeviceImpl read data $it")
                    incomingDataFlow.emit(it)
                }
            }
        } else {
            logger.log("$TAG onReceive Respond disconnections")
        }
    }

    fun discoverPeers() {
        logger.log("$TAG discoverPeers")
        coroutineScope.launch(dispatcher) {
            while (devices.isEmpty()) {
                discoverPeersCo()
                requestPeersCo()
                delay(500)
            }
        }
    }

    private suspend fun discoverPeersCo() {
        suspendCoroutine<Any> { continuation ->
            wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onFailure(code: Int) {
                    logger.log("$TAG discoverPeers onFailure. Error code: $code")
                    continuation.resume(Unit)
                }
            })
        }
    }

    private suspend fun requestPeersCo() {
        suspendCoroutine<Any> { continuation ->
            logger.log("$TAG requestPeers")
            //todo add synchronization
            wifiP2pManager.requestPeers(channel) { wifiP2pDeviceList ->
                logger.log("$TAG requestPeers wifiP2pDeviceList size:${wifiP2pDeviceList.deviceList.size}")
                val clients =
                    wifiP2pDeviceList.deviceList.map {
                        logger.log("$TAG requestPeers DEVICE ${it.deviceName}")
                        deviceFactory.create(it)
                    }
                if (devices.size != clients.size) {
                    devices.clear()
                    devices.addAll(clients)
                }
                continuation.resume(Unit)
            }
        }
    }

    private fun requestPeers() {
        logger.log("$TAG requestPeers")
        //todo add synchronization
        wifiP2pManager.requestPeers(channel) { wifiP2pDeviceList ->
            logger.log("$TAG requestPeers wifiP2pDeviceList size:${wifiP2pDeviceList.deviceList.size}")
            val clients =
                wifiP2pDeviceList.deviceList.filter { !it.deviceName.contains("samsung", true) }
                    .map {
                        logger.log("$TAG requestPeers DEVICE ${it.deviceName}")
                        deviceFactory.create(it)
                    }
            if (devices.size != clients.size) {
                devices.clear()
                devices.addAll(clients)
            }
        }
    }

    private fun registerReceiver() {
        logger.log("$TAG registerReceiver")
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(this, intentFilter)
    }

    private fun unregisterReceiver() {
        logger.log("$TAG unregisterReceiver")
        runCatching {
            context.unregisterReceiver(this)
        }.getOrElse {
            Log.e(TAG, it.message, it)
        }
    }

    private fun deletePersistentGroups() {
        logger.log("$TAG deletePersistentGroups start")
        try {
            val methods = WifiP2pManager::class.java.methods
            for (i in methods.indices) {
                if (methods[i].name == "deletePersistentGroup") {
                    // Delete any persistent group
                    for (netId in 0..31) {
                        methods[i].invoke(wifiP2pManager, channel, netId, null)
                    }
                }
            }
        } catch (e: Exception) {
            logger.log("$TAG deletePersistentGroups error:${e.localizedMessage}")
            e.printStackTrace()
        }
    }

    private fun disconnectDevice() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logger.log("$TAG disconnectDevice onSuccess")
            }

            override fun onFailure(i: Int) {
                logger.log("$TAG disconnectDevice onFailure:$i")
            }
        })
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

    companion object {
        private const val TAG = "CommunicationManagerImpl"
        const val SERVER_PORT = 8080
    }

}