package com.example.smartpet

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartpet.ui.HistoryScreen
import com.example.smartpet.ui.PetScreen
import com.example.smartpet.ui.SmartPetDashboard
import com.example.smartpet.viewmodel.SmartPetViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SmartPetViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)).get(SmartPetViewModel::class.java)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        viewModel.initBluetooth(btManager.adapter)

        requestPermissions()

        setContent {
            AppNavigation(viewModel)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
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

@Composable
fun AppNavigation(viewModel: SmartPetViewModel) {
    val navController = rememberNavController()
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val items = listOf(
                    Screen.Home to Icons.Default.Home,
                    Screen.Pet to Icons.Default.Pets,
                    Screen.History to Icons.Default.DateRange
                )

                items.forEach { (screen, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(screen.route) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) { SmartPetDashboard(viewModel = viewModel) { selectedDeviceAddress = it } }
            composable(Screen.Pet.route) { PetScreen(viewModel = viewModel, selectedDeviceAddress = selectedDeviceAddress) }
            composable(Screen.History.route) { HistoryScreen() }
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("Início")
    object Pet : Screen("Pet")
    object History : Screen("Histórico")
}