package edu.gatech.cc.cellwatch.androidtestapp.onboarding

import android.content.Context
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfile
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileStore

class AndroidOnboardingProfileStore(
    context: Context,
) : OnboardingProfileStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadProfile(): OnboardingProfile? {
        if (!prefs.contains(KEY_NAME)) {
            return null
        }
        val mode = prefs.getString(KEY_COLLECTION_MODE, CollectionMode.TESTING.name)
            ?.let { raw ->
                runCatching { CollectionMode.valueOf(raw) }.getOrDefault(CollectionMode.TESTING)
            }
            ?: CollectionMode.TESTING
        return OnboardingProfile(
            collectionMode = mode,
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            phone = prefs.getString(KEY_PHONE, "").orEmpty(),
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            fccAcknowledged = prefs.getBoolean(KEY_ACK, false),
            onboardingComplete = prefs.getBoolean(KEY_COMPLETE, false),
        )
    }

    override fun saveProfile(profile: OnboardingProfile) {
        prefs.edit()
            .putString(KEY_COLLECTION_MODE, profile.collectionMode.name)
            .putString(KEY_NAME, profile.name)
            .putString(KEY_PHONE, profile.phone)
            .putString(KEY_EMAIL, profile.email)
            .putBoolean(KEY_ACK, profile.fccAcknowledged)
            .putBoolean(KEY_COMPLETE, profile.onboardingComplete)
            .apply()
    }

    override fun clearProfile() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "cellwatch_onboarding_profile"
        private const val KEY_COLLECTION_MODE = "collection_mode"
        private const val KEY_NAME = "name"
        private const val KEY_PHONE = "phone"
        private const val KEY_EMAIL = "email"
        private const val KEY_ACK = "ack"
        private const val KEY_COMPLETE = "onboarding_complete"
    }
}
