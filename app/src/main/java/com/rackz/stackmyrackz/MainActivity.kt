package com.rackz.stackmyrackz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rackz.stackmyrackz.ui.navigation.RacksApp
import com.rackz.stackmyrackz.ui.theme.RacksTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RacksTheme {
                RacksApp()
            }
        }
    }
}
