package edu.gatech.cc.cellwatch.domain.onboarding

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingPersistenceUseCaseTest {
    private val validationUseCase = OnboardingValidationUseCase()

    @Test
    fun inMemoryStore_roundTripsAndClears() {
        val useCase = OnboardingPersistenceUseCase(InMemoryOnboardingProfileStore())
        assertNull(useCase.loadProfile())

        val saved = useCase.saveProfile(
            OnboardingProfile(
                collectionMode = CollectionMode.TESTING,
                name = "Jane Doe",
                phone = "404-555-1212",
                email = "jane@example.com",
                fccAcknowledged = true,
                onboardingComplete = true,
            ),
        )

        assertEquals("Jane Doe", saved.name)
        assertEquals("jane@example.com", useCase.loadProfile()?.email)

        useCase.clearProfile()
        assertNull(useCase.loadProfile())
    }

    @Test
    fun saveValidated_persistsNormalizedCompletedProfile() {
        val useCase = OnboardingPersistenceUseCase(InMemoryOnboardingProfileStore())
        val validation = validationUseCase.validate(
            OnboardingProfile(
                collectionMode = CollectionMode.FCC_CHALLENGE,
                name = "  Jane Doe ",
                phone = "4045551212",
                email = "jane@example.com",
                fccAcknowledged = true,
            ),
        )

        val persisted = useCase.saveValidated(validation)

        assertTrue(persisted.onboardingComplete)
        assertEquals("Jane Doe", persisted.name)
        assertEquals("404-555-1212", useCase.loadProfile()?.phone)
    }

    @Test
    fun saveValidated_rejectsInvalidProfile() {
        val useCase = OnboardingPersistenceUseCase(InMemoryOnboardingProfileStore())
        val validation = validationUseCase.validate(
            OnboardingProfile(
                name = "",
                phone = "123",
                email = "bad",
                fccAcknowledged = false,
            ),
        )

        assertFailsWith<IllegalStateException> {
            useCase.saveValidated(validation)
        }
    }
}
