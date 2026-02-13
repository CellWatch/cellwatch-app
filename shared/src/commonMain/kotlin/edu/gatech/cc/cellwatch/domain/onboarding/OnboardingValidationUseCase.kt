package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.core.util.ContactInfoValidator

class OnboardingValidationUseCase {
    fun validate(profile: OnboardingProfile): OnboardingValidationResult {
        val normalizedName = profile.name.trim()
        val normalizedPhone = ContactInfoValidator.asValidPhoneNumber(profile.phone).orEmpty()
        val normalizedEmail = ContactInfoValidator.asValidEmail(profile.email).orEmpty()

        val errors = linkedMapOf<OnboardingField, String>()
        if (normalizedName.isBlank()) {
            errors[OnboardingField.NAME] = "Name is required."
        }
        if (normalizedPhone.isBlank()) {
            errors[OnboardingField.PHONE] = "Phone must be 10 digits (###-###-####)."
        }
        if (normalizedEmail.isBlank()) {
            errors[OnboardingField.EMAIL] = "Email is invalid."
        }
        if (!profile.fccAcknowledged) {
            errors[OnboardingField.FCC_ACKNOWLEDGED] = "FCC acknowledgement is required."
        }

        val normalizedProfile = profile.copy(
            name = normalizedName,
            phone = normalizedPhone,
            email = normalizedEmail,
            onboardingComplete = errors.isEmpty(),
        )
        return OnboardingValidationResult(
            valid = errors.isEmpty(),
            normalizedProfile = normalizedProfile,
            fieldErrors = errors,
        )
    }
}
