package com.burakkarakas.smsfilter.data

enum class Label(val title: String) {
    IMPORTANT("Önemli"),
    NORMAL("Normal"),
    SPAM("Spam"),
}

data class SmsItem(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isInbox: Boolean,
    val read: Boolean,
    val label: Label,
)

data class Conversation(
    val address: String,
    val name: String?,
    val lastBody: String,
    val lastDate: Long,
    val unread: Int,
    val label: Label,
)

data class Contact(
    val name: String,
    val number: String,
)

/** Yapay zekaya örnek olarak verilen, daha önce verilmiş bir karar. */
data class Example(
    val label: Label,
    val sender: String,
    val text: String,
)
