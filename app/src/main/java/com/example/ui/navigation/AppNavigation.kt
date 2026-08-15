package com.example.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ui.analyzer.UnifiedAnalyzerScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.analyzer.ROMAnalyzerScreen
import com.example.ui.analyzer.BootAnalyzerScreen
import com.example.ui.logs.LogAnalyzerScreen
import com.example.ui.compare.ROMCompareScreen
import com.example.ui.compare.CompatibilityCheckScreen
import com.example.ui.builder.ROMBuilderScreen
import com.example.ui.device.DeviceInfoScreen
import com.example.ui.projects.ProjectsScreen
import com.example.ui.tools.ToolsScreen
import com.example.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    object Tools : Screen("tools", "Tools", Icons.Filled.Build)
    object Logs : Screen("log_analyzer", "Logs", Icons.Filled.Code)
    object Projects : Screen("projects", "Projects", Icons.Filled.Folder)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Tools,
    Screen.Logs,
    Screen.Projects,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            // Show bottom bar only on top-level destinations
            if (bottomNavItems.any { it.route == currentDestination?.route }) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Tools.route) { ToolsScreen(navController) }
            composable("rom_analyzer") { ROMAnalyzerScreen(navController) }
            composable("boot_analyzer") { BootAnalyzerScreen(navController) }
            composable(Screen.Logs.route) { LogAnalyzerScreen(navController) }
            composable("rom_compare") { ROMCompareScreen(navController) }
            composable("compatibility_check") { CompatibilityCheckScreen(navController) }
            composable("rom_builder") { ROMBuilderScreen(navController) }
            composable("device_info") { DeviceInfoScreen(navController) }
            composable("hash_calculator") { com.example.ui.tools.HashCalculatorScreen(navController) }
            composable("buildprop_analyzer") { com.example.ui.analyzer.BuildPropAnalyzerScreen(navController) }
            composable("apk_inspector") { com.example.ui.analyzer.ApkInspectorScreen(navController) }
            composable("init_analyzer") { com.example.ui.analyzer.InitScriptAnalyzerScreen(navController) }
            composable("fstab_analyzer") { com.example.ui.analyzer.FstabAnalyzerScreen(navController) }
            composable("file_explorer") { com.example.ui.explorer.FileExplorerScreen(navController) }
            composable("report_generator") { com.example.ui.tools.ReportGeneratorScreen(navController) }
            
            composable(
                route = "unified_analyzer/{toolType}",
                arguments = listOf(navArgument("toolType") { type = NavType.StringType })
            ) { backStackEntry ->
                val toolType = backStackEntry.arguments?.getString("toolType") ?: "unknown"
                UnifiedAnalyzerScreen(navController, toolType)
            }
            
            composable(Screen.Projects.route) { ProjectsScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
        }
    }
}
