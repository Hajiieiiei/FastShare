package com.fastshare.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastshare.app.presentation.navigation.FastShareNavHost
import com.fastshare.app.presentation.theme.FastShareTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ShellViewModel = hiltViewModel()
            val settings = vm.settings.collectAsStateWithLifecycle(initialValue = null)
            FastShareTheme(
                themeMode = settings?.themeMode ?: com.fastshare.app.domain.model.ThemeMode.SYSTEM,
                dynamicColor = settings?.dynamicColor ?: true,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FastShareNavHost()
                }
            }
        }
    }
}
