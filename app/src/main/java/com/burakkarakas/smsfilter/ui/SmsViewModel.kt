package com.burakkarakas.smsfilter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.burakkarakas.smsfilter.data.AppPrefs
import com.burakkarakas.smsfilter.data.Conversation
import com.burakkarakas.smsfilter.data.DeepSeekClient
import com.burakkarakas.smsfilter.data.Label
import com.burakkarakas.smsfilter.data.RuleKind
import com.burakkarakas.smsfilter.data.SmsItem
import com.burakkarakas.smsfilter.data.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private val repo = SmsRepository(application, prefs)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _spam = MutableStateFlow<List<SmsItem>>(emptyList())
    val spam: StateFlow<List<SmsItem>> = _spam.asStateFlow()

    private val _thread = MutableStateFlow<List<SmsItem>>(emptyList())
    val thread: StateFlow<List<SmsItem>> = _thread.asStateFlow()

    private val _rules = MutableStateFlow(allRules())
    val rules: StateFlow<Map<RuleKind, List<String>>> = _rules.asStateFlow()

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    val apiKey: String get() = prefs.apiKey
    val aiEnabled: Boolean get() = prefs.aiEnabled

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            // İzin verilmeden çağrılırsa hata vermesin; izin ekranı sonra tekrar tetikler.
            runCatching { reloadConversations() }
        }
    }

    fun openThread(address: String) {
        _thread.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            repo.markRead(address)
            _thread.value = repo.loadThread(address)
            reloadConversations()
        }
    }

    fun send(address: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.send(address, body) }
            _thread.value = repo.loadThread(address)
            reloadConversations()
        }
    }

    fun markNotSpam(item: SmsItem) {
        prefs.setLabel(item.id, Label.NORMAL, manual = true)
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
            val ok = DeepSeekClient.classify(key.trim(), "TEST", "Bu bir bağlantı testidir.") != null
            _testStatus.value = if (ok) {
                "Bağlantı başarılı."
            } else {
                "Bağlantı başarısız. Anahtarı ve internet bağlantını kontrol et."
            }
        }
    }

    fun addRule(kind: RuleKind, value: String) {
        prefs.addRule(kind, value)
        _rules.value = allRules()
        refresh()
    }

    fun removeRule(kind: RuleKind, value: String) {
        prefs.removeRule(kind, value)
        _rules.value = allRules()
        refresh()
    }

    private suspend fun reloadConversations() {
        val all = repo.loadMessages()
        _conversations.value = repo.toConversations(all)
        _spam.value = all.filter { it.label == Label.SPAM }
    }

    private fun allRules(): Map<RuleKind, List<String>> =
        RuleKind.entries.associateWith { prefs.rules(it) }
}
