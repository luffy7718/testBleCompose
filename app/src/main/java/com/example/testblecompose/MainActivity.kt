package com.example.testblecompose

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.testblecompose.ui.theme.TestBleComposeTheme
import java.util.*

private const val REQUEST_ENABLE_BLUETOOTH = 1
private const val REQUEST_LOCATION_PERMISSION = 2
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {

        } else {

        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestBleComposeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {

                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                    }
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH)
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADMIN)
                    DeviceListScreen(this@MainActivity)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview(context: Context = LocalContext.current) {
    TestBleComposeTheme {
        DeviceListScreen(context)
    }
}

@Composable
fun DeviceListScreen(context: Context) {
    // Etat Compose pour stocker la liste des appareils BLE détectés
    var devicesList by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    // Etat Compose pour stocker la valeur de la characteristic
    var characteristicValue by remember { mutableStateOf<ByteArray?>(null) }
    // Etat Compose pour stocker la connexion GATT en cours
    var gattConnection by remember { mutableStateOf<BluetoothGatt?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        // Bouton pour scanner les appareils BLE
        Button(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = {
                scanForDevices(context) { newDevicesList ->
                    devicesList = devicesList + newDevicesList
                }
            }
        ) {
            Text("Scanner")
        }

        // Liste des appareils BLE détectés
        LazyColumn {
            items(devicesList) { device ->
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Text(
                        text = device.name ?: "Unnamed Device",
                        modifier = Modifier
                            .clickable {
                                connectToDevice(device, context)
                            }
                            .padding(8.dp)
                    )
                }

            }
        }

        // Affichage de la valeur de la characteristic
        Text(
            text = "Characteristic Value: ${characteristicValue?.contentToString() ?: "N/A"}",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Bouton pour lire la valeur de la characteristic
        Button(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = {
              /*  readCharacteristic(gattConnection) { value ->
                   characteristicValue = value
                }*/
            }
        ) {
            Text("Read Characteristic")
        }
    }
}

/**

Fonction pour scanner les appareils BLE et mettre à jour la liste des appareils détectés.

@param context Le contexte de l'application Android.

@param onNewDevices La fonction à appeler lorsque de nouveaux appareils sont détectés.
 */
fun scanForDevices(context: Context, onNewDevices: (List<BluetoothDevice>) -> Unit) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    val handler = Handler(Looper.getMainLooper())
    val scanResults = mutableListOf<ScanResult>()

// Callback pour traiter les résultats du scan BLE
    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!scanResults.any { it.device == device }) {
                scanResults.add(result)
            }
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                val device = result.device
                if (!scanResults.any { it.device == device }) {
                    scanResults.add(result)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Do nothing
        }
    }

// Démarrer le scan BLE
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED) {
        bluetoothLeScanner.startScan(scanCallback)
// Arrêter le scan après 5 secondes
        handler.postDelayed({
            bluetoothLeScanner.stopScan(scanCallback)
            val devices = scanResults.mapNotNull { it.device }
            onNewDevices(devices)
        }, 5000)
    }
}

/**

Fonction pour se connecter à un appareil BLE et lire la valeur de la characteristic.

@param device L'appareil à connecter.

@param context Le contexte de l'application Android.

@param onCharacteristicRead La fonction à appeler lorsque la valeur de la characteristic est lue.
 */
fun connectToDevice(device: BluetoothDevice, context: Context) {
    val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt?.close()
                }
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val service = gatt?.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))
            val characteristic = service?.getCharacteristic(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"))
            characteristic?.let { readCharacteristic(it,gatt, context) }
        }
    }

    device.connectGatt(context, false, gattCallback)
}

/**

Fonction pour lire la valeur de la characteristic.
@param characteristic La characteristic à lire.
@param onCharacteristicRead La fonction à appeler lorsque la valeur est lue.
 */
fun readCharacteristic(characteristic: BluetoothGattCharacteristic,gattConnection:BluetoothGatt,context: Context) {
    // Vérifie que la connexion GATT est établie
    gattConnection.let { connection ->
        // Active la notification pour être notifié des mises à jour de la valeur de la characteristic
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        connection.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString(""))
        connection.writeDescriptor(descriptor)

        // Connecte le client à la characteristic pour lire sa valeur
        connection.readCharacteristic(characteristic)
        connection.setCharacteristicNotification(characteristic, true)
        connection.writeDescriptor(descriptor)
        connection.readRemoteRssi()

        // Attend quelques secondes avant de se déconnecter de la characteristic
        Handler(Looper.getMainLooper()).postDelayed({
            connection.setCharacteristicNotification(characteristic, false)
            connection.disconnect()
        }, 5000)
    }
}
