package com.burakkarakas.smsfilter.data

import java.util.Locale

val TURKISH_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

/**
 * Aynı numaranın farklı yazımlarını (+90 506..., 0506..., 506...) tek anahtarda toplar.
 * Harfli gönderenler (ör. "A101") küçük harfli haliyle kullanılır.
 */
fun senderKey(address: String): String {
    val digits = address.filter { it.isDigit() }
    return if (digits.length >= 7) digits.takeLast(10) else address.trim().lowercase(TURKISH_LOCALE)
}
