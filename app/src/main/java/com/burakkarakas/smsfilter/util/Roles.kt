package com.burakkarakas.smsfilter.util

import android.app.role.RoleManager
import android.content.Context

fun isDefaultSmsApp(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
