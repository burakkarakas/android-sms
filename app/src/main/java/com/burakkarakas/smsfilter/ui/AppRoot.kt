package com.burakkarakas.smsfilter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Screen {
    data object Home : Screen
    data class Chat(val address: String) : Screen
    data object Filters : Screen
    data object Settings : Screen
}

private val requiredPermissions = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.POST_NOTIFICATIONS,
)

@Composable
fun AppRoot(vm: SmsViewModel = viewModel()) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) vm.refresh() else permissionLauncher.launch(missing.toTypedArray())
    }

    // Uygulama öne geldiğinde (ör. başka bir SMS geldikten sonra) listeyi yenile.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            vm = vm,
            onOpenChat = { screen = Screen.Chat(it) },
            onOpenFilters = { screen = Screen.Filters },
            onOpenSettings = { screen = Screen.Settings },
        )
        is Screen.Chat -> ChatScreen(vm = vm, address = current.address, onBack = { screen = Screen.Home })
        Screen.Filters -> FilterScreen(vm = vm, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(vm = vm, onBack = { screen = Screen.Home })
    }
}
