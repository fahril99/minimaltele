package com.minimaltelegram

import android.content.Context
import android.content.SharedPreferences

object AccountManager {
    private const val PREFS_NAME = "MinimalTelegramAccounts"
    private const val KEY_ACTIVE_ACCOUNT = "active_account_id"
    private const val KEY_ACCOUNT_IDS = "account_ids"
    
    private lateinit val prefs: SharedPreferences
    
    var activeAccountId: Int = 1
        private set
        
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        activeAccountId = prefs.getInt(KEY_ACTIVE_ACCOUNT, 1)
        val ids = getAccountIds()
        if (ids.isEmpty()) {
            addAccountId(1)
        }
    }
    
    fun getAccountIds(): List<Int> {
        val str = prefs.getString(KEY_ACCOUNT_IDS, "") ?: ""
        return if (str.isEmpty()) emptyList() else str.split(",").map { it.toInt() }
    }
    
    private fun addAccountId(id: Int) {
        val ids = getAccountIds().toMutableSet()
        ids.add(id)
        prefs.edit().putString(KEY_ACCOUNT_IDS, ids.joinToString(",")).apply()
    }
    
    fun removeAccountId(id: Int) {
        val ids = getAccountIds().toMutableSet()
        ids.remove(id)
        prefs.edit().putString(KEY_ACCOUNT_IDS, ids.joinToString(",")).apply()
    }
    
    fun switchAccount(id: Int) {
        if (getAccountIds().contains(id)) {
            activeAccountId = id
            prefs.edit().putInt(KEY_ACTIVE_ACCOUNT, id).apply()
        }
    }
    
    fun createNewAccount(): Int {
        val ids = getAccountIds()
        val nextId = (ids.maxOrNull() ?: 0) + 1
        addAccountId(nextId)
        return nextId
    }
}
