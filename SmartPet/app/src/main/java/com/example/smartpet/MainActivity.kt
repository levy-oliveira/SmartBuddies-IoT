package com.example.smartpet

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.smartpet.ui.SmartPetDashboard
import com.example.smartpet.viewmodel.SmartPetViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<SmartPetViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        viewModel.initBluetooth(btManager.adapter)

        requestPermissions()

        setContent {
            SmartPetDashboard(viewModel = viewModel)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refreshPairedDevices()
        }
        launcher.launch(permissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPairedDevices()
    }
}