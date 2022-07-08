package com.gmail.maystruks08.remotecommunication

import android.net.nsd.NsdServiceInfo
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.controllers.NsdController
import com.gmail.maystruks08.remotecommunication.controllers.P2pController
import com.gmail.maystruks08.remotecommunication.controllers.commands.NsdControllerCommand
import com.gmail.maystruks08.remotecommunication.devices.ClientDevice
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactory
import com.gmail.maystruks08.remotecommunication.devices.DeviceFactoryImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CommunicationManagerImpl(config: Configuration) : CommunicationManager {

    private val _coroutineScope = config.app.coroutineScope
    private val _dispatcher = config.app.dispatcher
    private val logger = config.app.logger

    private var _isStarted = AtomicBoolean(false)
    private var _listenRemoteDataJob: Job? = null
    private var _discoverNdsServices: Job? = null

    private val _clients = mutableSetOf<ClientDevice>()
    private val _incomingDataFlow = MutableSharedFlow<TransferData>()
    private val _p2pManager by lazy {
        P2pController(config.app.context, config.app.logger)
    }
    private val _nsdManager by lazy {
        NsdController(config.app.context, config.app.appName, config.network.localePort, config.app.logger)
    }
    private val _deviceFactory: DeviceFactory by lazy {
        DeviceFactoryImpl(config)
    }
    private val _hostDevice by lazy {
        _deviceFactory.createHost()
    }

    override fun onStart() {
        if (_isStarted.get()) {
            log("component already onStart()")
            return
        }
        log("onStart")
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
            _coroutineScope.launch(_dispatcher) {
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
            log("component already onStop()")
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
        _discoverNdsServices = _coroutineScope.launch(_dispatcher) {
            _nsdManager.discoverNdsServices().collect {
                when (it) {
                    is NsdControllerCommand.NewServiceConnected -> {
                        handleNewServiceConnection(it.nsdServiceInfo)
                    }
                    is NsdControllerCommand.ServiceDisconnected -> {
                        handleServiceDisconnected(it.nsdServiceInfo)
                    }
                    is NsdControllerCommand.ServiceDiscoveryFinished -> {
                        _discoverNdsServices?.cancel()
                    }
                }
            }
        }
    }

    private fun handleNewServiceConnection(nsdServiceInfo: NsdServiceInfo) {
        log("start handle new client")
        _clients.find { it.ipAddress == nsdServiceInfo.serviceName } ?: run {
            val newDeviceHostAddress = nsdServiceInfo.host.hostAddress ?: return
            val newDevicePort = nsdServiceInfo.port
            if (newDeviceHostAddress == _hostDevice.ipAddress) return
            _clients.add(_deviceFactory.createClient(
                deviceIpAddress = newDeviceHostAddress,
                port = newDevicePort
            ).also {
                log("new client created")
            })
        }
    }

    private fun handleServiceDisconnected(nsdServiceInfo: NsdServiceInfo) {
        _clients.removeIf { it.ipAddress == nsdServiceInfo.serviceName }
    }

    private fun runHostDevice() {
        runCatching {
            _listenRemoteDataJob?.cancel()
            _listenRemoteDataJob = _coroutineScope.launch(_dispatcher) {
                _hostDevice.listenRemoteData().collect {
                    log("host device received data: $it")
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