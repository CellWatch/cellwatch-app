package edu.gatech.cc.cellwatch.domain.settings

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.onboarding.InMemoryOnboardingProfileStore
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingPersistenceUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfile
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingValidationUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object SettingsProfileViewParityContract {
    fun assertLoadAndPersistModeAndContactUpdates() {
        val store = InMemoryOnboardingProfileStore()
        val persistence = OnboardingPersistenceUseCase(store)
        persistence.saveProfile(
            OnboardingProfile(
                collectionMode = CollectionMode.TESTING,
                name = "Jane Doe",
                phone = "404-555-1212",
                email = "jane@example.com",
                fccAcknowledged = true,
                onboardingComplete = true,
            ),
        )

        val viewModel = SettingsProfileViewModel(OnboardingValidationUseCase(), persistence)
        val loaded = viewModel.loadPersistedProfile()
        assertEquals(CollectionMode.TESTING, loaded.collectionMode)

        viewModel.onCollectionModeChanged(CollectionMode.FCC_CHALLENGE)
        viewModel.onPhoneChanged("4041112222")
        viewModel.onEmailChanged("updated@example.com")
        viewModel.onAcknowledgementChanged(true)

        val submission = viewModel.submit()
        val persisted = persistence.loadProfile()
        assertTrue(submission.success)
        assertEquals(CollectionMode.FCC_CHALLENGE, persisted?.collectionMode)
        assertEquals("404-111-2222", persisted?.phone)
        assertEquals("updated@example.com", persisted?.email)
        assertTrue(persisted?.onboardingComplete == true)
    }
}
