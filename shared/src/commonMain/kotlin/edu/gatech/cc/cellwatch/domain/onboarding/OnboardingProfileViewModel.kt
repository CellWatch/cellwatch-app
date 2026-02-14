package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

data class OnboardingProfileUiState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val fccAcknowledged: Boolean = false,
    val feedbackMessage: String = "Complete the form and save your profile.",
    val feedbackIsError: Boolean = false,
    val fieldErrors: Map<OnboardingField, String> = emptyMap(),
)

data class OnboardingProfileSubmission(
    val success: Boolean,
    val statusText: String,
    val state: OnboardingProfileUiState,
)

class OnboardingProfileViewModel(
    private val validationUseCase: OnboardingValidationUseCase,
    private val persistenceUseCase: OnboardingPersistenceUseCase,
    private val collectionMode: CollectionMode,
) {
    constructor(
        validationUseCase: OnboardingValidationUseCase,
        persistenceUseCase: OnboardingPersistenceUseCase,
    ) : this(
        validationUseCase = validationUseCase,
        persistenceUseCase = persistenceUseCase,
        collectionMode = CollectionMode.TESTING,
    )

    private var uiState = OnboardingProfileUiState()

    fun currentState(): OnboardingProfileUiState = uiState

    fun loadPersistedProfile(): OnboardingProfileUiState {
        val persisted = persistenceUseCase.loadProfile()
        uiState = if (persisted == null) {
            OnboardingProfileUiState()
        } else {
            OnboardingProfileUiState(
                name = persisted.name,
                phone = formatPhoneDisplay(persisted.phone),
                email = persisted.email,
                fccAcknowledged = persisted.fccAcknowledged,
                feedbackMessage = "Saved profile loaded. You can edit and save again.",
                feedbackIsError = false,
            )
        }
        return uiState
    }

    fun onNameChanged(name: String): OnboardingProfileUiState {
        uiState = uiState.copy(name = name)
        return applyInlineFeedback()
    }

    fun onPhoneChanged(phone: String): OnboardingProfileUiState {
        uiState = uiState.copy(phone = formatPhoneDisplay(phone))
        return applyInlineFeedback()
    }

    fun onEmailChanged(email: String): OnboardingProfileUiState {
        uiState = uiState.copy(email = email)
        return applyInlineFeedback()
    }

    fun onAcknowledgementChanged(acknowledged: Boolean): OnboardingProfileUiState {
        uiState = uiState.copy(fccAcknowledged = acknowledged)
        return applyInlineFeedback()
    }

    fun submit(): OnboardingProfileSubmission {
        val profile = OnboardingProfile(
            collectionMode = collectionMode,
            name = uiState.name,
            phone = uiState.phone,
            email = uiState.email,
            fccAcknowledged = uiState.fccAcknowledged,
            onboardingComplete = false,
        )
        val result = validationUseCase.validate(profile)
        return if (result.valid) {
            val persisted = persistenceUseCase.saveValidated(result)
            uiState = OnboardingProfileUiState(
                name = persisted.name,
                phone = persisted.phone,
                email = persisted.email,
                fccAcknowledged = persisted.fccAcknowledged,
                feedbackMessage = "Profile saved.",
                feedbackIsError = false,
            )
            OnboardingProfileSubmission(
                success = true,
                statusText = buildSuccessStatus(persisted),
                state = uiState,
            )
        } else {
            val errors = result.fieldErrors.entries.joinToString(separator = "; ") { "${it.key}:${it.value}" }
            uiState = uiState.copy(
                feedbackMessage = "Fix validation errors and try again.",
                feedbackIsError = true,
                fieldErrors = result.fieldErrors,
            )
            OnboardingProfileSubmission(
                success = false,
                statusText = "Onboarding submit=FAILURE\nerrors=$errors",
                state = uiState,
            )
        }
    }

    private fun applyInlineFeedback(): OnboardingProfileUiState {
        val name = uiState.name.trim()
        val phoneDigits = uiState.phone.filter(Char::isDigit)
        val email = uiState.email.trim()
        val feedback = when {
            phoneDigits.isNotEmpty() && phoneDigits.length < 10 ->
                "Phone should be 10 digits."
            email.isNotEmpty() && !email.contains("@") ->
                "Email appears incomplete."
            name.isEmpty() || phoneDigits.isEmpty() || email.isEmpty() ->
                "Complete the form and save your profile."
            !uiState.fccAcknowledged ->
                "Please acknowledge FCC challenge sharing terms."
            else ->
                "Looks good. Tap Save Profile."
        }
        val isError = feedback != "Complete the form and save your profile." &&
            feedback != "Looks good. Tap Save Profile."
        uiState = uiState.copy(
            feedbackMessage = feedback,
            feedbackIsError = isError,
            fieldErrors = emptyMap(),
        )
        return uiState
    }

    private fun buildSuccessStatus(persisted: OnboardingProfile): String {
        return "Onboarding submit=SUCCESS\n" +
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
