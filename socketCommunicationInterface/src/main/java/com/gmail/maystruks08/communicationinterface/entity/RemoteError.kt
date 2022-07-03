package com.gmail.maystruks08.communicationinterface.entity

/**
 * This sealed class used for transfer error data between the devices
 *
 * Exception doesn't use because for transfer data need to serialize the object to JSON and deserialize.
 * When cash register receive JSON response and try to deserialize object which extends Exception class
 * he always receives base class java.land.Exception, not child
 */
sealed class RemoteError : Exception() {

    sealed class ConnectionError(override val message: String = "") : RemoteError() {
        class ConnectToRemoteError(errorCode: String) : ConnectionError(errorCode)
        class CreateSocketError(override val message: String) : ConnectionError()
    }

    sealed class CommunicationError : RemoteError() {
        class ReadFromClientSocketError(override val message: String) : CommunicationError()

    }

    class UnknownRemoteError : RemoteError()

}
