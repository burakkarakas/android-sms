package com.burakkarakas.smsfilter.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Hızlı yanıt isteklerini karşılar. Varsayılan SMS uygulaması olabilmek için gerekli. */
class HeadlessSmsSendService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
