package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.analyzer.ApkInspectorScreen
import com.example.ui.analyzer.BootAnalyzerScreen
import com.example.ui.analyzer.FstabAnalyzerScreen
import com.example.ui.analyzer.InitScriptAnalyzerScreen
import com.example.ui.analyzer.ROMAnalyzerScreen
import com.example.ui.analyzer.UnifiedAnalyzerScreen
import com.example.ui.analyzer.boot.ui.BootRomAnalyzerScreen
import com.example.ui.analyzer.dat.DatAnalyzerScreen
import com.example.ui.analyzer.elf.ui.ElfAnalyzerScreen
import com.example.ui.analyzer.flash.ui.FlashPrecheckScreen
import com.example.ui.analyzer.getprop.ui.GetpropAnalyzerScreen
import com.example.ui.analyzer.image.ui.ImageAnalyzerScreen
import com.example.ui.analyzer.kernel.studio.ui.KernelStudioScreen
import com.example.ui.analyzer.kernel.ui.KernelCrashAnalyzerScreen
import com.example.ui.analyzer.partition.ui.PartitionAnalyzerScreen
import com.example.ui.analyzer.selinux.ui.SelinuxAnalyzerScreen
import com.example.ui.analyzer.system.ui.FullSystemAnalyzerScreen
import com.example.ui.analyzer.vendor.ui.VendorHalRilScreen
import com.example.ui.compare.CompatibilityCheckScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.device.DeviceInfoScreen
import com.example.ui.diagnostic.BootDiagnosticScreen
import com.example.ui.explorer.FileExplorerScreen
import com.example.ui.logs.LogAnalyzerScreen
import com.example.ui.root.BootModesScreen
import com.example.ui.root.RootCenterScreen
import com.example.ui.samsung.SamsungFirmwareScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.tools.*

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    object Tools : Screen("tools", "Tools", Icons.Filled.Build)
    object Diagnostics : Screen("boot_diagnostic", "Diagnostics", Icons.Filled.Troubleshoot)
    object Logs : Screen("log_analyzer", "Logs", Icons.Filled.Code)
    object Device : Screen("device_info", "Device", Icons.Filled.PhoneAndroid)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Tools,
    Screen.Diagnostics,
    Screen.Logs,
    Screen.Device,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            // Show bottom bar on top-level destinations
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
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Main Top-Level Destinations
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Tools.route) { ToolsScreen(navController) }
            composable(Screen.Diagnostics.route) { BootDiagnosticScreen(navController) }
            composable(Screen.Logs.route) { LogAnalyzerScreen(navController) }
            composable(Screen.Device.route) { DeviceInfoScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }

            // Core Analyzers & Tools
            composable("full_system_analyzer") { FullSystemAnalyzerScreen(navController) }
            composable("image_analyzer") { ImageAnalyzerScreen(navController) }
            composable("rom_analyzer") { BootRomAnalyzerScreen(navController) }
            composable("boot_analyzer") { BootRomAnalyzerScreen(navController) }
            composable("compatibility_check") { CompatibilityCheckScreen(navController) }
            composable("global_search") { GlobalSearchScreen(navController) }
            composable("task_center") { TaskCenterScreen(navController) }
            composable("error_center") { ErrorCenterScreen(navController) }
            composable("root_center") { RootCenterScreen(navController) }
            composable("adb_fastboot") { com.example.ui.adb.AdbFastbootScreen(navController) }
            composable("usb_host_center") { com.example.ui.usb.UsbHostCenterScreen(navController) }
            composable("build_tool_registry") { BuildToolRegistryScreen(navController) }
            composable("hash_calculator") { HashCalculatorScreen(navController) }
            composable("buildprop_analyzer") { GetpropAnalyzerScreen(navController) }
            composable("getprop_analyzer") { GetpropAnalyzerScreen(navController) }
            composable("apk_inspector") { ApkInspectorScreen(navController) }
            composable("init_analyzer") { InitScriptAnalyzerScreen(navController) }
            composable("fstab_analyzer") { FstabAnalyzerScreen(navController) }
            composable("file_explorer") { FileExplorerScreen(navController) }
            composable("report_generator") { ReportGeneratorScreen(navController) }
            composable("elf_analyzer") { ElfAnalyzerScreen(navController) }
            composable("dat_analyzer") { DatAnalyzerScreen(navController) }
            composable("selinux_analyzer") { SelinuxAnalyzerScreen(navController) }
            composable("kernel_crash_analyzer") { KernelCrashAnalyzerScreen(navController) }
            composable("kernel_studio") { KernelStudioScreen(navController) }
            composable("partition_analyzer") { PartitionAnalyzerScreen(navController) }
            composable("flash_precheck") { FlashPrecheckScreen(navController) }
            composable("vendor_analyzer") { VendorHalRilScreen(navController) }
            composable("samsung_firmware") { SamsungFirmwareScreen(navController) }
            composable("boot_modes") { BootModesScreen(navController) }
            
            composable(
                route = "unified_analyzer/{toolType}",
                arguments = listOf(navArgument("toolType") { type = NavType.StringType })
            ) { backStackEntry ->
                val toolType = backStackEntry.arguments?.getString("toolType") ?: "unknown"
                UnifiedAnalyzerScreen(navController, toolType)
            }
        }
    }
}
