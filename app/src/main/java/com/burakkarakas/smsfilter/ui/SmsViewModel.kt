package com.burakkarakas.smsfilter.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.burakkarakas.smsfilter.data.AppPrefs
import com.burakkarakas.smsfilter.data.Contact
import com.burakkarakas.smsfilter.data.Conversation
import com.burakkarakas.smsfilter.data.DeepSeekClient
import com.burakkarakas.smsfilter.data.Label
import com.burakkarakas.smsfilter.data.RuleKind
import com.burakkarakas.smsfilter.data.SmsItem
import com.burakkarakas.smsfilter.data.SmsRepository
import com.burakkarakas.smsfilter.data.senderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private val repo = SmsRepository(application, prefs)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _spam = MutableStateFlow<List<SmsItem>>(emptyList())
    val spam: StateFlow<List<SmsItem>> = _spam.asStateFlow()

    private val _thread = MutableStateFlow<List<SmsItem>>(emptyList())
    val thread: StateFlow<List<SmsItem>> = _thread.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _rules = MutableStateFlow(allRules())
    val rules: StateFlow<Map<RuleKind, List<String>>> = _rules.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val apiKey: String get() = prefs.apiKey
    val aiEnabled: Boolean get() = prefs.aiEnabled

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            // İzin henüz verilmediyse hata vermesin; izin alındıktan sonra tekrar yüklenir.
            runCatching { reload() }
            _loading.value = false
        }
    }

    fun openThread(address: String) {
        _thread.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.markRead(address) }
            _thread.value = runCatching { repo.loadThread(address) }.getOrDefault(emptyList())
            runCatching { reload() }
        }
    }

    fun send(address: String, body: String, onSent: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val error = runCatching { repo.send(address, body) }.exceptionOrNull()
            if (error != null) {
                withContext(Dispatchers.Main) { toast("Gönderilemedi: ${error.message}") }
                return@launch
            }
            _thread.value = runCatching { repo.loadThread(address) }.getOrDefault(emptyList())
            runCatching { reload() }
            withContext(Dispatchers.Main) { onSent() }
        }
    }

    fun markNotSpam(item: SmsItem) {
        prefs.setLabel(item.id, Label.NORMAL, manual = true)
        prefs.addExample(Label.NORMAL, item.address, item.body)
        _spam.update { list -> list.filterNot { it.id == item.id } }
        refresh()
    }

    fun saveApiKey(key: String) {
        prefs.apiKey = key
    }

    fun setAiEnabled(enabled: Boolean) {
        prefs.aiEnabled = enabled
    }

    fun testApiKey(key: String) {
        _testStatus.value = "Test ediliyor..."
        viewModelScope.launch(Dispatchers.IO) {
            val result = DeepSeekClient.classify(key.trim(), "TEST", "Bu bir bağlantı testidir.", emptyList())
            _testStatus.value = result.fold(
                onSuccess = { "Bağlantı başarılı. Yanıt: ${it.name}" },
                onFailure = { "Bağlantı başarısız: ${it.message}" },
            )
        }
    }

    fun addRule(kind: RuleKind, value: String) {
        if (kind == RuleKind.BLOCKED_SENDER) {
            // Engellenen gönderenin mesajlarını hemen listeden kaldır; kalıcı sonuç yenilemeden gelir.
            val key = senderKey(value)
            _conversations.update { list ->
                list.map { if (senderKey(it.address) == key) it.copy(label = Label.SPAM) else it }
            }
        }
        prefs.addRule(kind, value)
        _rules.value = allRules()
        refresh()
    }

    fun removeRule(kind: RuleKind, value: String) {
        prefs.removeRule(kind, value)
        _rules.value = allRules()
        refresh()
    }

    private suspend fun reload() {
        val contacts = repo.loadContacts()
        val all = repo.loadMessages(contacts)
        _contacts.value = contacts
        _conversations.value = repo.toConversations(all, contacts)
        _spam.value = all.filter { it.label == Label.SPAM }
    }

    private fun allRules(): Map<RuleKind, List<String>> =
        RuleKind.entries.associateWith { prefs.rules(it) }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }
}
