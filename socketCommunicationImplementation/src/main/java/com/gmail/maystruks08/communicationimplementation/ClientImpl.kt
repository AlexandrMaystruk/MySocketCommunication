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
            throw RemoteError.CommunicationError.ReadFromClientSocketError(
                it.message ?: it.localizedMessage
            )
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun write(data: TransferData) {
        try {
            if (client.isOutputShutdown) throw RuntimeException("output stream shutdown")
            if (writer == null) throw RuntimeException("Writer is null")
            writer?.writeObject(data)
            writer?.flush()
            logger.log("write message finish")
        } catch (e: Exception) {
            logger.log("write message to device error ${e.localizedMessage};")
        }
    }

    override fun close() {
        try {
            if (!client.isInputShutdown) client.shutdownInput()
            if (!client.isOutputShutdown) client.shutdownOutput()
            if (!client.isClosed) client.close()
            logger.log("${client.inetAddress?.hostAddress} closed the connection")
        } catch (e: NullPointerException) {
            logger.log("closed the connection error, null pointer exception")
        } catch (e: Exception) {
            logger.log("closed the connection error")
        } catch (t: Throwable) {
            logger.log("closed the connection error ${t.localizedMessage} ")
        }
    }

}