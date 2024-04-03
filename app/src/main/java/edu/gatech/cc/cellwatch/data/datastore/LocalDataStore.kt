package edu.gatech.cc.cellwatch.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalDataStore(private val context: Context) {
    private val TAG = this::class.simpleName

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("data_store")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val COLLECTION_MODE = stringPreferencesKey("collection_mode")
        private val FCC_POLICY_AGREED = booleanPreferencesKey("fcc_policy_agreed")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val PHONE_NUMBER = stringPreferencesKey("phone_number")
        private val EMAIL = stringPreferencesKey("email")
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val getDeviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID] ?: ""
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID] = deviceId
        }
        Firebase.crashlytics.setCustomKey("device_id", deviceId)
    }

    val getCollectionMode: Flow<CollectionMode?> = context.dataStore.data.map { preferences ->
        val stored = preferences[COLLECTION_MODE]
        if (stored == null) {
            null
        } else {
            try {
                CollectionMode.valueOf(stored)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "invalid stored collection mode $stored", e)
                throw e
            }
        }
    }

    suspend fun saveFccPolicyAgreed(fccPolicyAgreed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FCC_POLICY_AGREED] = fccPolicyAgreed
        }
    }

    val getFccPolicyAgreed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FCC_POLICY_AGREED] ?: false
    }

    suspend fun saveCollectionMode(collectionMode: CollectionMode) {
        context.dataStore.edit { preferences ->
            preferences[COLLECTION_MODE] = collectionMode.name
        }
    }

    val getUserName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME] ?: ""
    }

    suspend fun saveUserName(userName: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = userName
        }
    }

    val getPhoneNumber: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PHONE_NUMBER] ?: ""
    }

    suspend fun savePhoneNumber(phoneNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[PHONE_NUMBER] = phoneNumber
        }
    }

    val getEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EMAIL] ?: ""
    }

    suspend fun saveEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[EMAIL] = email
        }
    }

    val getOnboardingComplete: Flow<Boolean> = context.dataStore.data.map {preferences ->
        preferences[ONBOARDING_COMPLETE] ?: false
    }

    suspend fun saveOnboardingComplete(complete: Boolean) {
        context.dataStore.edit {preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        }
    }
}
