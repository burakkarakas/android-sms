package com.burakkarakas.smsfilter.data

/** Karar verirken kullanılan kullanıcı kuralları ve rehber bilgisi. */
class FilterContext(
    val blockedSenders: Set<String>,
    val allowedSenders: Set<String>,
    val blockedKeywords: List<String>,
    /** senderKey -> kişi adı */
    val contactNames: Map<String, String>,
)

class SpamClassifier(private val prefs: AppPrefs) {

    /** Kullanıcının açıkça verdiği kurallar. Karar yoksa null. */
    fun explicit(address: String, body: String, ctx: FilterContext): Label? {
        val key = senderKey(address)
        if (key in ctx.blockedSenders) return Label.SPAM
        if (key in ctx.allowedSenders || key in ctx.contactNames) return Label.NORMAL
        val text = body.lowercase(TURKISH_LOCALE)
        if (ctx.blockedKeywords.any { text.contains(it) }) return Label.SPAM
        return null
    }

    /** DeepSeek'e sorar. Anahtar yoksa, yapay zeka kapalıysa veya istek başarısızsa null. */
    fun askAi(address: String, body: String): Label? {
        val key = prefs.apiKey
        if (!prefs.aiEnabled || key.isBlank()) return null
        return DeepSeekClient.classify(key, address, body, prefs.examples()).getOrNull()
    }

    /** Yapay zeka kullanılamadığında kelime analizi. */
    fun heuristic(body: String): Label {
        val text = body.lowercase(TURKISH_LOCALE)
        return when {
            SPAM_HINTS.count { text.contains(it) } >= 2 -> Label.SPAM
            IMPORTANT_HINTS.any { text.contains(it) } -> Label.IMPORTANT
            else -> Label.NORMAL
        }
    }

    private companion object {
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
