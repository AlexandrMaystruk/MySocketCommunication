package com.gmail.maystruks08.communicationinterface.entity

data class SocketConfiguration(
    val ipAddress: String,
    val inputPort: Int,
    val connectTimeout: Int,
    val readWriteTimeout: Int
)