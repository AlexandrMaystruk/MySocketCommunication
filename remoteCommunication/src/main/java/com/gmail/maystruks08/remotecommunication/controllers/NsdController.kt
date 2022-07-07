package com.gmail.maystruks08.remotecommunication.controllers

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.gmail.maystruks08.communicationimplementation.ServerImpl.Companion.LOCAL_SERVER_PORT
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.remotecommunication.controllers.commands.NsdControllerCommand
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("MissingPermission")
class NsdController(
    private val context: Context,
    private val logger: CommunicationLogger
) {

    private val _nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var _registrationListener: NsdManager.RegistrationListener? = null
    private var _discoveryListener: NsdManager.DiscoveryListener? = null

    private var _currentServiceInfo: NsdServiceInfo? = null
    private val _connectedServices = mutableSetOf<NsdServiceInfo>()

    fun startWork() {
        log("startWork")
        _currentServiceInfo = null
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = LOCAL_SERVER_PORT
        }
        _nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                    _currentServiceInfo = nsdServiceInfo
                    log("onServiceRegistered ${nsdServiceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    _currentServiceInfo = null
                    log("Registration failed ${serviceInfo.serviceName} $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    _currentServiceInfo = null
                    log("onServiceUnregistered ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    log("unregistration failed: $errorCode")
                }
            }.also { _registrationListener = it })
    }

    fun stopWork() {
        _nsdManager.apply {
            _registrationListener?.let { unregisterService(it) }
            _discoveryListener?.let { stopServiceDiscovery(it) }
            _currentServiceInfo = null
            _registrationListener = null
            _discoveryListener = null
            _connectedServices.clear()
        }
        log("stopWork")
    }

    fun discoverNdsServices(): Flow<NsdControllerCommand> {
        return callbackFlow {
            _nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {
                        log("service discovery started")
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        when {
                            // service.serviceType always return with dot at the end
                            service.serviceType != "$SERVICE_TYPE." -> {
                                log("found unknown service type: ${service.serviceType}")
                            }
                            service.checkIsTheSameService() -> {
                                //log("found self service: ${service.serviceName}")
                            }
                            service.serviceName.contains(SERVICE_NAME) -> {
                                resolveServices(this@callbackFlow, service)
                            }
                        }
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        log("service lost: $service")
                        _connectedServices.remove(service)
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        log("discovery stopped: $serviceType")
                        trySendBlocking(NsdControllerCommand.ServiceDiscoveryFinished)
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        log("discovery failed: Error code:$errorCode")
                        _nsdManager.stopServiceDiscovery(this)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        log("discovery failed: Error code:$errorCode")
                        _nsdManager.stopServiceDiscovery(this)
                    }
                }.also { _discoveryListener = it })

            awaitClose { log("discover nds services flow closed") }
        }
    }

    private fun resolveServices(
        producerScope: ProducerScope<NsdControllerCommand>,
        service: NsdServiceInfo
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (!serviceInfo.checkIsTheSameService()) {
                    log("resolve ${serviceInfo.toLog()} failed: $errorCode")
                    return
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.checkIsTheSameService()) {
                    log("resolved same current device")
                    return
                }
                log("resolve succeeded: ${serviceInfo.toLog()}")
                _connectedServices.add(serviceInfo)
                producerScope.trySendBlocking(NsdControllerCommand.NewServiceConnected(serviceInfo))
            }
        }

        _nsdManager.resolveService(service, resolveListener)
    }

    private fun NsdServiceInfo.checkIsTheSameService(): Boolean{
        return serviceName == _currentServiceInfo?.serviceName
    }

    private fun NsdServiceInfo.toLog(): String{
        return "$serviceName : ${host?.hostAddress.orEmpty()}"
    }

    private fun log(message: String) {
        logger.log("$TAG $message")
    }

    companion object {
        private const val TAG = "Nsd->"
        internal const val SERVICE_TYPE = "_nsdchat._tcp"
        internal const val SERVICE_NAME = "private_nsd_service"
    }

}