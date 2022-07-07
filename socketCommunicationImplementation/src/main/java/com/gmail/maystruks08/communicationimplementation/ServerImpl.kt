package com.gmail.maystruks08.communicationimplementation

import com.gmail.maystruks08.communicationinterface.Client
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.Server
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.ServerSocket

class ServerImpl(
    private val factory: SocketFactory,
    private val logger: CommunicationLogger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScope: CoroutineScope
) : Server {

    private var serverSocket: ServerSocket? = null
    private val mutex = Mutex()
    private val clients = mutableListOf<Client>()

    override fun readFromClients(onNewDataReceived: (TransferData) -> Unit) {
        runCatching {
            logger.log("Start creation server on port $LOCAL_SERVER_PORT")
            val socket = factory.createServerSocket(LOCAL_SERVER_PORT).also {
                serverSocket = it
            }
            while (!socket.isClosed) {
                val client: Client = ClientImpl(
                    client = socket.accept(),
                    logger = logger
                )
                handleClient(client, onNewDataReceived)
            }
        }.getOrElse {
            logger.log("Server error: ${it.message}")
            throw RuntimeException("Server error")
        }
    }

    override fun close() {
        if (serverSocket?.isClosed == false) {
            runCatching {
                serverSocket?.close()
                coroutineScope.launch(dispatcher) {
                    mutex.withLock {
                        clients.forEach { it.close() }
                    }
                }
            }.onFailure {
                logger.log("Close server socket error: ${it.message}")
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun handleClient(client: Client, onNewDataReceived: (TransferData) -> Unit) {
        coroutineScope.launch(dispatcher) {
            mutex.withLock { clients.add(client) }
            try {
                val dataFromClient = client.read()
                logger.log("Server received data: $dataFromClient")
                onNewDataReceived.invoke(dataFromClient)
            } catch (remoteError: RemoteError.CommunicationError.ReadFromClientSocketError) {
                val failureData = TransferData(0, null, remoteError)
                onNewDataReceived.invoke(failureData)
            } finally {
                client.close()
            }

            mutex.withLock { clients.remove(client) }
        }
    }

    companion object{
        const val LOCAL_SERVER_PORT = 8080
    }
}
