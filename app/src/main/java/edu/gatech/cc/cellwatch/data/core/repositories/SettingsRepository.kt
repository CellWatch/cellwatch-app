package edu.gatech.cc.cellwatch.data.core.repositories

import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class SettingsRepository(
    private val dataStore: LocalDataStore,
) {
    suspend fun getDeviceId(): String {
        var id = dataStore.getDeviceId.firstOrNull()
        if (id == null || id == "") {
            id = UUID.randomUUID().toString()
            dataStore.saveDeviceId(id)
        }

        return id
    }

    suspend fun getOnboardingComplete(): Boolean {
        return dataStore.getOnboardingComplete.firstOrNull() ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.saveOnboardingComplete(complete)
    }

    suspend fun getCollectionMode(): CollectionMode {
        var mode = dataStore.getCollectionMode.firstOrNull()
        if (mode == null) {
            mode = if (getName().isNotBlank() && getPhoneNumber().isNotBlank() && getEmail().isNotBlank() && getFccSharingAcknowledged()) {
                CollectionMode.FCC_CHALLENGE
            } else {
                CollectionMode.TESTING
            }

            dataStore.saveCollectionMode(mode)
        }
        return mode
    }

    suspend fun setCollectionMode(mode: CollectionMode) {
        if (mode == CollectionMode.FCC_CHALLENGE && (
            getName().isBlank() ||
            getPhoneNumber().isBlank() ||
            getEmail().isBlank() ||
            !getFccSharingAcknowledged()
        )) {
            throw MissingFccInfoException()
        }

        dataStore.saveCollectionMode(mode)
    }

    suspend fun getName(): String {
        return dataStore.getUserName.firstOrNull() ?: ""
    }

    suspend fun setName(name: String) {
        if (getCollectionMode() == CollectionMode.FCC_CHALLENGE && name.isBlank()) {
            throw BlankInFccChallengeModeException()
        }

        dataStore.saveUserName(name)
    }

    suspend fun getPhoneNumber(): String {
        return dataStore.getPhoneNumber.firstOrNull() ?: ""
    }

    suspend fun setPhoneNumber(phoneNumber: String) {
        if (getCollectionMode() == CollectionMode.FCC_CHALLENGE && phoneNumber.isBlank()) {
            throw BlankInFccChallengeModeException()
        }

        dataStore.savePhoneNumber(phoneNumber)
    }

    suspend fun getEmail(): String {
        return dataStore.getEmail.firstOrNull() ?: ""
    }

    suspend fun setEmail(email: String) {
        if (getCollectionMode() == CollectionMode.FCC_CHALLENGE && email.isBlank()) {
            throw BlankInFccChallengeModeException()
        }

        dataStore.saveEmail(email)
    }

    suspend fun getFccSharingAcknowledged(): Boolean {
        return dataStore.getFccPolicyAgreed.firstOrNull() ?: false
    }

    suspend fun setFccSharingAcknowledged(acknowledged: Boolean) {
        if (getCollectionMode() == CollectionMode.FCC_CHALLENGE && !acknowledged) {
            throw IllegalStateException("Cannot un-acknowledge FCC sharing while in FCC Challenge mode")
        }

        dataStore.saveFccPolicyAgreed(acknowledged)
    }

    class MissingFccInfoException: IllegalStateException()
    class BlankInFccChallengeModeException: IllegalStateException()
}