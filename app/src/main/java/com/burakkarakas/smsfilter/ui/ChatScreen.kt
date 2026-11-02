package com.burakkarakas.smsfilter.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.burakkarakas.smsfilter.data.Contact
import com.burakkarakas.smsfilter.data.RuleKind
import com.burakkarakas.smsfilter.data.SmsItem
import com.burakkarakas.smsfilter.data.senderKey

/** address boşsa yeni mesaj ekranıdır: alıcıyı numarayla ya da rehberden seçerek belirlenir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: SmsViewModel,
    address: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val thread by vm.thread.collectAsState()
    val contacts by vm.contacts.collectAsState()
    var recipient by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    val isNew = address.isBlank()
    val target = if (isNew) recipient.trim() else address
    val contactName = if (isNew) {
        null
    } else {
        contacts.firstOrNull { senderKey(it.number) == senderKey(address) }?.name
    }
    val suggestions = if (isNew && recipient.isNotBlank()) {
        contacts.filter { matches(it, recipient.trim()) }.take(5)
    } else {
        emptyList()
    }

    LaunchedEffect(address) {
        if (!isNew) vm.openThread(address)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        contactName ?: if (isNew) "Yeni mesaj" else address,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (!isNew) {
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
                        vm.send(target, text) {
                            text = ""
                            // Yeni mesajdan gönderildiyse o kişinin sohbetine geç.
                            if (isNew) onOpenChat(target)
                        }
                    },
                    enabled = text.isNotBlank() && target.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isNew) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Alıcı") },
                    placeholder = { Text("Numara veya kişi adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                suggestions.forEach { contact ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(contact.number) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(contact.name.ifBlank { contact.number }, fontWeight = FontWeight.Bold)
                        Text(
                            contact.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

private fun matches(contact: Contact, query: String): Boolean {
    val digits = query.filter { it.isDigit() }
    return contact.name.contains(query, ignoreCase = true) ||
        (digits.length >= 3 && contact.number.filter { it.isDigit() }.contains(digits))
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
