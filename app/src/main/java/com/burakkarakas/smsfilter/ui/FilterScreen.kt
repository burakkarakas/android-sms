package com.burakkarakas.smsfilter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.burakkarakas.smsfilter.data.RuleKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(vm: SmsViewModel, onBack: () -> Unit) {
    val spam by vm.spam.collectAsState()
    val rules by vm.rules.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filtreler") },
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
            Text("Spam mesajlar", style = MaterialTheme.typography.titleMedium)
            if (spam.isEmpty()) {
                Text(
                    "Spam olarak işaretlenmiş mesaj yok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            spam.take(100).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.address,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.markNotSpam(item) }) { Text("Geri al") }
                }
            }

            HorizontalDivider()
            RuleEditor(
                title = "Engellenen gönderenler",
                hint = "Numara veya gönderen adı",
                values = rules[RuleKind.BLOCKED_SENDER].orEmpty(),
                onAdd = { vm.addRule(RuleKind.BLOCKED_SENDER, it) },
                onRemove = { vm.removeRule(RuleKind.BLOCKED_SENDER, it) },
            )

            HorizontalDivider()
            RuleEditor(
                title = "Engellenen kelimeler",
                hint = "Örn: bahis",
                values = rules[RuleKind.BLOCKED_KEYWORD].orEmpty(),
                onAdd = { vm.addRule(RuleKind.BLOCKED_KEYWORD, it) },
                onRemove = { vm.removeRule(RuleKind.BLOCKED_KEYWORD, it) },
            )

            HorizontalDivider()
            RuleEditor(
                title = "İzin verilen gönderenler",
                hint = "Numara veya gönderen adı",
                values = rules[RuleKind.ALLOWED_SENDER].orEmpty(),
                onAdd = { vm.addRule(RuleKind.ALLOWED_SENDER, it) },
                onRemove = { vm.removeRule(RuleKind.ALLOWED_SENDER, it) },
            )
        }
    }
}

@Composable
private fun RuleEditor(
    title: String,
    hint: String,
    values: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Text(title, style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(hint) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                onAdd(input)
                input = ""
            },
            enabled = input.isNotBlank(),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Ekle")
        }
    }
    values.forEach { value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onRemove(value) }) {
                Icon(Icons.Default.Close, contentDescription = "Kaldır")
            }
        }
    }
}
