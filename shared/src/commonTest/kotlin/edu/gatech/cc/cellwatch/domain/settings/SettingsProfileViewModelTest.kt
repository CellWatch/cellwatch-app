package edu.gatech.cc.cellwatch.domain.settings

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.onboarding.InMemoryOnboardingProfileStore
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingPersistenceUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfile
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingValidationUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsProfileViewModelTest {
    private val store = InMemoryOnboardingProfileStore()
    private val persistence = OnboardingPersistenceUseCase(store)
    private val validation = OnboardingValidationUseCase()

    @Test
    fun loadPersistedProfile_returnsDefaultWhenEmpty() {
        val viewModel = SettingsProfileViewModel(validation, persistence)
        val state = viewModel.loadPersistedProfile()

        assertEquals(CollectionMode.TESTING, state.collectionMode)
        assertEquals("", state.name)
        assertEquals("Update your settings and save.", state.feedbackMessage)
    }

    @Test
    fun submit_persistsUpdatedModeAndContactFields() {
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
        val viewModel = SettingsProfileViewModel(validation, persistence)
        viewModel.loadPersistedProfile()
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

    @Test
    fun submit_invalidDataReturnsFieldErrors() {
        val viewModel = SettingsProfileViewModel(validation, persistence)
        viewModel.loadPersistedProfile()
        viewModel.onCollectionModeChanged(CollectionMode.FCC_CHALLENGE)
        viewModel.onNameChanged("Jane Doe")
        viewModel.onPhoneChanged("404")
        viewModel.onEmailChanged("bad")
        viewModel.onAcknowledgementChanged(false)

        val submission = viewModel.submit()

        assertFalse(submission.success)
        assertTrue(submission.statusText.contains("Settings save=FAILURE"))
        assertTrue(submission.state.fieldErrors.isNotEmpty())
    }
}
