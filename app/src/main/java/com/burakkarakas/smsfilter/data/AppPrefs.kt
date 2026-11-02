package com.burakkarakas.smsfilter.data

import android.content.Context
import androidx.core.content.edit

enum class RuleKind(val storageKey: String) {
    BLOCKED_SENDER("blocked_senders"),
    ALLOWED_SENDER("allowed_senders"),
    BLOCKED_KEYWORD("blocked_keywords"),
}

/** Ayarlar, kurallar ve mesaj etiketleri. Veriler yalnızca bu uygulamanın kendi alanında tutulur. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sms_filter", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_API_KEY, value.trim()) }

    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AI_ENABLED, value) }

    fun rules(kind: RuleKind): List<String> =
        prefs.getStringSet(kind.storageKey, emptySet()).orEmpty().sorted()

    fun addRule(kind: RuleKind, value: String) {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) return
        val updated = prefs.getStringSet(kind.storageKey, emptySet()).orEmpty().toMutableSet()
        updated.add(cleaned)
        prefs.edit { putStringSet(kind.storageKey, updated) }
    }

    fun removeRule(kind: RuleKind, value: String) {
        val updated = prefs.getStringSet(kind.storageKey, emptySet()).orEmpty().toMutableSet()
        updated.remove(value)
        prefs.edit { putStringSet(kind.storageKey, updated) }
    }

    /** manual: kullanıcının elle verdiği etiket; değilse yapay zekanın verdiği etiket. */
    fun getLabel(messageId: Long, manual: Boolean): Label? =
        prefs.getString(labelKey(messageId, manual), null)?.let { name ->
            runCatching { Label.valueOf(name) }.getOrNull()
        }

    fun setLabel(messageId: Long, label: Label, manual: Boolean) {
        prefs.edit { putString(labelKey(messageId, manual), label.name) }
    }

    private fun labelKey(messageId: Long, manual: Boolean) =
        "${if (manual) "manual" else "ai"}_$messageId"

    private companion object {
        const val KEY_API_KEY = "deepseek_api_key"
        const val KEY_AI_ENABLED = "ai_enabled"
    }
}
