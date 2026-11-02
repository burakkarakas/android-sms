package com.burakkarakas.smsfilter.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

enum class RuleKind(val storageKey: String) {
    BLOCKED_SENDER("blocked_senders"),
    ALLOWED_SENDER("allowed_senders"),
    BLOCKED_KEYWORD("blocked_keywords"),

    /** Yapay zekanın spam mesajlardan çıkardığı ya da kullanıcının yazdığı serbest metin kurallar. */
    AI_RULE("ai_rules"),
}

/** Ayarlar, kurallar, mesaj etiketleri ve yapay zekaya örnek olarak verilen geçmiş kararlar. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sms_filter", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_API_KEY, value.trim()) }

    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AI_ENABLED, value) }

    fun rules(kind: RuleKind): List<String> {
        val values = prefs.getStringSet(kind.storageKey, emptySet()).orEmpty()
        return when (kind) {
            RuleKind.BLOCKED_KEYWORD, RuleKind.AI_RULE -> values.sorted()
            RuleKind.BLOCKED_SENDER, RuleKind.ALLOWED_SENDER -> values.map { senderKey(it) }.distinct().sorted()
        }
    }

    fun addRule(kind: RuleKind, value: String) {
        val cleaned = when (kind) {
            RuleKind.BLOCKED_KEYWORD -> value.trim().lowercase(TURKISH_LOCALE)
            RuleKind.AI_RULE -> value.trim().take(200)
            RuleKind.BLOCKED_SENDER, RuleKind.ALLOWED_SENDER -> senderKey(value)
        }
        if (cleaned.isEmpty()) return
        val updated = prefs.getStringSet(kind.storageKey, emptySet()).orEmpty().toMutableSet()
        updated.add(cleaned)
        prefs.edit { putStringSet(kind.storageKey, updated) }
    }

    fun removeRule(kind: RuleKind, value: String) {
        val updated = prefs.getStringSet(kind.storageKey, emptySet()).orEmpty().toMutableSet()
        when (kind) {
            RuleKind.BLOCKED_KEYWORD, RuleKind.AI_RULE -> updated.remove(value)
            RuleKind.BLOCKED_SENDER, RuleKind.ALLOWED_SENDER -> updated.removeAll { senderKey(it) == value }
        }
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

    /** En yeni örnekler başta. Yapay zekaya "daha önce bu tür mesajları şöyle sınıflandırdık" diye verilir. */
    fun examples(): List<Example> {
        val raw = prefs.getString(KEY_EXAMPLES, null) ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .mapNotNull { obj ->
                val label = runCatching { Label.valueOf(obj.getString("label")) }.getOrNull()
                label?.let { Example(it, obj.optString("sender"), obj.optString("text")) }
            }
    }

    fun addExample(label: Label, sender: String, text: String) {
        val array = JSONArray()
        array.put(JSONObject().put("label", label.name).put("sender", sender).put("text", text.take(300)))
        examples().take(MAX_EXAMPLES - 1).forEach { example ->
            array.put(
                JSONObject()
                    .put("label", example.label.name)
                    .put("sender", example.sender)
                    .put("text", example.text),
            )
        }
        prefs.edit { putString(KEY_EXAMPLES, array.toString()) }
    }

    private fun labelKey(messageId: Long, manual: Boolean) =
        "${if (manual) "manual" else "ai"}_$messageId"

    private companion object {
        const val KEY_API_KEY = "deepseek_api_key"
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_EXAMPLES = "examples"
        const val MAX_EXAMPLES = 30
    }
}
