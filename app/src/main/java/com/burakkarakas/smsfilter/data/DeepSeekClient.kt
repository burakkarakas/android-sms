package com.burakkarakas.smsfilter.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** DeepSeek sohbet API'sine tek kelimelik sınıflandırma isteği gönderir. */
object DeepSeekClient {
    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"

    private const val SYSTEM_PROMPT =
        "Sen bir SMS filtresisin. Gelen mesajı tek kelimeyle sınıflandır. " +
            "IMPORTANT: doğrulama kodu, banka, kargo, fatura, resmi bildirim gibi önemli mesajlar. " +
            "SPAM: reklam, kampanya, bahis, sahte kazanç, dolandırıcılık veya şüpheli link içeren istenmeyen mesajlar. " +
            "NORMAL: diğer her şey. " +
            "Sadece IMPORTANT, SPAM veya NORMAL yaz."

    fun classify(apiKey: String, address: String, body: String): Label? {
        val payload = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0)
            .put("max_tokens", 10)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", "Gönderen: $address\nMesaj: $body")),
            )

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val answer = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .uppercase()
            parseLabel(answer)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLabel(answer: String): Label? = when {
        "SPAM" in answer -> Label.SPAM
        "IMPORTANT" in answer -> Label.IMPORTANT
        "NORMAL" in answer -> Label.NORMAL
        else -> null
    }
}
