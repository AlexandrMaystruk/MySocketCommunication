package com.gmail.maystruks08.remotecommunication

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdServiceInfo
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
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

    private var _isStarted = AtomicBoolean(false)
    private val _clients = mutableSetOf<ClientDevice>()
    private var _listenRemoteDataJob: Job? = null
    private val _incomingDataFlow = MutableSharedFlow<TransferData>()
    private val _p2pManager by lazy { P2pController(context, coroutineScope, dispatcher, logger) }
    private val _nsdManager by lazy { NsdController(context, logger) }
    private val _hostDevice by lazy { _deviceFactory.createHost() }
    private val _deviceFactory: DeviceFactory by lazy {
        DeviceFactoryImpl(coroutineScope, dispatcher, logger)
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        log("onResume")
        _p2pManager.startWork()
        _nsdManager.startWork()
        runHostDevice()
        discoverNdsServices()
        _isStarted.set(true)
    }

    override fun getRemoteClientsTransferDataFlow(): Flow<TransferData> {
        return _incomingDataFlow
    }

    override fun sendToRemoteClients(data: TransferData) {
        if (!_isStarted.get()) {
            log("call firstly onStart()")
            return
        }

        if (_clients.isEmpty()) {
            log("no connected clients, can't send!!!")
            return
        }

        _clients.forEach { clientDevice ->
            coroutineScope.launch(dispatcher) {
                runCatching {
                    clientDevice.sendData(data)
                }.onFailure {
                    log("failure send to client: ${clientDevice.ipAddress}")
                }
            }
        }
    }

    override fun onStop() {
        if (!_isStarted.get()) {
            log("component already onStop() ")
            return
        }
        log("onStop")
        _listenRemoteDataJob?.cancel()
        _clients.clear()
        _nsdManager.stopWork()
        _p2pManager.stopWork()
        _isStarted.set(false)
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
        log("start handle new client")
        _clients.find { it.deviceName == nsdServiceInfo.serviceName } ?: run {
            _clients.add(_deviceFactory.createClient(
                deviceName = nsdServiceInfo.serviceName,
                deviceIpAddress = nsdServiceInfo.host.hostAddress ?: return
            ).also {
                log("new client created")
            })
        }
    }

    private fun runHostDevice() {
        runCatching {
            _listenRemoteDataJob?.cancel()
            _listenRemoteDataJob = coroutineScope.launch(dispatcher) {
                _hostDevice.listenRemoteData().collect {
                    log("HostDeviceImpl read data $it")
                    _incomingDataFlow.emit(it)
                }
            }
        }
    }

    private fun log(message: String) {
        logger.log("$TAG $message")
    }

    companion object {
        private const val TAG = "Communication->"
    }

}