package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingValidationUseCaseTest {
    private val useCase = OnboardingValidationUseCase()

    @Test
    fun validate_requiresAllFields_forTestingMode() {
        val result = useCase.validate(
            OnboardingProfile(
                collectionMode = CollectionMode.TESTING,
                name = "",
                phone = "123",
                email = "bad",
                fccAcknowledged = false,
            ),
        )

        assertFalse(result.valid)
        assertTrue(result.fieldErrors.containsKey(OnboardingField.NAME))
        assertTrue(result.fieldErrors.containsKey(OnboardingField.PHONE))
        assertTrue(result.fieldErrors.containsKey(OnboardingField.EMAIL))
        assertTrue(result.fieldErrors.containsKey(OnboardingField.FCC_ACKNOWLEDGED))
    }

    @Test
    fun validate_normalizesAndCompletes_whenValid() {
        val result = useCase.validate(
            OnboardingProfile(
                collectionMode = CollectionMode.FCC_CHALLENGE,
                name = "  Jane Doe ",
                phone = "4045551212",
                email = "jane@example.com",
                fccAcknowledged = true,
            ),
        )

        assertTrue(result.valid)
        assertEquals("Jane Doe", result.normalizedProfile.name)
        assertEquals("404-555-1212", result.normalizedProfile.phone)
        assertEquals("jane@example.com", result.normalizedProfile.email)
        assertTrue(result.normalizedProfile.onboardingComplete)
    }
}
