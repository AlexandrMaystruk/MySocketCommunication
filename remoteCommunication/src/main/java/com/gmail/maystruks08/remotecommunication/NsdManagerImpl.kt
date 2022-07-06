package com.gmail.maystruks08.remotecommunication

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.gmail.maystruks08.communicationimplementation.ServerImpl.Companion.LOCAL_SERVER_PORT
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("MissingPermission")
class NsdManagerImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: CommunicationLogger
) {

    private val _nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var _serviceInfo: NsdServiceInfo? = null
    private var _serviceName: String? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val services = mutableListOf<String>()

    fun onStart() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = LOCAL_SERVER_PORT
        }
        _nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun onStop() {
        _nsdManager.apply {
            unregisterService(registrationListener)
            stopServiceDiscovery(discoveryListener)
        }
    }

    fun discoverNdsServices(): Flow<NsdServiceCommand> {
        return callbackFlow {
            _nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {
                        logger.log("$TAG Service discovery started")
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        logger.log("$TAG onServiceFound ${service.serviceName}")
                        when {
                            service.serviceType != "$SERVICE_TYPE." -> { //  service.serviceType always return with dot at the end
                                logger.log("$TAG Unknown Service Type: ${service.serviceType}")
                            }
                            service.serviceName == _serviceName -> {
                                logger.log("$TAG Same machine: $_serviceName")
                            }
                            service.serviceName.contains(SERVICE_NAME) -> {
                                resolveServices(this@callbackFlow, service)
                            }
                        }
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        logger.log("$TAG service lost: $service")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        logger.log("$TAG Discovery stopped: $serviceType")
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        logger.log("$TAG Discovery failed: Error code:$errorCode")
                        _nsdManager.stopServiceDiscovery(this)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        logger.log("$TAG Discovery failed: Error code:$errorCode")
                        _nsdManager.stopServiceDiscovery(this)
                    }
                }.also { discoveryListener = it })

            awaitClose { logger.log("$TAG Flow<NsdServiceCommand> closed") }
        }
    }

    fun resolveServices(producerScope: ProducerScope<NsdServiceCommand>, service: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int
            ) {
                // Called when the resolve fails. Use the error code to debug.
                logger.log("$TAG Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                logger.log("$TAG Resolve Succeeded. $serviceInfo")
                if (serviceInfo.serviceName == _serviceName) {
                    logger.log("$TAG Same IP")
                    return
                }
                _serviceInfo = serviceInfo
                producerScope.trySendBlocking(NsdServiceCommand.ReceivedRemoteConnectionInfo(serviceInfo))
            }
        }

        _nsdManager.resolveService(service, resolveListener)
    }


    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            _serviceName = nsdServiceInfo.serviceName
            logger.log("$TAG onServiceRegistered ${nsdServiceInfo.serviceName}")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            logger.log("$TAG Registration failed ${serviceInfo.serviceName} $errorCode")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            logger.log("$TAG onServiceUnregistered ${arg0.serviceName}")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            logger.log("$TAG Unregistration failed: $errorCode")
        }
    }

    companion object {
        private const val TAG = "Nsd->"
        const val SERVICE_TYPE = "_nsdchat._tcp"
        const val SERVICE_NAME = "NsdChat"
    }

}