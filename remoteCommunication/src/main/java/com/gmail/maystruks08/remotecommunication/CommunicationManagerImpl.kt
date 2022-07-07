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
import com.gmail.maystruks08.remotecommunication.devices.ClientDevice
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactory
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactoryImpl
import com.gmail.maystruks08.remotecommunication.managers.NsdController
import com.gmail.maystruks08.remotecommunication.managers.NsdControllerCommand
import com.gmail.maystruks08.remotecommunication.managers.P2pController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class CommunicationManagerImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: CommunicationLogger
) : CommunicationManager {

    var isSender = false
    var isTest = false
    private var _lastConnectedServiceIpAddress: String? = null //for test only

    private var isStarted = AtomicBoolean(false)
    private val _incomingDataFlow = MutableSharedFlow<TransferData>()
    private val _p2pManager by lazy { P2pController(context, coroutineScope, dispatcher, logger) }
    private val _nsdManager by lazy { NsdController(context, logger) }
    private val _hostDevice by lazy { _deviceFactory.createHost() }
    private val _deviceFactory: DeviceFactory by lazy {
        DeviceFactoryImpl(coroutineScope, dispatcher, logger)
    }
    private val clients = mutableSetOf<ClientDevice>()
    private var _listenRemoteDataJob: Job? = null

    @Suppress("DEPRECATION")
    override fun onStart() {
        logger.log("$TAG onResume")
        _p2pManager.startWork()
        _nsdManager.onStart()
        runHostDevice()
        discoverNdsServices()
        isStarted.set(true)
    }

    override fun getRemoteClientsTransferDataFlow(): Flow<TransferData> {
        return _incomingDataFlow
    }

    override fun sendToRemoteClients(data: TransferData) {
        if (isTest) {
            testCommunication(data)
            return
        }
        if (!isSender) {
            logger.log("$TAG change to sender!")
            return
        }

        if (clients.isEmpty()) {
            logger.log("$TAG no connected clients, can't send!!!")
            return
        }

        clients.forEach { clientDevice ->
            coroutineScope.launch(dispatcher) {
                runCatching {
                    clientDevice.sendData(data)
                }.onFailure {
                    logger.log("$TAG failure send to client: ${clientDevice.ipAddress}")
                }
            }
        }
    }

    override fun onStop() {
        logger.log("$TAG onStop")
        _listenRemoteDataJob?.cancel()
        _nsdManager.onStop()
        _p2pManager.stopWork()
        isStarted.set(false)
    }

    private fun discoverNdsServices() {
        coroutineScope.launch(dispatcher) {
            _nsdManager.discoverNdsServices().collect {
                when (it) {
                    is NsdControllerCommand.NewServiceConnected -> {
                        handleNewServiceConnection(it.nsdServiceInfo)
                    }
                    NsdControllerCommand.ServiceDiscoveryFinished -> {

                    }
                }
            }
        }
    }

    private fun handleNewServiceConnection(nsdServiceInfo: NsdServiceInfo) {
        _lastConnectedServiceIpAddress = nsdServiceInfo.host.hostAddress //for test only

        logger.log("$TAG start handle new client")
        clients.find { it.deviceName == nsdServiceInfo.serviceName } ?: run {
            clients.add(_deviceFactory.createClient(
                deviceName = nsdServiceInfo.serviceName,
                deviceIpAddress = nsdServiceInfo.host.hostAddress ?: return
            ).also {
                logger.log("$TAG new client created")
            })
        }
    }

    private fun runHostDevice() {
        runCatching {
            _listenRemoteDataJob?.cancel()
            _listenRemoteDataJob = coroutineScope.launch(dispatcher) {
                _hostDevice.listenRemoteData().collect {
                    logger.log("$TAG HostDeviceImpl read data $it")
                    _incomingDataFlow.emit(it)
                }
            }
        }
    }


    private fun testCommunication(data: TransferData) {
        if (isSender) {
            runCatching {
                val socketFactory = SocketFactoryImpl(logger)
                val config = SocketConfiguration(
                    _lastConnectedServiceIpAddress.orEmpty(),
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
    }

    companion object {
        private const val TAG = "Communication->"
    }

}