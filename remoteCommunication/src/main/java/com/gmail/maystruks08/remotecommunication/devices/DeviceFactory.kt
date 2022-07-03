package com.gmail.maystruks08.remotecommunication.devices

import android.net.wifi.p2p.WifiP2pDevice

interface DeviceFactory {

    fun create(device: WifiP2pDevice): ClientDevice

}

