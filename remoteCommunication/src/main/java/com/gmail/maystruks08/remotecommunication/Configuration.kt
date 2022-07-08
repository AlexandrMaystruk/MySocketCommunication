package com.gmail.maystruks08.remotecommunication

import android.content.Context
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

interface Configuration {

    val app: App
    val network: Network

    interface App {
        val appName: String
        val context: Context
        val coroutineScope: CoroutineScope
        val dispatcher: CoroutineDispatcher
        val logger: CommunicationLogger
    }

    interface Network {
        val localeIdAddress: String
        val localePort: Int
    }
}