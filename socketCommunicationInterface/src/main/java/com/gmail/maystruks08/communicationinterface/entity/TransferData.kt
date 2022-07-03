package com.gmail.maystruks08.communicationinterface.entity

import java.io.Serializable

data class TransferData(
    val messageCode: Int,
    val data: String?,
    val error: RemoteError? = null
): Serializable
