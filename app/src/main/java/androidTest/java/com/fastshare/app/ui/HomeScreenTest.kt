package com.fastshare.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fastshare.app.presentation.screens.HomeScreen
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `send and receive buttons are visible`() {
        rule.setContent {
            HomeScreen(
                onSend = {}, onReceive = {}, onScanQr = {}, onShowQr = {},
                onManualConnect = {}, onSendText = {}, onOpenSettings = {}, onOpenTrusted = {},
            )
        }
        rule.onNodeWithText("Send Files").assertIsDisplayed()
        rule.onNodeWithText("Receive Files").assertIsDisplayed()
    }
}
