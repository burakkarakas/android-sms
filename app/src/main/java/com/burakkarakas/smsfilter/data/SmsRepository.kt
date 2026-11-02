package com.burakkarakas.smsfilter.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager

/** Sistemin SMS veritabanı üzerinde okuma, yazma ve gönderme işlemleri. */
class SmsRepository(private val context: Context, private val prefs: AppPrefs) {

    private val resolver = context.contentResolver
    private val classifier = SpamClassifier(prefs)
    private val nameCache = HashMap<String, String?>()

    fun loadMessages(limit: Int = 3000): List<SmsItem> =
        query(extra = null, extraArgs = null, sort = "${Telephony.Sms.DATE} DESC LIMIT $limit")

    fun loadThread(address: String): List<SmsItem> =
        query(
            extra = "${Telephony.Sms.ADDRESS} = ?",
            extraArgs = arrayOf(address),
            sort = "${Telephony.Sms.DATE} DESC",
        )

    fun toConversations(all: List<SmsItem>): List<Conversation> =
        all.groupBy { it.address }
            .map { (address, items) ->
                // Liste tarihe göre azalan sırada geliyor; ilk öğe en son mesaj.
                val last = items.first()
                Conversation(
                    address = address,
                    name = contactName(address),
                    lastBody = last.body,
                    lastDate = last.date,
                    unread = items.count { it.isInbox && !it.read },
                    label = last.label,
                )
            }
            .sortedByDescending { it.lastDate }

    fun markRead(address: String) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(
            Telephony.Sms.Inbox.CONTENT_URI,
            values,
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(address),
        )
    }

    fun send(address: String, body: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendMultipartTextMessage(address, null, smsManager.divideMessage(body), null, null)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    /** Gelen mesajı kaydeder, sınıflandırır ve etiketini döndürür. */
    fun receive(address: String, body: String, date: Long): Label? {
        val id = saveIncoming(address, body, date) ?: return null
        val quick = classifier.quick(address, body, isContact(address))
        val ai = if (quick == null) classifier.askAi(address, body) else null
        if (ai != null) prefs.setLabel(id, ai, manual = false)
        return quick ?: ai ?: Label.NORMAL
    }

    fun contactName(address: String): String? =
        nameCache.getOrPut(address) { runCatching { queryContactName(address) }.getOrNull() }

    private fun isContact(address: String): Boolean = contactName(address) != null

    private fun saveIncoming(address: String, body: String, date: Long): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        return resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
    }

    private fun queryContactName(address: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun query(extra: String?, extraArgs: Array<String>?, sort: String): List<SmsItem> {
        val selection = listOfNotNull("${Telephony.Sms.TYPE} IN (?, ?)", extra).joinToString(" AND ")
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
        ) + (extraArgs ?: emptyArray<String>())
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )

        val items = mutableListOf<SmsItem>()
        resolver.query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, sort)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val address = cursor.getString(1).orEmpty()
                val body = cursor.getString(2).orEmpty()
                val isInbox = cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX
                items += SmsItem(
                    id = id,
                    address = address,
                    body = body,
                    date = cursor.getLong(3),
                    isInbox = isInbox,
                    read = cursor.getInt(5) == 1,
                    label = labelOf(id, address, body, isInbox),
                )
            }
        }
        return items
    }

    private fun labelOf(id: Long, address: String, body: String, isInbox: Boolean): Label {
        if (!isInbox) return Label.NORMAL
        return prefs.getLabel(id, manual = true)
            ?: classifier.quick(address, body, isContact(address))
            ?: prefs.getLabel(id, manual = false)
            ?: Label.NORMAL
    }
}
