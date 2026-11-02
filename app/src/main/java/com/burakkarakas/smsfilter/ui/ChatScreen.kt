package com.burakkarakas.smsfilter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.burakkarakas.smsfilter.data.RuleKind
import com.burakkarakas.smsfilter.data.SmsItem

/** address boşsa yeni mesaj ekranıdır ve alıcı numarayı kullanıcıdan ister. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: SmsViewModel, address: String, onBack: () -> Unit) {
    val thread by vm.thread.collectAsState()
    val conversations by vm.conversations.collectAsState()
    var recipient by remember { mutableStateOf(address) }
    var text by remember { mutableStateOf("") }

    val target = if (address.isBlank()) recipient.trim() else address
    val title = conversations.firstOrNull { it.address == address }?.name
        ?: address.ifBlank { "Yeni mesaj" }

    LaunchedEffect(address) {
        if (address.isNotBlank()) vm.openThread(address)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (address.isNotBlank()) {
                        IconButton(onClick = {
                            vm.addRule(RuleKind.BLOCKED_SENDER, address)
                            onBack()
                        }) {
                            Icon(Icons.Default.Block, contentDescription = "Engelle")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Mesaj yaz") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        vm.send(target, text)
                        text = ""
                    },
                    enabled = text.isNotBlank() && target.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (address.isBlank()) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Alıcı numara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            LazyColumn(
                // Liste ters çevrildi: en yeni mesaj en altta görünür.
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(thread, key = { it.id }) { MessageBubble(it) }
            }
        }
    }
}

@Composable
private fun MessageBubble(item: SmsItem) {
    val fromMe = !item.isInbox
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = item.body,
                    color = if (fromMe) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatTime(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fromMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
