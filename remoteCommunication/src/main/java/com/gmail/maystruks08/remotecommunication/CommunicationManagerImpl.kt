package com.gmail.maystruks08.remotecommunication

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdServiceInfo
import com.gmail.maystruks08.communicationimplementation.ClientImpl
import com.gmail.maystruks08.communicationimplementation.ServerImpl.Companion.LOCAL_SERVER_PORT
import com.gmail.maystruks08.communicationimplementation.SocketFactoryImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.SocketConfiguration
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactory
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactoryImpl
import com.gmail.maystruks08.remotecommunication.managers.NsdControllerImpl
import com.gmail.maystruks08.remotecommunication.managers.NsdServiceCommand
import com.gmail.maystruks08.remotecommunication.managers.P2pBroadcastCommand
import com.gmail.maystruks08.remotecommunication.managers.P2pControllerImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@SuppressLint("MissingPermission")
class CommunicationManagerImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: CommunicationLogger
) : CommunicationManager {

    var isSender = false
    var isTest = false

    var nsdServiceInfo: NsdServiceInfo? = null

    private val _incomingDataFlow = MutableSharedFlow<TransferData>()
    private val _p2pManager by lazy { P2pControllerImpl(context, coroutineScope, dispatcher, logger) }
    private val _nsdManager by lazy { NsdControllerImpl(context, coroutineScope, dispatcher, logger) }
    private val _deviceFactory: DeviceFactory by lazy {
        DeviceFactoryImpl(
            coroutineScope,
            dispatcher,
            logger
        )
    }

    private var hostDevice = _deviceFactory.createHost()
    private var listenRemoteDataJob: Job? = null


    @Suppress("DEPRECATION")
    override fun onStart() {
        logger.log("$TAG onResume")
        _p2pManager.startWork()
        coroutineScope.launch(dispatcher) {
            _p2pManager.p2pBroadcastCommandFlow.collect { p2pBroadcastCommand ->
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
                        listenRemoteData()
                    }
                    P2pBroadcastCommand.DeviceDisconnected -> {
                        //handle this case
                    }
                }
            }
        }
    }

    fun nsdManagerOnStart() {
        _nsdManager.onStart()
    }

    fun discoverNdsServices() {
        coroutineScope.launch {
            _nsdManager.discoverNdsServices().collect {
                logger.log("$TAG command collected $it")
                delay(1000)
                async(Dispatchers.Default) {
                    logger.log("$TAG command collected  and start async")
                    delay(1000)
                    when (it) {
                        is NsdServiceCommand.ReceivedRemoteConnectionInfo -> {
                            nsdServiceInfo = it.nsdServiceInfo
                            sendData(
                                TransferData(
                                    200,
                                    "Fuck you, wifi direct!!!"
                                )
                            )
                        }
                    }
                }.await()
            }
        }
    }

    fun nsdManagerOnStop() {
        _nsdManager.onStop()
    }

    fun registerDnsService() {
        _p2pManager.registerDnsService()
    }

    fun discoverDnsService() {
        _p2pManager.discoverDnsService()
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

        _p2pManager.getWifiP2pDevices().forEach {
            val isConnected = runCatching {
                _p2pManager.sendConnectRequest(it)
            }.getOrDefault(false)

            if (isConnected) {
                val wifiP2pInfo = _p2pManager.getConnectionInfo()
                //TODO groupOwnerAddress is always 192.168.49.1 WTF ??????????
                if (wifiP2pInfo.isGroupOwner && wifiP2pInfo.groupOwnerAddress != null) {
                    val client = _deviceFactory.createClient()
                    client.sendData(wifiP2pInfo.groupOwnerAddress.hostName, data)
                } else {
                    listenRemoteData()
                }
            }
        }
    }

    override fun observeBroadcast(): Flow<TransferData> {
        logger.log("$TAG observeBroadcast")
        return _incomingDataFlow
    }


    override fun onStop() {
        logger.log("$TAG onPause")
        _p2pManager.stopWork()
    }

    private suspend fun testCommunication(data: TransferData) {
        if (isSender) {
            sendData(data)
        } else {
            listenRemoteData()
        }
    }

    private fun sendData(data: TransferData) {
        runCatching {
            val socketFactory = SocketFactoryImpl(logger)
            val config = SocketConfiguration(
                nsdServiceInfo?.host?.hostAddress.orEmpty(),
                LOCAL_SERVER_PORT,
                1000,
                5 * 1000
            )
            val socket = socketFactory.create(config)
            ClientImpl(socket, logger).also {
                it.write(data)
                it.close()
            }
        }
    }

    private suspend fun listenRemoteData() {
        runCatching {
            listenRemoteDataJob?.cancel()
            listenRemoteDataJob = coroutineScope.launch {
                hostDevice.listenRemoteData().collect {
                    logger.log("$TAG HostDeviceImpl read data $it")
                    _incomingDataFlow.emit(it)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Communication->"
    }

}