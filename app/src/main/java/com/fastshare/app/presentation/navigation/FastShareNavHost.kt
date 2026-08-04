package com.fastshare.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fastshare.app.presentation.screens.HistoryScreen
import com.fastshare.app.presentation.screens.HomeScreen
import com.fastshare.app.presentation.screens.ManualConnectScreen
import com.fastshare.app.presentation.screens.QrScanScreen
import com.fastshare.app.presentation.screens.QrShowScreen
import com.fastshare.app.presentation.screens.ReceiveScreen
import com.fastshare.app.presentation.screens.SendScreen
import com.fastshare.app.presentation.screens.SendTextScreen
import com.fastshare.app.presentation.screens.SettingsScreen
import com.fastshare.app.presentation.screens.TransfersScreen
import com.fastshare.app.presentation.screens.TrustedDevicesScreen

@Composable
fun FastShareNavHost() {
    val navController = rememberNavController()
    // simple layout: each composable is self-contained; destinations are top-level routes.
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(
                onSend = { navController.navigate(Destination.Send.route) },
                onReceive = { navController.navigate(Destination.Receive.route) },
                onScanQr = { navController.navigate(Destination.QrScan.route) },
                onShowQr = { navController.navigate(Destination.QrShow.route) },
                onManualConnect = { navController.navigate(Destination.ManualConnect.route) },
                onSendText = { navController.navigate(Destination.SendText.route) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
                onOpenTrusted = { navController.navigate(Destination.TrustedDevices.route) },
            )
        }
        composable(Destination.Send.route) { SendScreen(onClose = { navController.popBackStack() }) }
        composable(Destination.Receive.route) { ReceiveScreen(onClose = { navController.popBackStack() }) }
        composable(Destination.History.route) { HistoryScreen() }
        composable(Destination.Settings.route) { SettingsScreen() }
        composable(Destination.Transfers.route) { TransfersScreen() }
        composable(Destination.QrScan.route) { QrScanScreen(onClose = { navController.popBackStack() }) }
        composable(Destination.QrShow.route) { QrShowScreen(onClose = { navController.popBackStack() }) }
        composable(Destination.ManualConnect.route) { ManualConnectScreen(onClose = { navController.popBackStack() }) }
        composable(Destination.SendText.route) { SendTextScreen(onClose = { navController.popBackStack() }) }
        composable(Destination.TrustedDevices.route) { TrustedDevicesScreen(onClose = { navController.popBackStack() }) }
    }
}
