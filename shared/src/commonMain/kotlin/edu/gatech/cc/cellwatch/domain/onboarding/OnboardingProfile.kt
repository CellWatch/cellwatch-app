package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

data class OnboardingProfile(
    val collectionMode: CollectionMode = CollectionMode.TESTING,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val fccAcknowledged: Boolean = false,
    val onboardingComplete: Boolean = false,
)

enum class OnboardingField {
    NAME,
    PHONE,
    EMAIL,
    FCC_ACKNOWLEDGED,
}

data class OnboardingValidationResult(
    val valid: Boolean,
    val normalizedProfile: OnboardingProfile,
    val fieldErrors: Map<OnboardingField, String>,
)
