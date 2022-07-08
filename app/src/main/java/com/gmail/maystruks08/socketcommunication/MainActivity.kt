package com.gmail.maystruks08.socketcommunication

import android.Manifest.permission
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.CommunicationManager
import com.gmail.maystruks08.remotecommunication.CommunicationManagerImpl
import com.gmail.maystruks08.remotecommunication.Configuration
import com.gmail.maystruks08.remotecommunication.getLocalIpAddress
import com.gmail.maystruks08.socketcommunication.ui.theme.SocketCommunicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.ServerSocket

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    private val communicationManager by lazy { createCommunicationManager() }
    private val logsList = mutableStateListOf<String>()
    private var messageCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SocketCommunicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RequestPermissions {
                        val isShownChangeOrderStateDialog = remember { mutableStateOf(false) }
                        val broadcastData = remember { mutableStateOf("") }
                        LaunchedEffect(key1 = communicationManager, block = {
                            communicationManager.getRemoteClientsTransferDataFlow().collect {
                                broadcastData.value = it.toString()
                                isShownChangeOrderStateDialog.value = true
                            }
                        })
                        ShowRemoteMessageDialog(isShownChangeOrderStateDialog, broadcastData)
                        Column {
                            Spacer(modifier = Modifier.padding(20.dp))
                            ActionButtons()
                            LogsList(logsList)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ShowRemoteMessageDialog(
        showDialog: MutableState<Boolean>,
        broadcastData: MutableState<String>
    ) {
        if (showDialog.value) {
            AlertDialog(
                title = {
                    Text(text = "Received new message ")
                },
                text = {
                    Text(text = broadcastData.value)
                },
                onDismissRequest = { showDialog.value = false },
                confirmButton = {

                },
                dismissButton = {
                    Button(onClick = {
                        showDialog.value = false
                    }) {
                        Text("Close")
                    }
                }
            )
        }
    }

    @Composable
    fun ActionButtons() {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = {
                communicationManager.onStart()
            }
        ) {
            Text("onStart")
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = {
                communicationManager.sendToRemoteClients(
                    TransferData(
                        messageCode = 200,
                        data = "I hope the message: $messageCount is not lost"
                    )
                )
                messageCount++
            }
        ) {
            Text("Send data")
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = {
                communicationManager.onStop()
            }
        ) {
            Text("onStop")
        }

        Spacer(modifier = Modifier.padding(20.dp))
    }

    @Composable
    fun RequestPermissions(onPermissionsGranted: @Composable () -> Unit) {
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
                "Coarse Location permission Granted"
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
                "Fine Location permission Grante"
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
                "Change WiFi State permission Granted"
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
                "access WiFi state permission Granted"
            )
            accessWiFiStatePermissionState.shouldShowRationale || !accessWiFiStatePermissionState.permissionRequested -> {
                LaunchedEffect(key1 = Unit, block = {
                    accessWiFiStatePermissionState.launchPermissionRequest()
                })
            }
        }

        if (coarseLocationPermissionState.hasPermission &&
            fineLocationPermissionState.hasPermission &&
            changeWiFiStatePermissionState.hasPermission &&
            accessWiFiStatePermissionState.hasPermission
        ) {
            onPermissionsGranted.invoke()
        }
    }

    @Composable
    fun LogsList(items: SnapshotStateList<String>) {
        val listState = rememberLazyListState()
        LaunchedEffect(items.size) {
            listState.scrollToItem(items.lastIndex)
        }

        LazyColumn(state = rememberLazyListState()) {
            items(items.size) { index ->
                val textStyle = remember {
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(text = items[index], style = textStyle)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        communicationManager.onStop()
    }

    private fun createCommunicationManager(): CommunicationManager {
        val communicationLogger = object : CommunicationLogger {
            override fun log(message: String) {
                Log.d("Logger", message)
                logsList.add(message)
            }

            override fun logError(exception: Exception, message: String) {
                Log.e("Logger", message, exception)
                logsList.add(message)
            }
        }
        val ip = getLocalIpAddress(communicationLogger).orEmpty()
        var freePort: Int
        ServerSocket(0).use { freePort = it.localPort }
        val config = object : Configuration {
            override val app = object : Configuration.App {
                override val appName = "private_nsd_service"
                override val context = applicationContext
                override val coroutineScope: CoroutineScope = lifecycleScope
                override val dispatcher = Dispatchers.IO
                override val logger = communicationLogger
            }
            override val network = object : Configuration.Network {
                override val localeIdAddress = ip
                override val localePort = freePort
            }
        }
        return CommunicationManagerImpl(config)
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