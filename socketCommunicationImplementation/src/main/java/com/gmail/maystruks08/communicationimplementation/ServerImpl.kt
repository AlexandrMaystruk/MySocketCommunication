package com.gmail.maystruks08.communicationimplementation

import com.gmail.maystruks08.communicationinterface.Client
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.Server
import com.gmail.maystruks08.communicationinterface.SocketFactory
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import java.net.ServerSocket

class ServerImpl(
    private val factory: SocketFactory,
    private val logger: CommunicationLogger
) : Server {

    private var serverSocket: ServerSocket? = null

    override fun readFromClients(onNewDataReceived: (TransferData) -> Unit) {
        runCatching {
            logger.log("Start creation server")
            val socket = factory.createServerSocket().also { serverSocket = it }
            while (!socket.isClosed){
                val client: Client = ClientImpl(
                    client = socket.accept(),
                    logger = logger
                )
                val dataFromClient = client.read()
                logger.log("Server received data: $dataFromClient")
                client.close()
                onNewDataReceived.invoke(dataFromClient)
            }
        }.getOrElse {
            it.printStackTrace()
            logger.log("Server error: ${it.message}")
            throw RuntimeException("Read from client error")
        }
    }

    override fun close() {
        if (serverSocket?.isClosed == false) {
            runCatching {
                serverSocket?.close()
            }.onFailure {
                logger.log("Close server socket error: ${it.message}")
            }
        }
    }
}
