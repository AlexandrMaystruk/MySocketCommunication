package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationimplementation.ServerImpl
import com.gmail.maystruks08.communicationinterface.Server
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.Configuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.Closeable

class HostDeviceImpl(
    private val configuration: Configuration,
    private val socketFactory: SocketFactory,
) : HostDevice, Closeable {
    private val logger = configuration.app.logger

    private var server: Server? = null

    override val ipAddress: String
        get() = socketFactory.localeIpAddress

    override fun listenRemoteData(): Flow<TransferData> {
        close()
        val server = ServerImpl(
            serverPort = configuration.network.localePort,
            factory = socketFactory,
            dispatcher = configuration.app.dispatcher,
            coroutineScope = configuration.app.coroutineScope,
            logger = logger,
        ).also { server = it }
        return callbackFlow {
            runCatching {
                server.readFromClients { transferData ->
                    trySend(transferData).onFailure {
                        logger.log("$TAG emit client data failure: ${it?.localizedMessage}")
                        server.close()
                    }
                }
            }.getOrElse {
                logger.log("$TAG callbackFlow failure, cancel will call")
                cancel(cause = CancellationException("read from clients error: ${it.localizedMessage}"))
            }
            awaitClose { server.close() }
        }.flowOn(configuration.app.dispatcher)
    }

    override fun close() {
        server?.close()
        server = null
    }

    companion object{
        private const val TAG = "HostDeviceImpl"
    }

}
