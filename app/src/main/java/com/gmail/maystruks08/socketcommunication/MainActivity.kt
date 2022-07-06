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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.gmail.maystruks08.communicationinterface.CommunicationLogger
import com.gmail.maystruks08.communicationinterface.entity.TransferData
import com.gmail.maystruks08.remotecommunication.CommunicationManagerImpl
import com.gmail.maystruks08.socketcommunication.ui.theme.SocketCommunicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    private val logsList = mutableStateListOf<String>()
    var messageCount = 0

    private val communicationManager: CommunicationManagerImpl by lazy {
        CommunicationManagerImpl(
            applicationContext,
            lifecycleScope,
            Dispatchers.IO,
            object : CommunicationLogger {
                override fun log(message: String) {
                    Log.d("Logger", message)
                    logsList.add(message)
                }

                override fun logError(exception: Exception, message: String) {
                    Log.e("CommunicationLogger", message, exception)
                    logsList.add(message)
                }
            })
    }

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
                            communicationManager.observeBroadcast().collect {
                                broadcastData.value = it.toString()
                                isShownChangeOrderStateDialog.value = true
                            }
                        })
                        ShowRemoteMessageDialog(isShownChangeOrderStateDialog, broadcastData)
                        Column {
                            val isSender = remember { mutableStateOf(false) }
                            val isTest = remember { mutableStateOf(false) }
                            FeatureFlags(isSender, isTest)
                            ActionButtons(isSender, isTest)
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
        //show dialog
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

                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FeatureFlags(isSender: MutableState<Boolean>, isTest: MutableState<Boolean>) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(0.3f))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Is sender")
                Checkbox(
                    checked = isSender.value,
                    onCheckedChange = {
                        isSender.value = it
                        communicationManager.isSender = it
                    }
                )
            }
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Test only Client-Server communication")
                Checkbox(
                    checked = isTest.value,
                    onCheckedChange = {
                        isTest.value = it
                        communicationManager.isTest = it
                    }
                )
            }
            Spacer(modifier = Modifier.weight(0.3f))
        }
    }

    @Composable
    fun ActionButtons(isSender: MutableState<Boolean>, isTest: MutableState<Boolean>) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.onStart()
            }
        ) {
            Text("startWork P2p")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                lifecycleScope.launch(Dispatchers.IO) { communicationManager.discoverPeers() }
            }
        ) {
            Text("discoverPeers")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = ::getAllIpsInLocaleNetwork
        ) {
            Text("Get All Ips In Locale Network")
        }

        val buttonText = if (isTest.value && !isSender.value) {
            "Start server"
        } else {
            "Send data to remote device"
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = ::sendDataToRemoteDevices
        ) {
            Text(buttonText)
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.onStop()
            }
        ) {
            Text("stopWork P2p")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.nsdManagerOnStart()
            }
        ) {
            Text("nsdManagerOnStart")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.discoverNdsServices()
            }
        ) {
            Text("discoverNdsServices")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.registerDnsService()
            }
        ) {
            Text("registerDnsService")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.discoverDnsService()
            }
        ) {
            Text("discoverDnsService")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                communicationManager.nsdManagerOnStop()
            }
        ) {
            Text("nsdManagerOnStop")
        }
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

    private fun getAllIpsInLocaleNetwork() {
        lifecycleScope.launch(Dispatchers.IO) {
            communicationManager.getAllIpsInLocaleNetwork()
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
                Log.d("Logger", "sendDataToRemoteDevices error $it")
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