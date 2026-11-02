package com.burakkarakas.smsfilter.data

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager

/** Sistemin SMS ve rehber veritabanı üzerinde okuma, yazma ve gönderme işlemleri. */
class SmsRepository(private val context: Context, private val prefs: AppPrefs) {

    private val resolver = context.contentResolver
    private val classifier = SpamClassifier(prefs)

    fun loadContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        // Rehber izni yoksa boş liste döner.
        runCatching {
            resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(1)
                        if (number != null) contacts += Contact(cursor.getString(0).orEmpty(), number)
                    }
                }
        }
        return contacts
    }

    fun filterContext(contacts: List<Contact> = loadContacts()): FilterContext = FilterContext(
        blockedSenders = prefs.rules(RuleKind.BLOCKED_SENDER).toSet(),
        allowedSenders = prefs.rules(RuleKind.ALLOWED_SENDER).toSet(),
        blockedKeywords = prefs.rules(RuleKind.BLOCKED_KEYWORD),
        contactNames = contacts.associate { senderKey(it.number) to it.name.ifBlank { it.number } },
    )

    fun loadMessages(contacts: List<Contact> = loadContacts(), limit: Int = 3000): List<SmsItem> =
        query(
            extra = null,
            extraArgs = null,
            sort = "${Telephony.Sms.DATE} DESC LIMIT $limit",
            ctx = filterContext(contacts),
        )

    fun loadThread(address: String, contacts: List<Contact> = loadContacts()): List<SmsItem> {
        val key = senderKey(address)
        // Numaralarda son 10 hane eşleşsin diye LIKE kullanılır (+90 ya da 0 öneki fark etmesin).
        val isNumber = address.count { it.isDigit() } >= 7
        val extra = if (isNumber) "${Telephony.Sms.ADDRESS} LIKE ?" else "${Telephony.Sms.ADDRESS} = ?"
        val arg = if (isNumber) "%$key" else address.trim()
        return query(
            extra = extra,
            extraArgs = arrayOf(arg),
            sort = "${Telephony.Sms.DATE} DESC LIMIT 3000",
            ctx = filterContext(contacts),
        ).filter { senderKey(it.address) == key }
    }

    fun toConversations(all: List<SmsItem>, contacts: List<Contact>): List<Conversation> {
        val names = contacts.associate { senderKey(it.number) to it.name.ifBlank { it.number } }
        return all.groupBy { senderKey(it.address) }
            .map { (key, items) ->
                // Liste tarihe göre azalan sırada; ilk öğe en son mesaj.
                val last = items.first()
                Conversation(
                    address = last.address,
                    name = names[key],
                    lastBody = last.body,
                    lastDate = last.date,
                    unread = items.count { it.isInbox && !it.read },
                    label = last.label,
                )
            }
            .sortedByDescending { it.lastDate }
    }

    fun markRead(address: String) {
        loadThread(address)
            .filter { it.isInbox && !it.read }
            .forEach { item ->
                val values = ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                }
                resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms._ID} = ?", arrayOf(item.id.toString()))
            }
    }

    fun markAllRead() {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(Telephony.Sms.Inbox.CONTENT_URI, values, "${Telephony.Sms.READ} = 0", null)
    }

    /** Mesajı gönderir ve sistem geçmişine yazar. Hata olursa exception fırlatır. */
    fun send(address: String, body: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendMultipartTextMessage(address, null, smsManager.divideMessage(body), null, null)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        runCatching { resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) }
    }

    /** Gelen mesajı kaydeder, sınıflandırır ve etiketini döndürür. */
    fun receive(address: String, body: String, date: Long): Label? {
        val id = saveIncoming(address, body, date) ?: return null
        val explicit = classifier.explicit(address, body, filterContext())
        val ai = if (explicit == null) classifier.askAi(address, body) else null
        if (ai != null) prefs.setLabel(id, ai, manual = false)
        val label = explicit ?: ai ?: classifier.heuristic(body)
        if (label == Label.SPAM) prefs.addExample(label, address, body)
        return label
    }

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

    private fun query(extra: String?, extraArgs: Array<String>?, sort: String, ctx: FilterContext): List<SmsItem> {
        // Gönderilen mesajlar sıra/hata durumunda (outbox, failed, queued) da görünsün.
        val selection = listOfNotNull("${Telephony.Sms.TYPE} IN (?, ?, ?, ?, ?)", extra).joinToString(" AND ")
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
            Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString(),
            Telephony.Sms.MESSAGE_TYPE_FAILED.toString(),
            Telephony.Sms.MESSAGE_TYPE_QUEUED.toString(),
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
                    label = labelOf(id, address, body, isInbox, ctx),
                )
            }
        }
        return items
    }

    private fun labelOf(id: Long, address: String, body: String, isInbox: Boolean, ctx: FilterContext): Label {
        if (!isInbox) return Label.NORMAL
        return prefs.getLabel(id, manual = true)
            ?: classifier.explicit(address, body, ctx)
            ?: prefs.getLabel(id, manual = false)
            ?: classifier.heuristic(body)
    }
}
