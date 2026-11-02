package com.burakkarakas.smsfilter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.burakkarakas.smsfilter.data.Conversation
import com.burakkarakas.smsfilter.data.Label
import com.burakkarakas.smsfilter.data.senderKey
import com.burakkarakas.smsfilter.util.isDefaultSmsApp

private enum class HomeTab(val title: String) {
    ALL("Tümü"),
    UNREAD("Okunmamış"),
    IMPORTANT("Önemli"),
    SPAM("Spam"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: SmsViewModel,
    onOpenChat: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val conversations by vm.conversations.collectAsState()
    val spam by vm.spam.collectAsState()
    val loading by vm.loading.collectAsState()
    val isDefault = isDefaultSmsApp(LocalContext.current)
    var tab by remember { mutableStateOf(HomeTab.ALL) }
    var spamBySender by remember { mutableStateOf(false) }
    val spamSenders = spam.groupBy { senderKey(it.address) }.values
        .map { it.first().address to it.size }
        .sortedByDescending { it.second }

    val visible: List<Conversation> = when (tab) {
        HomeTab.ALL -> conversations.filter { it.label != Label.SPAM }
        HomeTab.UNREAD -> conversations.filter { it.label != Label.SPAM && it.unread > 0 }
        HomeTab.IMPORTANT -> conversations.filter { it.label == Label.IMPORTANT }
        HomeTab.SPAM -> emptyList()
    }
    val isEmpty = if (tab == HomeTab.SPAM) spam.isEmpty() else visible.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesajlar", fontWeight = FontWeight.Bold) },
                actions = {
                    if (tab == HomeTab.UNREAD) {
                        IconButton(onClick = { vm.markAllRead() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Hepsini okundu yap")
                        }
                    }
                    IconButton(onClick = onOpenFilters) {
                        Icon(Icons.Default.Block, contentDescription = "Filtreler")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenChat("") }) {
                Icon(Icons.Default.Edit, contentDescription = "Yeni mesaj")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(HomeTab.entries) { option ->
                    FilterChip(
                        selected = tab == option,
                        onClick = { tab = option },
                        label = { Text(option.title) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (!isDefault) {
                        item { DefaultAppBanner(onOpenSettings) }
                    }
                    if (isEmpty) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Bu kategoride mesaj yok",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (tab == HomeTab.SPAM) {
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = !spamBySender,
                                    onClick = { spamBySender = false },
                                    label = { Text("Mesajlar") },
                                )
                                FilterChip(
                                    selected = spamBySender,
                                    onClick = { spamBySender = true },
                                    label = { Text("Spamlenen numaralar (${spamSenders.size})") },
                                )
                            }
                        }
                        if (spamBySender) {
                            items(spamSenders, key = { "sender_" + senderKey(it.first) }) { (address, count) ->
                                ListRow(
                                    title = address,
                                    subtitle = "$count spam mesaj",
                                    date = null,
                                    unread = 0,
                                    onClick = {},
                                    trailing = {
                                        TextButton(onClick = { vm.trustSender(address) }) { Text("Listeden çıkar") }
                                    },
                                )
                            }
                        } else {
                            items(spam, key = { it.id }) { item ->
                                ListRow(
                                    title = item.address,
                                    subtitle = item.body,
                                    date = item.date,
                                    unread = 0,
                                    onClick = { onOpenChat(item.address) },
                                    trailing = {
                                        TextButton(onClick = { vm.markNotSpam(item) }) { Text("Geri al") }
                                    },
                                )
                            }
                        }
                    } else {
                        items(visible, key = { it.address }) { conversation ->
                            ListRow(
                                title = conversation.name ?: conversation.address,
                                subtitle = conversation.lastBody,
                                date = conversation.lastDate,
                                unread = conversation.unread,
                                onClick = { onOpenChat(conversation.address) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultAppBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Gelen mesajları filtrelemek için bu uygulamayı varsayılan SMS uygulaması yap.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenSettings) { Text("Ayarlara git") }
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    date: Long?,
    unread: Int,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (date != null) {
                    Text(
                        text = formatTime(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("$unread") }
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Avatar(name: String) {
    val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
