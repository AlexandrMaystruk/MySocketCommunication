package com.gmail.maystruks08.remotecommunication.devices

import com.gmail.maystruks08.communicationimplementation.ServerImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.Server
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.Closeable

class HostDeviceImpl(
    private val dispatcher: CoroutineDispatcher,
    private val logger: CommunicationLogger,
    private val socketFactory: SocketFactory
) : HostDevice, Closeable {

    private var server: Server? = null

    override fun listenRemoteData(): Flow<TransferData> {
        val server = ServerImpl(socketFactory, logger).also { server = it }
        return callbackFlow {
            runCatching {
                server.readFromClients { transferData ->
                    logger.log("$TAG readFromClients received data in callback")
                    trySendBlocking(transferData)
                        .onSuccess {
                            logger.log("$TAG trySendBlocking success")
                        }
                        .onFailure {
                            logger.log("$TAG trySendBlocking failure ${it?.localizedMessage}")
                            server.close()
                        }.onClosed {
                            logger.log("$TAG trySendBlocking closed flow ${it?.localizedMessage}")
                            logger.log("$TAG ")
                        }
                }
            }.getOrElse {
                logger.log("$TAG callbackFlow failure, cancel will call")
                cancel(cause = CancellationException("readFromClients error: ${it.localizedMessage}"))
            }

            awaitClose { server.close() }
        }.flowOn(dispatcher)
    }

    override fun close() {
        server?.close()
        server = null
    }

    companion object{
        private const val TAG = "HostDeviceImpl"
    }

}
