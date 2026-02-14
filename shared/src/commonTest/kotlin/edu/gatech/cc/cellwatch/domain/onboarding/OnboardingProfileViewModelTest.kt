package edu.gatech.cc.cellwatch.domain.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingProfileViewModelTest {
    @Test
    fun inlineFeedback_updatesAcrossInputEdits() {
        val viewModel = createViewModel()

        assertEquals("Complete the form and save your profile.", viewModel.currentState().feedbackMessage)
        assertFalse(viewModel.currentState().feedbackIsError)

        viewModel.onPhoneChanged("404")
        assertEquals("Phone should be 10 digits.", viewModel.currentState().feedbackMessage)
        assertTrue(viewModel.currentState().feedbackIsError)

        viewModel.onPhoneChanged("4045551212")
        viewModel.onEmailChanged("jane")
        assertEquals("Email appears incomplete.", viewModel.currentState().feedbackMessage)
        assertTrue(viewModel.currentState().feedbackIsError)

        viewModel.onEmailChanged("jane@example.com")
        viewModel.onNameChanged("Jane Doe")
        assertEquals("Please acknowledge FCC challenge sharing terms.", viewModel.currentState().feedbackMessage)
        assertTrue(viewModel.currentState().feedbackIsError)

        viewModel.onAcknowledgementChanged(true)
        assertEquals("Looks good. Tap Save Profile.", viewModel.currentState().feedbackMessage)
        assertFalse(viewModel.currentState().feedbackIsError)
        assertEquals("404-555-1212", viewModel.currentState().phone)
    }

    @Test
    fun submit_invalidProfile_returnsFailureAndFieldErrors() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("")
        viewModel.onPhoneChanged("123")
        viewModel.onEmailChanged("bad")
        viewModel.onAcknowledgementChanged(false)

        val submission = viewModel.submit()

        assertFalse(submission.success)
        assertTrue(submission.statusText.contains("Onboarding submit=FAILURE"))
        assertEquals("Fix validation errors and try again.", submission.state.feedbackMessage)
        assertTrue(submission.state.feedbackIsError)
        assertTrue(submission.state.fieldErrors.isNotEmpty())
    }

    @Test
    fun submit_validProfile_persistsNormalizedValuesAndReloadsForEdit() {
        val store = InMemoryOnboardingProfileStore()
        val persistence = OnboardingPersistenceUseCase(store)
        val viewModel = OnboardingProfileViewModel(OnboardingValidationUseCase(), persistence)

        viewModel.onNameChanged("  Jane Doe ")
        viewModel.onPhoneChanged("4045551212")
        viewModel.onEmailChanged("jane@example.com")
        viewModel.onAcknowledgementChanged(true)
        val firstSubmission = viewModel.submit()

        assertTrue(firstSubmission.success)
        assertEquals("Profile saved.", firstSubmission.state.feedbackMessage)
        assertEquals("Jane Doe", firstSubmission.state.name)
        assertEquals("404-555-1212", firstSubmission.state.phone)
        assertTrue(firstSubmission.statusText.contains("onboardingComplete=true"))

        val reopened = OnboardingProfileViewModel(OnboardingValidationUseCase(), persistence)
        val reopenedState = reopened.loadPersistedProfile()
        assertEquals("Jane Doe", reopenedState.name)
        assertEquals("404-555-1212", reopenedState.phone)
        assertEquals("jane@example.com", reopenedState.email)
        assertEquals("Saved profile loaded. You can edit and save again.", reopenedState.feedbackMessage)

        reopened.onPhoneChanged("4041112222")
        reopened.onEmailChanged("updated@example.com")
        reopened.onAcknowledgementChanged(true)
        val editSubmission = reopened.submit()
        assertTrue(editSubmission.success)
        assertTrue(editSubmission.statusText.contains("phone=404-111-2222"))
        assertTrue(editSubmission.statusText.contains("email=updated@example.com"))
    }

    private fun createViewModel(): OnboardingProfileViewModel {
        return OnboardingProfileViewModel(
            validationUseCase = OnboardingValidationUseCase(),
            persistenceUseCase = OnboardingPersistenceUseCase(
                store = InMemoryOnboardingProfileStore(),
            ),
        )
    }
}
