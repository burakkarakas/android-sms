package com.burakkarakas.smsfilter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** MMS desteklenmiyor. Bu receiver yalnızca varsayılan SMS uygulaması şartını karşılamak için var. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
