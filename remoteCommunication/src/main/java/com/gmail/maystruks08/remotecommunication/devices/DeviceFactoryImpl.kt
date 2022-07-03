package com.gmail.maystruks08.remotecommunication.devices

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import com.gmail.maystruks08.communicationimplementation.SocketFactoryImpl
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.SocketFactory
import kotlinx.coroutines.CoroutineDispatcher

class DeviceFactoryImpl(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val dispatcher: CoroutineDispatcher,
    private val logger: CommunicationLogger
) : DeviceFactory {

    private val socketFactory: SocketFactory by lazy {
        SocketFactoryImpl(logger)
    }

    @SuppressLint("MissingPermission")
    override fun create(device: WifiP2pDevice): ClientDevice {
        return ClientDeviceImpl(
            deviceMacAddress = device.deviceAddress,
            wifiP2pManager = wifiP2pManager,
            channel = channel,
            socketFactory = socketFactory,
            dispatcher = dispatcher,
            logger = logger,
            context = context
        )
    }

}
