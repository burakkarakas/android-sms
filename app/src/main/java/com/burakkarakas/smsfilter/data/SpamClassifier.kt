package com.burakkarakas.smsfilter.data

import java.util.Locale

class SpamClassifier(private val prefs: AppPrefs) {

    /** Kullanıcı kuralları ve basit kelime analizi. Karar veremezse null döner. */
    fun quick(address: String, body: String, isContact: Boolean): Label? {
        if (address in prefs.rules(RuleKind.BLOCKED_SENDER)) return Label.SPAM
        if (address in prefs.rules(RuleKind.ALLOWED_SENDER) || isContact) return Label.NORMAL

        val text = body.lowercase(TURKISH)
        val blockedKeyword = prefs.rules(RuleKind.BLOCKED_KEYWORD).any { keyword ->
            text.contains(keyword.lowercase(TURKISH))
        }
        if (blockedKeyword) return Label.SPAM
        if (SPAM_HINTS.count { text.contains(it) } >= 2) return Label.SPAM
        if (IMPORTANT_HINTS.any { text.contains(it) }) return Label.IMPORTANT
        return null
    }

    /** Kurallar kararsız kaldığında DeepSeek'e sorar. Anahtar yoksa veya yapay zeka kapalıysa null. */
    fun askAi(address: String, body: String): Label? {
        val key = prefs.apiKey
        if (!prefs.aiEnabled || key.isBlank()) return null
        return DeepSeekClient.classify(key, address, body)
    }

    private companion object {
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")

        val SPAM_HINTS = listOf(
            "kazandınız", "bonus", "bahis", "casino", "freespin", "çekiliş", "hediye",
            "indirim", "tıkla", "hemen başvur", "%", "bedava", "kredi", "kampanya",
        )

        val IMPORTANT_HINTS = listOf(
            "doğrulama kodu", "dogrulama kodu", "şifre", "otp", "kargo", "teslim",
            "fatura", "hesabınız", "ödeme",
        )
    }
}
