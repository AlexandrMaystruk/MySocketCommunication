package com.gmail.maystruks08.remotecommunication.managers

import android.net.wifi.p2p.WifiP2pDevice

sealed class P2pControllerCommand {
    object DeviceConnected : P2pControllerCommand()
    object DeviceDisconnected : P2pControllerCommand()
    data class CurrentDeviceChanged(val wifiP2pDevice: WifiP2pDevice) : P2pControllerCommand()
}
