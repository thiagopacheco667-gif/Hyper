package com.hyper.island.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hyper.island.data.SettingsRepository
import com.hyper.island.ui.theme.HyperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsRepository(applicationContext)
        setContent {
            HyperTheme {
                SetupScreen(settings)
            }
        }
    }
}
