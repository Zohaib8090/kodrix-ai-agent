package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.AppNavigation
import com.example.ui.navigation.Routes
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDest = Routes.DASHBOARD

        setContent {
            val uiState = settingsViewModel.state.collectAsStateWithLifecycle().value
            val systemDark = isSystemInDarkTheme()
            val isDark = when (uiState.themeMode.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            MyApplicationTheme(
                darkTheme = isDark,
                accentColor = uiState.accentColor,
                dynamicColor = false
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNavigation(startDestination = startDest)
                }
            }
        }
    }
}
