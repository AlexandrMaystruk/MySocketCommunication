package com.gmail.maystruks08.remotecommunication.controllers

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
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.os.Looper
import com.gmail.maystruks08.communicationimplementation.ServerImpl.Companion.LOCAL_SERVER_PORT
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import com.gmail.maystruks08.remotecommunication.controllers.NsdController.Companion.SERVICE_NAME
import com.gmail.maystruks08.remotecommunication.controllers.NsdController.Companion.SERVICE_TYPE
import com.gmail.maystruks08.remotecommunication.controllers.commands.P2pControllerCommand
import com.gmail.maystruks08.remotecommunication.getLocalIpAddress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@SuppressLint("MissingPermission")
class P2pController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: CommunicationLogger
) : BroadcastReceiver() {

    private val _wifiP2pManager by lazy { context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager }
    private val _devicesMutex = Mutex()
    private val _devices = mutableMapOf<WifiP2pDevice, Boolean>()
    private var _channel: WifiP2pManager.Channel? = null
    private val _p2PControllerCommandSharedFlow = MutableSharedFlow<P2pControllerCommand>()
    private val _channelListener = WifiP2pManager.ChannelListener {
        log("onChannelDisconnected")
    }

    val p2PControllerCommandFlow: Flow<P2pControllerCommand>
        get() = _p2PControllerCommandSharedFlow

    fun startWork() {
        log("startWork")
        _channel = _wifiP2pManager.initialize(context, Looper.getMainLooper(), _channelListener)
        registerReceiver(this, fullIntentFilter)
    }

    suspend fun getWifiP2pDevices(): List<WifiP2pDevice> {
        log("discoverPeers started")
        _devicesMutex.withLock {
            withContext(dispatcher) {
                discoverPeers()
                requestPeers()
                if(_devices.isEmpty()){
                    log("waiting peers..")
                }
                while (_devices.isEmpty()) {
                    delay(500)
                }
            }
        }
        log("discoverPeers finished: ${_devices.map { "${it.key.deviceName} ${it.key.isGroupOwner}," }}}")
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
        tryToRemoveGroup()
        log("stopWork")
    }


    @Suppress("DEPRECATION")
    override fun onReceive(context: Context?, intent: Intent?) {
        log("onReceive ${intent?.action}")
        when (val action = intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        log("onReceive Wifi P2P is enabled")
                    } else {
                        log("onReceive Wi-Fi P2P is not enabled")
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
                        P2pControllerCommand.DeviceConnected
                    } else {
                        P2pControllerCommand.DeviceDisconnected
                    }
                    _p2PControllerCommandSharedFlow.emit(command)
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                coroutineScope.launch {
                    val wifiP2pDevice = intent.getParcelableExtra<WifiP2pDevice>(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                    )
                    _p2PControllerCommandSharedFlow.emit(
                        P2pControllerCommand.CurrentDeviceChanged(wifiP2pDevice ?: return@launch)
                    )
                }
            }
        }
    }

    fun registerDnsService() {
        val localeIp = getLocalIpAddress(logger) ?: "Can't get ip"
        val record: Map<String, String> = mapOf(
            DEVICE_PORT_KEY to LOCAL_SERVER_PORT.toString(),
            DEVICE_IP_KEY to localeIp
        )
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_NAME, SERVICE_TYPE, record)
        _wifiP2pManager.addLocalService(
            _channel,
            serviceInfo,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() = logger.log("addLocalService onSuccess")
                override fun onFailure(arg0: Int) = logger.log("addLocalService onFailure: $arg0")
            })
    }

    fun discoverDnsSdServiceResponse() {
        _wifiP2pManager.setDnsSdResponseListeners(
            _channel,
            { instanceName, registrationType, resourceType ->
                logger.log("onBonjourServiceAvailable $instanceName, ${resourceType.deviceName}")
            },
            { fullDomain, record, device ->
                logger.log("DnsSdTxtRecord available. fullDomain: $fullDomain, deviceName: ${device.deviceName}")
                val remoteDeviceIp = record[DEVICE_IP_KEY]
                val remoteDevicePort = record[DEVICE_PORT_KEY]
                logger.log("DnsSdTxtRecord ip:$remoteDeviceIp : port: $remoteDevicePort")
            }
        )
    }

    private suspend fun discoverPeers() {
        suspendCoroutine<Any> { continuation ->
            _wifiP2pManager.discoverPeers(_channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onFailure(code: Int) {
                    log("discoverPeers onFailure. Error code: $code")
                    continuation.resume(Unit)
                }
            })
        }
    }

    private suspend fun requestPeers() {
        //potential place on concurrent modification exception!
        suspendCoroutine<Any> { continuation ->
            _wifiP2pManager.requestPeers(_channel) { wifiP2pDeviceList ->
                val clients = wifiP2pDeviceList.deviceList
                if (_devices.size != clients.size) {
                    val oldDevices = _devices.toMap()
                    _devices.clear()
                    clients.forEach { _devices[it] = oldDevices[it] ?: false }
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
        return runCatching {
            withTimeout(60 * 1000) {
                suspendCoroutine<Boolean> { continuation ->
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
        }.getOrElse { throwable ->
            logger.log("waitConnectionBroadcast error: ${throwable.localizedMessage}")
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

    private fun tryToRemoveGroup() {
        if(_devices.isEmpty()) return
        runCatching {
            _wifiP2pManager.removeGroup(_channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    log("disconnectDevice onSuccess")
                }

                override fun onFailure(i: Int) {
                    log("disconnectDevice onFailure:$i")
                }
            })
        }
    }

    private fun log(message: String) {
        logger.log("$TAG $message")
    }

    companion object {
        private const val TAG = "P2p->"
        private const val DEVICE_PORT_KEY = "devicePort"
        private const val DEVICE_IP_KEY = "deviceIp"
    }

}