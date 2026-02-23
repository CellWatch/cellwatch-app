package edu.gatech.cc.cellwatch.domain.onboarding

import kotlin.test.assertEquals
import kotlin.test.assertTrue

object OnboardingProfileViewParityContract {
    fun assertSaveLoadEditRoundTrip() {
        val store = InMemoryOnboardingProfileStore()
        val persistence = OnboardingPersistenceUseCase(store)
        val viewModel = OnboardingProfileViewModel(OnboardingValidationUseCase(), persistence)

        viewModel.onNameChanged("  Jane Doe ")
        viewModel.onPhoneChanged("4045551212")
        viewModel.onEmailChanged("jane@example.com")
        viewModel.onAcknowledgementChanged(true)
        val firstSubmission = viewModel.submit()

        assertTrue(firstSubmission.success)
        assertEquals("Jane Doe", firstSubmission.state.name)
        assertEquals("404-555-1212", firstSubmission.state.phone)
        assertEquals("Profile saved.", firstSubmission.state.feedbackMessage)
        assertTrue(firstSubmission.statusText.contains("onboardingComplete=true"))

        val reopened = OnboardingProfileViewModel(OnboardingValidationUseCase(), persistence)
        val reopenedState = reopened.loadPersistedProfile()
        assertEquals("Jane Doe", reopenedState.name)
        assertEquals("404-555-1212", reopenedState.phone)
        assertEquals("jane@example.com", reopenedState.email)

        reopened.onPhoneChanged("4041112222")
        reopened.onEmailChanged("updated@example.com")
        reopened.onAcknowledgementChanged(true)
        val edited = reopened.submit()
        assertTrue(edited.success)
        assertTrue(edited.statusText.contains("phone=404-111-2222"))
        assertTrue(edited.statusText.contains("email=updated@example.com"))
    }
}
