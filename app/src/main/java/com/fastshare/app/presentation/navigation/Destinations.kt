package com.fastshare.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Transfers : Destination("transfers")
    data object History : Destination("history")
    data object Settings : Destination("settings")
    data object Send : Destination("send")
    data object Receive : Destination("receive")
    data object QrScan : Destination("qr_scan")
    data object QrShow : Destination("qr_show")
    data object ManualConnect : Destination("manual_connect")
    data object SendText : Destination("send_text")
    data object TrustedDevices : Destination("trusted_devices")

    data class TransferDetail(val id: String) : Destination("transfer/$id") {
        companion object {
            const val ROUTE = "transfer/{transferId}"
            const val ARG = "transferId"
            fun build(id: String) = "transfer/$id"
        }
    }
}

data class BottomNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Destination.Home, "Home", Icons.Outlined.Home),
    BottomNavItem(Destination.Transfers, "Transfers", Icons.Outlined.SwapVert),
    BottomNavItem(Destination.History, "History", Icons.Outlined.History),
    BottomNavItem(Destination.Settings, "Settings", Icons.Outlined.Settings),
)
