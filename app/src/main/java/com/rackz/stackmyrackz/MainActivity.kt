package com.rackz.stackmyrackz

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.rackz.stackmyrackz.ui.navigation.RacksApp
import com.rackz.stackmyrackz.ui.theme.AppThemeMode
import com.rackz.stackmyrackz.ui.theme.RacksTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialThemeMode = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            ?.let { name -> AppThemeMode.entries.firstOrNull { it.name == name } }
            ?: AppThemeMode.SYSTEM
        val initialDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        val initialDestination = intent?.getStringExtra("open_destination")

        setContent {
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            var dynamicColor by remember { mutableStateOf(initialDynamicColor) }

            RacksTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                RacksApp(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    initialDestination = initialDestination,
                    onThemeModeSelected = { selected ->
                        themeMode = selected
                        prefs.edit { putString(KEY_THEME_MODE, selected.name) }
                    },
                    onDynamicColorChanged = { enabled ->
                        dynamicColor = enabled
                        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
                    }
                )
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "racks_appearance"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}
