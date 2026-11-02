package com.burakkarakas.smsfilter.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** DeepSeek sohbet API'sine tek kelimelik sınıflandırma isteği gönderir. */
object DeepSeekClient {
    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"

    private const val BASE_PROMPT =
        "Sen bir SMS filtresisin. Gelen mesajı tek kelimeyle sınıflandır. " +
            "IMPORTANT: doğrulama kodu, banka, kargo, fatura, resmi bildirim gibi önemli mesajlar. " +
            "SPAM: reklam, kampanya, bahis, sahte kazanç, dolandırıcılık veya şüpheli link içeren istenmeyen mesajlar. " +
            "NORMAL: diğer her şey. " +
            "Sadece IMPORTANT, SPAM veya NORMAL yaz."

    fun classify(apiKey: String, address: String, body: String, examples: List<Example>): Result<Label> =
        runCatching {
            val payload = JSONObject()
                .put("model", MODEL)
                .put("temperature", 0)
                .put("max_tokens", 10)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt(examples)))
                        .put(JSONObject().put("role", "user").put("content", "Gönderen: $address\nMesaj: $body")),
                )

            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 10_000
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

                val answer = JSONObject(response)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .uppercase()
                parseLabel(answer) ?: error("Beklenmeyen yanıt: ${answer.take(60)}")
            } finally {
                connection.disconnect()
            }
        }

    private fun systemPrompt(examples: List<Example>): String {
        if (examples.isEmpty()) return BASE_PROMPT
        val lines = examples.joinToString("\n") {
            "[${it.label.name}] Gönderen: ${it.sender} | Mesaj: ${it.text.take(160)}"
        }
        return "$BASE_PROMPT\n\nKullanıcının daha önce onayladığı kararlar. " +
            "Yeni mesaj bunlardan birine benziyorsa aynı kategoriyi seç:\n$lines"
    }

    private fun parseLabel(answer: String): Label? = when {
        "SPAM" in answer -> Label.SPAM
        "IMPORTANT" in answer -> Label.IMPORTANT
        "NORMAL" in answer -> Label.NORMAL
        else -> null
    }
}
