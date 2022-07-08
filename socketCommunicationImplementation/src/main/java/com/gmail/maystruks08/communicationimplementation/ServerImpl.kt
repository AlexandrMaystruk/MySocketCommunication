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
    private val serverPort: Int,
    private val factory: SocketFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScope: CoroutineScope,
    private val logger: CommunicationLogger
) : Server {

    private var serverSocket: ServerSocket? = null
    private val mutex = Mutex()
    private val clients = mutableListOf<Client>()

    override fun readFromClients(onNewDataReceived: (TransferData) -> Unit) {
        runCatching {
            log("starting ${factory.localeIpAddress}/$serverPort")
            val socket = factory.createServerSocket(serverPort).also { serverSocket = it }
            while (!socket.isClosed) {
                val client: Client = ClientImpl(
                    client = socket.accept(),
                    logger = logger
                )
                handleClient(client, onNewDataReceived)
            }
        }.getOrElse {
            log("handle clients error: ${it.message}")
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
                log("close socket error: ${it.message}")
            }
            log("finished")
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun handleClient(client: Client, onNewDataReceived: (TransferData) -> Unit) {
        coroutineScope.launch(dispatcher) {
            mutex.withLock { clients.add(client) }
            try {
                val dataFromClient = client.read()
                log("received data")
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

    private fun log(message: String) {
        logger.log("$TAG $message")
    }

    companion object {
        const val TAG = "Server->"
    }

}
