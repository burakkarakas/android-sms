package com.burakkarakas.smsfilter.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.burakkarakas.smsfilter.data.AppPrefs
import com.burakkarakas.smsfilter.data.Contact
import com.burakkarakas.smsfilter.data.Conversation
import com.burakkarakas.smsfilter.data.DeepSeekClient
import com.burakkarakas.smsfilter.data.Example
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

    /** Ana liste ekrandan çıkıp dönünce kaldığı yerden devam etsin diye ekran dışında tutulur. */
    val homeListState = LazyListState()
    var homeTabIndex by mutableIntStateOf(0)

    private val _ruleStatus =MutableStateFlow<String?>(null)
    val ruleStatus: StateFlow<String?> = _ruleStatus.asStateFlow()

    @Volatile
    private var allMessages: List<SmsItem> = emptyList()

    private val _loading =MutableStateFlow(false)
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
            val now = System.currentTimeMillis()
            val loaded = runCatching { repo.loadThread(address) }.getOrDefault(emptyList())
            // Sistem kaydına yazılamadıysa bile gönderilen mesaj ekranda görünsün.
            val saved = loaded.any { !it.isInbox && it.body == body && now - it.date < 60_000 }
            _thread.value = if (saved) loaded else {
                listOf(SmsItem(-now, address, body, now, isInbox = false, read = true, label = Label.NORMAL)) + loaded
            }
            runCatching { reload() }
            withContext(Dispatchers.Main) { onSent() }
        }
    }

    fun markAllRead() {
        // Okunmamış sayaçları hemen sıfırla; kalıcı sonuç yenilemeden gelir.
        _conversations.update { list -> list.map { it.copy(unread = 0) } }
        viewModelScope.launch(Dispatchers.IO) {
            val error = runCatching { repo.markAllRead() }.exceptionOrNull()
            if (error != null) {
                withContext(Dispatchers.Main) { toast("Okundu yapılamadı: ${error.message}") }
            }
            runCatching { reload() }
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

    /** Spam klasöründeki mesajlardan yapay zekaya kural çıkartır; kullanıcı beğenmediklerini silebilir. */
    fun generateRules() {
        val key = prefs.apiKey
        if (key.isBlank()) {
            _ruleStatus.value = "Önce Ayarlar'dan DeepSeek API anahtarını gir."
            return
        }
        val spamSamples = _spam.value
            .distinctBy { it.body.take(40) }
            .take(60)
            .map { Example(Label.SPAM, it.address, it.body) }
        if (spamSamples.isEmpty()) {
            _ruleStatus.value = "Spam klasörü boş; kural çıkarılacak mesaj yok."
            return
        }
        val normalSamples = allMessages
            .filter { it.isInbox && it.label != Label.SPAM }
            .distinctBy { senderKey(it.address) }
            .take(15)
            .map { Example(Label.NORMAL, it.address, it.body) }

        _ruleStatus.value = "Kurallar çıkarılıyor..."
        viewModelScope.launch(Dispatchers.IO) {
            val existing = prefs.rules(RuleKind.AI_RULE)
            val result = DeepSeekClient.generateRules(key, spamSamples, normalSamples, existing)
            _ruleStatus.value = result.fold(
                onSuccess = { rules ->
                    val fresh = rules.filterNot { it in existing }
                    fresh.forEach { prefs.addRule(RuleKind.AI_RULE, it) }
                    _rules.value = allRules()
                    if (fresh.isEmpty()) "Yeni kural bulunamadı." else "${fresh.size} yeni kural eklendi."
                },
                onFailure = { "Kural çıkarılamadı: ${it.message}" },
            )
        }
    }

    /** Bir göndereni spam listesinden çıkarır: engeli kaldırır ve izin verilenlere ekler. */
    fun trustSender(address: String) {
        val key = senderKey(address)
        prefs.removeRule(RuleKind.BLOCKED_SENDER, key)
        prefs.addRule(RuleKind.ALLOWED_SENDER, key)
        _spam.update { list -> list.filterNot { senderKey(it.address) == key } }
        _rules.value = allRules()
        refresh()
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
        allMessages = all
        _conversations.value = repo.toConversations(all, contacts)
        _spam.value = all.filter { it.label == Label.SPAM }
    }

    private fun allRules(): Map<RuleKind, List<String>> =
        RuleKind.entries.associateWith { prefs.rules(it) }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }
}
