package com.lareward.app

import android.content.Context
import android.content.SharedPreferences

class AuthPrefs private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: AuthPrefs? = null

        fun getInstance(context: Context): AuthPrefs =
            instance ?: synchronized(this) {
                instance ?: AuthPrefs(context.applicationContext).also { instance = it }
            }
    }

    var jwtToken: String?
        get() = prefs.getString("jwt_token", null)
        set(value) { prefs.edit().putString("jwt_token", value).apply() }

    fun clearAuth() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !jwtToken.isNullOrEmpty()
}