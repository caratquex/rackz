package com.rackz.stackmyrackz.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rackz.stackmyrackz.ui.dashboard.DashboardRoute
import com.rackz.stackmyrackz.ui.home.HomeRoute
import com.rackz.stackmyrackz.ui.sync.SyncBottomSheetRoute
import com.rackz.stackmyrackz.ui.theme.AppThemeMode
import com.rackz.stackmyrackz.ui.wealth.WealthRoute
import kotlinx.coroutines.launch

private enum class RacksDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(
        route = "home",
        label = "Home",
        icon = Icons.Rounded.Dialpad
    ),
    DASHBOARD(
        route = "dashboard",
        label = "Dashboard",
        icon = Icons.Rounded.BarChart
    ),
    WEALTH(
        route = "wealth",
        label = "Wealth",
        icon = Icons.Rounded.Savings
    )
}

@Composable
fun RacksApp(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    initialDestination: String? = null,
    onThemeModeSelected: (AppThemeMode) -> Unit = {},
    onDynamicColorChanged: (Boolean) -> Unit = {}
) {
    val startDestination = remember(initialDestination) {
        when (initialDestination) {
            RacksDestination.WEALTH.route -> RacksDestination.WEALTH.route
            RacksDestination.DASHBOARD.route -> RacksDestination.DASHBOARD.route
            else -> RacksDestination.HOME.route
        }
    }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isAppearanceOpen by remember { mutableStateOf(false) }
    var isSyncOpen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                RacksDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(RacksDestination.HOME.route) {
                HomeRoute(
                    onShowSnackbar = { message ->
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    onOpenSync = { isSyncOpen = true },
                    onOpenAppearance = { isAppearanceOpen = true }
                )
            }
            composable(RacksDestination.DASHBOARD.route) {
                DashboardRoute(
                    onOpenSync = { isSyncOpen = true },
                    onOpenAppearance = { isAppearanceOpen = true },
                    onShowSnackbar = { message, actionLabel, onAction ->
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = actionLabel,
                                duration = if (actionLabel != null) {
                                    SnackbarDuration.Long
                                } else {
                                    SnackbarDuration.Short
                                }
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onAction?.invoke()
                            }
                        }
                    }
                )
            }
            composable(RacksDestination.WEALTH.route) {
                WealthRoute(
                    onOpenSync = { isSyncOpen = true },
                    onOpenAppearance = { isAppearanceOpen = true },
                    onShowSnackbar = { message ->
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
        }
    }

    if (isAppearanceOpen) {
        AppearanceBottomSheet(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeModeSelected = onThemeModeSelected,
            onDynamicColorChanged = onDynamicColorChanged,
            onDismiss = { isAppearanceOpen = false }
        )
    }

    if (isSyncOpen) {
        SyncBottomSheetRoute(
            onDismiss = { isSyncOpen = false },
            onShowSnackbar = { message ->
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceBottomSheet(
    themeMode: AppThemeMode,
    dynamicColor: Boolean,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = themeMode == mode
                            val icon = when (mode) {
                                AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                                AppThemeMode.DARK -> Icons.Rounded.DarkMode
                            }
                            val container = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                            val contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .background(container)
                                    .clickable { onThemeModeSelected(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onDynamicColorChanged(!dynamicColor) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Material You",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChanged
                    )
                }
            }
        }
    }
}
