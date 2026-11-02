package com.burakkarakas.smsfilter.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** DeepSeek sohbet API'sine sınıflandırma ve kural çıkarma istekleri gönderir. */
object DeepSeekClient {
    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"

    private const val BASE_PROMPT =
        "Sen bir SMS filtresisin. Gelen mesajı tek kelimeyle sınıflandır. " +
            "IMPORTANT: doğrulama kodu, banka, kargo, fatura, resmi bildirim gibi önemli mesajlar. " +
            "SPAM: reklam, kampanya, bahis, sahte kazanç, dolandırıcılık veya şüpheli link içeren istenmeyen mesajlar. " +
            "NORMAL: diğer her şey. " +
            "Sadece IMPORTANT, SPAM veya NORMAL yaz."

    private const val RULES_PROMPT =
        "Sen bir SMS spam analistisin. Kullanıcının spam olarak işaretlediği mesajları inceleyip " +
            "bu mesajları ve benzerlerini yakalayacak en fazla 10 kısa, genel Türkçe kural yaz. " +
            "Her kural tek satır olsun ve 'Eğer ... ise spam' gibi anlaşılır olsun. " +
            "Normal mesajları yanlışlıkla yakalayacak kadar geniş kural yazma. " +
            "Mevcut kuralları tekrar etme. Sadece kuralları, her satıra bir tane olacak şekilde, " +
            "numara ya da madde işareti olmadan yaz."

    fun classify(
        apiKey: String,
        address: String,
        body: String,
        examples: List<Example>,
        rules: List<String> = emptyList(),
    ): Result<Label> = runCatching {
        val answer = chat(apiKey, systemPrompt(examples, rules), "Gönderen: $address\nMesaj: $body", 10).uppercase()
        parseLabel(answer) ?: error("Beklenmeyen yanıt: ${answer.take(60)}")
    }

    /** Spam örneklerinden kısa kurallar çıkarır. Normal örnekler aşırı genellemeyi önlemek için verilir. */
    fun generateRules(
        apiKey: String,
        spam: List<Example>,
        notSpam: List<Example>,
        existing: List<String>,
    ): Result<List<String>> = runCatching {
        val user = buildString {
            append("SPAM mesajlar:\n")
            spam.forEach { append("- ${it.sender}: ${it.text.take(160)}\n") }
            if (notSpam.isNotEmpty()) {
                append("\nSPAM OLMAYAN mesajlar (bunları yakalama):\n")
                notSpam.forEach { append("- ${it.sender}: ${it.text.take(100)}\n") }
            }
            if (existing.isNotEmpty()) {
                append("\nMevcut kurallar:\n")
                existing.forEach { append("- $it\n") }
            }
        }
        val bullet = Regex("^(\\d+[.)]\\s*)")
        chat(apiKey, RULES_PROMPT, user, 700, readTimeoutMs = 60_000)
            .lines()
            .map { it.trim().trimStart('-', '*', '•', ' ').replace(bullet, "").trim() }
            .filter { it.length in 8..200 }
            .take(10)
    }

    private fun chat(
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Int,
        readTimeoutMs: Int = 10_000,
    ): String {
        val payload = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code: ${response.take(200)}")

            return JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            connection.disconnect()
        }
    }

    private fun systemPrompt(examples: List<Example>, rules: List<String>): String {
        var prompt = BASE_PROMPT
        if (rules.isNotEmpty()) {
            prompt += "\n\nKullanıcının spam kuralları. Mesaj bunlardan birine uyuyorsa SPAM de:\n" +
                rules.joinToString("\n") { "- $it" }
        }
        if (examples.isNotEmpty()) {
            val lines = examples.joinToString("\n") {
                "[${it.label.name}] Gönderen: ${it.sender} | Mesaj: ${it.text.take(160)}"
            }
            prompt += "\n\nKullanıcının daha önce onayladığı kararlar. " +
                "Yeni mesaj bunlardan birine benziyorsa aynı kategoriyi seç:\n$lines"
        }
        return prompt
    }

    private fun parseLabel(answer: String): Label? = when {
        "SPAM" in answer -> Label.SPAM
        "IMPORTANT" in answer -> Label.IMPORTANT
        "NORMAL" in answer -> Label.NORMAL
        else -> null
    }
}
