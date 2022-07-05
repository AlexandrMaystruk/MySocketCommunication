package com.gmail.maystruks08.remotecommunication

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
import android.os.Looper
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@SuppressLint("MissingPermission")
class P2pManagerImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: CommunicationLogger
) : BroadcastReceiver() {

    private val _wifiP2pManager by lazy { context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager }
    private val _devicesMutex = Mutex()
    private val _devices = mutableMapOf<WifiP2pDevice, Boolean>()
    private var _channel: WifiP2pManager.Channel? = null
    private val _p2pBroadcastCommandSharedFlow = MutableSharedFlow<P2pBroadcastCommand>()
    private val _channelListener = WifiP2pManager.ChannelListener {
        logger.log("$TAG onChannelDisconnected")
    }

    val p2pBroadcastCommandFlow: Flow<P2pBroadcastCommand>
        get() = _p2pBroadcastCommandSharedFlow

    fun startWork() {
        _channel = _wifiP2pManager.initialize(context, Looper.getMainLooper(), _channelListener)
        registerReceiver(this, fullIntentFilter)
        coroutineScope.launch(dispatcher) { getWifiP2pDevices() }
    }

    suspend fun getWifiP2pDevices(): List<WifiP2pDevice> {
        logger.log("$TAG discoverPeers")
        _devicesMutex.withLock {
            withContext(dispatcher) {
                while (_devices.isEmpty()) {
                    discoverPeers()
                    requestPeers()
                    delay(500)
                }
            }
        }
        logger.log("$TAG discoverPeers finished")
        return _devices.keys.toList()
    }

    @Throws(RemoteError.ConnectionError.ConnectToRemoteError::class)
    suspend fun sendConnectRequest(device: WifiP2pDevice): Boolean {
        suspendCoroutine<Unit> {
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                wps.setup = WpsInfo.PBC
                groupOwnerIntent = 0
            }
            logger.log("Start send pair command")
            _wifiP2pManager.connect(_channel, config, object : WifiP2pManager.ActionListener {
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
        val isDeviceConnected = _devices[device] ?: false
        if (isDeviceConnected) {
            logger.log("already connected")
            return true
        }
        val connectionResult = waitConnectionBroadcast()
        _devices[device] = connectionResult
        logger.log("waitConnectionBroadcast result $connectionResult")
        return connectionResult
    }

    fun stopWork() {
        unregisterReceiver(this)
        logger.log("$TAG onPause")
        deletePersistentGroups()
        removeGroup()
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
                coroutineScope.launch {
                    requestPeers()
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                coroutineScope.launch {
                    val networkInfo =
                        intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    val command = if (networkInfo?.isConnected == true) {
                        P2pBroadcastCommand.DeviceConnected
                    } else {
                        P2pBroadcastCommand.DeviceDisconnected
                    }
                    _p2pBroadcastCommandSharedFlow.emit(command)
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                coroutineScope.launch {
                    val wifiP2pDevice = intent.getParcelableExtra<WifiP2pDevice>(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                    )
                    _p2pBroadcastCommandSharedFlow.emit(
                        P2pBroadcastCommand.CurrentDeviceChanged(wifiP2pDevice ?: return@launch)
                    )
                }
            }
        }
    }

    private suspend fun discoverPeers() {
        suspendCoroutine<Any> { continuation ->
            _wifiP2pManager.discoverPeers(_channel, object : WifiP2pManager.ActionListener {
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

    private suspend fun requestPeers() {
        suspendCoroutine<Any> { continuation ->
            _wifiP2pManager.requestPeers(_channel) { wifiP2pDeviceList ->
                logger.log("$TAG requestPeers wifiP2pDeviceList size:${wifiP2pDeviceList.deviceList.size}")
                //TODO improve _devices updating
                val clients = wifiP2pDeviceList.deviceList
                if (_devices.size != clients.size) {
                    val oldDevices = _devices.toMap()
                    _devices.clear()
                    clients.forEach {
                        logger.log("$TAG ${it.deviceName}, isGroupOwner: ${it.isGroupOwner}, status: ${it.status}")
                        _devices[it] = oldDevices[it] ?: false
                    }
                }
                continuation.resume(Unit)
            }
        }
    }

    suspend fun getConnectionInfo() = suspendCoroutine<WifiP2pInfo> { continuation ->
        runCatching {
            _wifiP2pManager.requestConnectionInfo(_channel) {
                continuation.resume(it)
            }
        }.onFailure {
            continuation.resumeWithException(it)
        }
    }

    private suspend fun waitConnectionBroadcast(): Boolean {
        var receiver: BroadcastReceiver? = null
        return try {
            withTimeout(60 * 1000) {
                suspendCoroutine { continuation ->
                    val connectionChangeReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            when (intent?.action) {
                                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                                    logger.log("onReceive Respond to connection changed")
                                    val networkInfo =
                                        intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
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
                    registerReceiver(connectionChangeReceiver, connectionIntentFilter)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logger.log("waitConnectionBroadcast error: ${e.localizedMessage}")
            receiver?.let { unregisterReceiver(it) }
            false
        }
    }

    private fun registerReceiver(broadcastReceiver: BroadcastReceiver, intentFilter: IntentFilter) {
        logger.log("registerReceiver")
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


    private val fullIntentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val connectionIntentFilter =
        IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)


    private fun deletePersistentGroups() {
        logger.log("$TAG deletePersistentGroups start")
        try {
            val methods = WifiP2pManager::class.java.methods
            for (i in methods.indices) {
                if (methods[i].name == "deletePersistentGroup") {
                    // Delete any persistent group
                    for (netId in 0..31) {
                        methods[i].invoke(_wifiP2pManager, _channel, netId, null)
                    }
                }
            }
        } catch (e: Exception) {
            logger.log("$TAG deletePersistentGroups error:${e.localizedMessage}")
            e.printStackTrace()
        }
    }

    private fun removeGroup() {
        _wifiP2pManager.removeGroup(_channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logger.log("$TAG disconnectDevice onSuccess")
            }

            override fun onFailure(i: Int) {
                logger.log("$TAG disconnectDevice onFailure:$i")
            }
        })
    }


    companion object {
        private const val TAG = "P2pManager"
    }

}