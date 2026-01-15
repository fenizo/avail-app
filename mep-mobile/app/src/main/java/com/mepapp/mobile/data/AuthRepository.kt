package com.mepapp.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mepapp.mobile.network.LoginRequest
import com.mepapp.mobile.network.MepApiService
import com.mepapp.mobile.network.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class AuthRepository(private val context: Context) {
    private val apiService = NetworkModule.createService<MepApiService>()
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")

    val authToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[TOKEN_KEY] }
        
    val userId: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[USER_ID_KEY] }

    suspend fun login(phone: String, pin: String) {
        val response = apiService.login(LoginRequest(phone, pin))
        context.dataStore.edit { settings ->
            settings[TOKEN_KEY] = response.token
            settings[USER_ID_KEY] = response.id
        }
        NetworkModule.setAuthToken(response.token)
    }

    suspend fun logout() {
        context.dataStore.edit { settings ->
            settings.remove(TOKEN_KEY)
            settings.remove(USER_ID_KEY)
        }
        NetworkModule.setAuthToken("")
    }
}
