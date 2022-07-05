package com.gmail.maystruks08.communicationimplementation

import com.gmail.maystruks08.communicationinterface.Client
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.RemoteError
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket


class ClientImpl(
    private val client: Socket,
    private val logger: CommunicationLogger
) : Client {

    private val reader: ObjectInputStream? by lazy {
        runCatching {
            ObjectInputStream(client.getInputStream())
        }.getOrElse {
            logger.log("can't get input stream")
            return@getOrElse null
        }
    }
    private val writer: ObjectOutputStream? by lazy {
        runCatching {
            ObjectOutputStream(client.getOutputStream())
        }.getOrElse {
            logger.log("can't get output stream")
            return@getOrElse null
        }
    }

    override fun read(): TransferData {
        return runCatching {
            reader?.readObject() as TransferData
        }.getOrElse {
            throw RemoteError.CommunicationError.ReadFromClientSocketError(it.message ?: it.localizedMessage)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun write(data: TransferData) {
        runCatching {
            if (client.isOutputShutdown) throw RuntimeException("output stream shutdown")
            if (writer == null) throw RuntimeException("writer is null")
            writer?.writeObject(data)
            writer?.flush()
            logger.log("write message finish")
        }.onFailure {
            throw RemoteError.CommunicationError.WriteToClientSocketError(it.localizedMessage)
        }
    }

    override fun close() {
        runCatching {
            if (!client.isInputShutdown) client.shutdownInput()
            if (!client.isOutputShutdown) client.shutdownOutput()
            if (!client.isClosed) client.close()
            logger.log("closed the connection to: ${client.inetAddress?.hostAddress}")
        }.onFailure {
            logger.log("closed the connection error ${it.localizedMessage}")
        }
    }

}