package edu.gatech.cc.cellwatch.domain.settings

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingField
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingPersistenceUseCase
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfile
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingValidationUseCase

data class SettingsProfileUiState(
    val collectionMode: CollectionMode = CollectionMode.TESTING,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val fccAcknowledged: Boolean = false,
    val feedbackMessage: String = "Update your settings and save.",
    val feedbackIsError: Boolean = false,
    val fieldErrors: Map<OnboardingField, String> = emptyMap(),
)

data class SettingsProfileSubmission(
    val success: Boolean,
    val statusText: String,
    val state: SettingsProfileUiState,
)

class SettingsProfileViewModel(
    private val validationUseCase: OnboardingValidationUseCase,
    private val persistenceUseCase: OnboardingPersistenceUseCase,
) {
    private var uiState = SettingsProfileUiState()
    private var onboardingComplete = false

    fun currentState(): SettingsProfileUiState = uiState

    fun loadPersistedProfile(): SettingsProfileUiState {
        val persisted = persistenceUseCase.loadProfile()
        uiState = if (persisted == null) {
            SettingsProfileUiState()
        } else {
            onboardingComplete = persisted.onboardingComplete
            SettingsProfileUiState(
                collectionMode = persisted.collectionMode,
                name = persisted.name,
                phone = formatPhoneDisplay(persisted.phone),
                email = persisted.email,
                fccAcknowledged = persisted.fccAcknowledged,
                feedbackMessage = "Saved settings loaded.",
                feedbackIsError = false,
            )
        }
        return uiState
    }

    fun onCollectionModeChanged(collectionMode: CollectionMode): SettingsProfileUiState {
        uiState = uiState.copy(collectionMode = collectionMode)
        return applyInlineFeedback()
    }

    fun onNameChanged(name: String): SettingsProfileUiState {
        uiState = uiState.copy(name = name)
        return applyInlineFeedback()
    }

    fun onPhoneChanged(phone: String): SettingsProfileUiState {
        uiState = uiState.copy(phone = formatPhoneDisplay(phone))
        return applyInlineFeedback()
    }

    fun onEmailChanged(email: String): SettingsProfileUiState {
        uiState = uiState.copy(email = email)
        return applyInlineFeedback()
    }

    fun onAcknowledgementChanged(acknowledged: Boolean): SettingsProfileUiState {
        uiState = uiState.copy(fccAcknowledged = acknowledged)
        return applyInlineFeedback()
    }

    fun submit(): SettingsProfileSubmission {
        val profile = OnboardingProfile(
            collectionMode = uiState.collectionMode,
            name = uiState.name,
            phone = uiState.phone,
            email = uiState.email,
            fccAcknowledged = uiState.fccAcknowledged,
            onboardingComplete = onboardingComplete,
        )
        val result = validationUseCase.validate(profile)
        return if (result.valid) {
            val normalized = result.normalizedProfile.copy(onboardingComplete = true)
            val persisted = persistenceUseCase.saveProfile(normalized)
            onboardingComplete = persisted.onboardingComplete
            uiState = SettingsProfileUiState(
                collectionMode = persisted.collectionMode,
                name = persisted.name,
                phone = persisted.phone,
                email = persisted.email,
                fccAcknowledged = persisted.fccAcknowledged,
                feedbackMessage = "Settings saved.",
                feedbackIsError = false,
            )
            SettingsProfileSubmission(
                success = true,
                statusText = buildSuccessStatus(persisted),
                state = uiState,
            )
        } else {
            val errors = result.fieldErrors.entries.joinToString(separator = "; ") { "${it.key}:${it.value}" }
            uiState = uiState.copy(
                feedbackMessage = "Fix validation errors and save again.",
                feedbackIsError = true,
                fieldErrors = result.fieldErrors,
            )
            SettingsProfileSubmission(
                success = false,
                statusText = "Settings save=FAILURE\nerrors=$errors",
                state = uiState,
            )
        }
    }

    private fun applyInlineFeedback(): SettingsProfileUiState {
        val name = uiState.name.trim()
        val phoneDigits = uiState.phone.filter(Char::isDigit)
        val email = uiState.email.trim()
        val feedback = when {
            phoneDigits.isNotEmpty() && phoneDigits.length < 10 ->
                "Phone should be 10 digits."
            email.isNotEmpty() && !email.contains("@") ->
                "Email appears incomplete."
            name.isEmpty() || phoneDigits.isEmpty() || email.isEmpty() ->
                "Update your settings and save."
            !uiState.fccAcknowledged ->
                "Please acknowledge FCC challenge sharing terms."
            else ->
                "Looks good. Tap Save Settings."
        }
        val isError = feedback != "Update your settings and save." &&
            feedback != "Looks good. Tap Save Settings."
        uiState = uiState.copy(
            feedbackMessage = feedback,
            feedbackIsError = isError,
            fieldErrors = emptyMap(),
        )
        return uiState
    }

    private fun buildSuccessStatus(persisted: OnboardingProfile): String {
        return "Settings save=SUCCESS\n" +
            "collectionMode=${persisted.collectionMode}\n" +
            "name=${persisted.name}\n" +
            "phone=${persisted.phone}\n" +
            "email=${persisted.email}\n" +
            "ack=${persisted.fccAcknowledged}\n" +
            "onboardingComplete=${persisted.onboardingComplete}\n" +
            "persisted=true"
    }

    private fun formatPhoneDisplay(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        val limited = digits.take(10)
        return when {
            limited.length <= 3 -> limited
            limited.length <= 6 -> "${limited.take(3)}-${limited.drop(3)}"
            else -> "${limited.take(3)}-${limited.drop(3).take(3)}-${limited.drop(6)}"
        }
    }
}
