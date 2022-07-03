package com.gmail.maystruks08.socketcommunication

import android.Manifest.permission
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.CommunicationManagerImpl
import com.gmail.maystruks08.socketcommunication.ui.theme.SocketCommunicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    val stringBuilder = StringBuilder()
    val state = MutableStateFlow(stringBuilder.toString())

    var messageCount = 0

    private val communicationManager: CommunicationManagerImpl by lazy {
        CommunicationManagerImpl(
            this.applicationContext,
            Dispatchers.IO,
            lifecycle,
            object : CommunicationLogger {
                override fun log(message: String) {
                    Log.d("CommunicationLogger", message)
                    stringBuilder.appendLine(message)
                    lifecycleScope.launch {
                        state.value = stringBuilder.toString()
                    }
                }

                override fun logError(exception: Exception, message: String) {
                    Log.e("CommunicationLogger", message, exception)
                    stringBuilder.appendLine(message)
                    lifecycleScope.launch {
                        state.value = stringBuilder.toString()
                    }
                }
            })
    }

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SocketCommunicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
//                    val logs = state.collectAsState().value

                    val changeWiFiStatePermissionState =
                        rememberPermissionState(permission.CHANGE_WIFI_STATE)
                    val accessWiFiStatePermissionState =
                        rememberPermissionState(permission.ACCESS_WIFI_STATE)
                    val coarseLocationPermissionState =
                        rememberPermissionState(permission.ACCESS_COARSE_LOCATION)
                    val fineLocationPermissionState =
                        rememberPermissionState(permission.ACCESS_FINE_LOCATION)

                    when {
                        coarseLocationPermissionState.hasPermission -> Log.d(
                            "TAGG",
                            "Call permission Granted coarseLocationPermissionState"
                        )
                        coarseLocationPermissionState.shouldShowRationale || !coarseLocationPermissionState.permissionRequested -> {
                            LaunchedEffect(key1 = Unit, block = {
                                coarseLocationPermissionState.launchPermissionRequest()
                            })
                        }
                    }

                    when {
                        fineLocationPermissionState.hasPermission -> Log.d(
                            "TAGG",
                            "Call permission Granted fineLocationPermissionState"
                        )
                        fineLocationPermissionState.shouldShowRationale || !fineLocationPermissionState.permissionRequested -> {
                            LaunchedEffect(key1 = Unit, block = {
                                fineLocationPermissionState.launchPermissionRequest()
                            })
                        }
                    }

                    when {
                        changeWiFiStatePermissionState.hasPermission -> Log.d(
                            "TAGG",
                            "Call permission Granted changeWiFiStatePermissionState"
                        )
                        changeWiFiStatePermissionState.shouldShowRationale || !changeWiFiStatePermissionState.permissionRequested -> {
                            LaunchedEffect(key1 = Unit, block = {
                                changeWiFiStatePermissionState.launchPermissionRequest()
                            })
                        }
                    }

                    when {
                        accessWiFiStatePermissionState.hasPermission -> Log.d(
                            "TAGG",
                            "Call permission Granted accessWiFiStatePermissionState"
                        )
                        accessWiFiStatePermissionState.shouldShowRationale || !accessWiFiStatePermissionState.permissionRequested -> {
                            LaunchedEffect(key1 = Unit, block = {
                                accessWiFiStatePermissionState.launchPermissionRequest()
                            })
                        }
                    }

                    if (changeWiFiStatePermissionState.hasPermission && accessWiFiStatePermissionState.hasPermission) {
                        communicationManager.onResume()

                        val isShownChangeOrderStateDialog = remember { mutableStateOf(false) }
                        val broadcastData = remember { mutableStateOf("") }
                        LaunchedEffect(key1 = communicationManager, block = {
                            communicationManager.observeBroadcast().collect {
                                broadcastData.value = it.toString()
                                isShownChangeOrderStateDialog.value = true
                            }
                        })

                        //show dialog
                        if (isShownChangeOrderStateDialog.value) {
                            AlertDialog(
                                title = {
                                    Text(text = "Received new message ")
                                },
                                text = {
                                    Text(text = broadcastData.value)
                                },
                                onDismissRequest = { isShownChangeOrderStateDialog.value = false },
                                confirmButton = {

                                },
                                dismissButton = {

                                }
                            )
                        }

                        Column {
                            Button(onClick = ::getConnectedDevices) {
                                Text("Get connected devices")
                            }

                            Button(onClick = ::sendDataToRemoteDevices) {
                                Text("Send data to remote device")
                            }

                            Button(onClick = ::discoverPeers) {
                                Text("discoverPeers")
                            }
                            val isChecked = remember {
                                mutableStateOf(false)
                            }
                            Row {
                                Text("Is sender")
                                Checkbox(
                                    checked = isChecked.value,
                                    onCheckedChange = {
                                        isChecked.value = it
                                        communicationManager.setMode(it)
                                    }
                                )
                            }

                            val isTest = remember { mutableStateOf(false) }
                            Row {
                                Text("Test only Client-Server communication")
                                Checkbox(
                                    checked = isTest.value,
                                    onCheckedChange = {
                                        isTest.value = it
                                        communicationManager.isTest = it
                                    }
                                )
                            }


                            val scrollStateVertical = rememberScrollState()
                            Text(
                                text = stringBuilder.toString(),
                                modifier = Modifier.verticalScroll(state = scrollStateVertical)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        communicationManager.onPause()
    }

    private fun discoverPeers() {
        communicationManager.discoverPeers()
    }

    private fun getConnectedDevices() {
        lifecycleScope.launch(Dispatchers.IO) {
//            communicationManager.getConnectedDevices()
        }
    }

    private fun sendDataToRemoteDevices() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                communicationManager.sendBroadcast(
                    TransferData(
                        200,
                        "Broadcast message $messageCount to all devices"
                    )
                )
                messageCount++

            }.getOrElse {
                Log.d("CommunicationLogger", "sendDataToRemoteDevices error $it")
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SocketCommunicationTheme {
        Greeting("Android")
    }
}