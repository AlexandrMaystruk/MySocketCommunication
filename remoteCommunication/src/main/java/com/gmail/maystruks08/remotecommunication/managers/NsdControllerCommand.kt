package com.gmail.maystruks08.remotecommunication.managers

import android.net.nsd.NsdServiceInfo

sealed class NsdControllerCommand {
    data class NewServiceConnected(val nsdServiceInfo: NsdServiceInfo) : NsdControllerCommand()
    object ServiceDiscoveryFinished : NsdControllerCommand()

}