package com.gmail.maystruks08.communicationinterface

interface CommunicationLogger {
    fun log(message: String)
    fun logError(exception: Exception, message: String)
}