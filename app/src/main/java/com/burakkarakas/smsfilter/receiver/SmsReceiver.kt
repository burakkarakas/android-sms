package com.burakkarakas.smsfilter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.burakkarakas.smsfilter.data.AppPrefs
import com.burakkarakas.smsfilter.data.Label
import com.burakkarakas.smsfilter.data.SmsRepository
import com.burakkarakas.smsfilter.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Varsayılan SMS uygulaması olarak sistem bu mesajı bize teslim eder; kaydetmek bizim işimiz.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SmsRepository(context, AppPrefs(context))
                messages.groupBy { it.originatingAddress.orEmpty() }.forEach { (address, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val label = repo.receive(address, body, parts.first().timestampMillis) ?: return@forEach
                    if (label != Label.SPAM) Notifier.show(context, address, body)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
