package com.gmail.maystruks08.remotecommunication.controllers.commands

import android.net.nsd.NsdServiceInfo

sealed class NsdControllerCommand {
    data class NewServiceConnected(val nsdServiceInfo: NsdServiceInfo) : NsdControllerCommand()
    data class ServiceDisconnected(val nsdServiceInfo: NsdServiceInfo) : NsdControllerCommand()
    object ServiceDiscoveryFinished : NsdControllerCommand()

}