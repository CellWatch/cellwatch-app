package edu.gatech.cc.cellwatch.domain.onboarding

interface OnboardingProfileStore {
    fun loadProfile(): OnboardingProfile?
    fun saveProfile(profile: OnboardingProfile)
    fun clearProfile()
}

class InMemoryOnboardingProfileStore(
    private var profile: OnboardingProfile? = null,
) : OnboardingProfileStore {
    override fun loadProfile(): OnboardingProfile? = profile

    override fun saveProfile(profile: OnboardingProfile) {
        this.profile = profile
    }

    override fun clearProfile() {
        profile = null
    }
}

class OnboardingPersistenceUseCase(
    private val store: OnboardingProfileStore,
) {
    fun loadProfile(): OnboardingProfile? = store.loadProfile()

    fun saveProfile(profile: OnboardingProfile): OnboardingProfile {
        store.saveProfile(profile)
        return profile
    }

    fun saveValidated(result: OnboardingValidationResult): OnboardingProfile {
        check(result.valid) { "Cannot persist invalid onboarding profile." }
        val persisted = result.normalizedProfile.copy(onboardingComplete = true)
        store.saveProfile(persisted)
        return persisted
    }

    fun clearProfile() {
        store.clearProfile()
    }
}
