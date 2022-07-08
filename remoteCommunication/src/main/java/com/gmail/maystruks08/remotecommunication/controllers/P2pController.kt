package com.gmail.maystruks08.remotecommunication.controllers

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.gmail.maystruks08.communicationinterface.CommunicationLogger

class P2pController(
    private val context: Context,
    private val logger: CommunicationLogger
) {
    private val _wifiP2pManager by lazy { context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager }
    private var _channel: WifiP2pManager.Channel? = null
    private val _channelListener =
        WifiP2pManager.ChannelListener { logger.log("$TAG channel disconnected") }

    fun startWork() {
        logger.log("$TAG start work")
        _channel = _wifiP2pManager.initialize(context, Looper.getMainLooper(), _channelListener)
    }

    fun stopWork() {
        _channel = null
        logger.log("$TAG stop work")
    }

    companion object {
        private const val TAG = "P2p->"
    }
}
