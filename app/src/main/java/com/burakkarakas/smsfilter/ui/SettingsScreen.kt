package com.burakkarakas.smsfilter.ui

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.burakkarakas.smsfilter.util.isDefaultSmsApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SmsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var isDefault by remember { mutableStateOf(isDefaultSmsApp(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultSmsApp(context)
    }

    var apiKey by remember { mutableStateOf(vm.apiKey) }
    var aiEnabled by remember { mutableStateOf(vm.aiEnabled) }
    val testStatus by vm.testStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Varsayılan SMS uygulaması", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isDefault) {
                    "Bu uygulama varsayılan SMS uygulaması."
                } else {
                    "Gelen mesajları filtreleyebilmek için bu uygulamayı varsayılan yapmalısın."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isDefault) {
                Button(onClick = {
                    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                }) {
                    Text("Varsayılan yap")
                }
            }

            HorizontalDivider()

            Text("Yapay zeka filtresi (DeepSeek)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("DeepSeek API anahtarı") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.saveApiKey(apiKey) }) { Text("Kaydet") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.testApiKey(apiKey) },
                    enabled = apiKey.isNotBlank(),
                ) {
                    Text("Test et")
                }
            }
            testStatus?.let { Text(it) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kurallarla karar veremediğim mesajları yapay zekaya sor")
                    Text(
                        "Bu açıkken mesaj metni DeepSeek'e gönderilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = aiEnabled,
                    onCheckedChange = {
                        aiEnabled = it
                        vm.setAiEnabled(it)
                    },
                )
            }
        }
    }
}
