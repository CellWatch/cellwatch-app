package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.core.util.ContactInfoValidator
import edu.gatech.cc.cellwatch.domain.profile.ProfileCopy

class OnboardingValidationUseCase {
    fun validate(profile: OnboardingProfile): OnboardingValidationResult {
        val normalizedName = profile.name.trim()
        val normalizedPhone = ContactInfoValidator.asValidPhoneNumber(profile.phone).orEmpty()
        val normalizedEmail = ContactInfoValidator.asValidEmail(profile.email).orEmpty()

        val errors = linkedMapOf<OnboardingField, String>()
        if (normalizedName.isBlank()) {
            errors[OnboardingField.NAME] = ProfileCopy.NAME_REQUIRED
        }
        if (normalizedPhone.isBlank()) {
            errors[OnboardingField.PHONE] = ProfileCopy.PHONE_INVALID
        }
        if (normalizedEmail.isBlank()) {
            errors[OnboardingField.EMAIL] = ProfileCopy.EMAIL_INVALID
        }
        if (!profile.fccAcknowledged) {
            errors[OnboardingField.FCC_ACKNOWLEDGED] = ProfileCopy.ACKNOWLEDGEMENT_REQUIRED
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
