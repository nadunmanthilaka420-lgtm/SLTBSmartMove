package com.example.sltbsmartmove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.sltbsmartmove.ui.theme.SLTBSmartMoveTheme
import com.example.sltbsmartmove.uis.SmartMoveApp
import com.example.sltbsmartmove.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    // AuthViewModel is scoped to the Activity lifecycle so auth state persists
    // through configuration changes (rotation, theme switch, etc.)
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SLTBSmartMoveTheme {
                SmartMoveApp(authViewModel = authViewModel)
            }
        }
    }
}
