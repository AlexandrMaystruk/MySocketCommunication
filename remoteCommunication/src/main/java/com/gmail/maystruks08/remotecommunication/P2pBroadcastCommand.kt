package com.gmail.maystruks08.remotecommunication

import android.net.wifi.p2p.WifiP2pDevice

sealed class P2pBroadcastCommand {
    object DeviceConnected : P2pBroadcastCommand()
    object DeviceDisconnected : P2pBroadcastCommand()
    data class CurrentDeviceChanged(val wifiP2pDevice: WifiP2pDevice) : P2pBroadcastCommand()
}