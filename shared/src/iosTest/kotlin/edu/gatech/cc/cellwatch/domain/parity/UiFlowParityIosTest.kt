package edu.gatech.cc.cellwatch.domain.parity

import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewParityContract
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartPreflightParityContract
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileViewParityContract
import edu.gatech.cc.cellwatch.domain.settings.SettingsProfileViewParityContract
import edu.gatech.cc.cellwatch.domain.sync.PendingSyncParityContract
import kotlin.test.Test

class UiFlowParityIosTest {
    @Test
    fun onboardingProfile_saveLoadEditRoundTrip_matchesContract() {
        OnboardingProfileViewParityContract.assertSaveLoadEditRoundTrip()
    }

    @Test
    fun settingsProfile_modeAndContactUpdates_matchContract() {
        SettingsProfileViewParityContract.assertLoadAndPersistModeAndContactUpdates()
    }

    @Test
    fun measurementStartPreflight_confirmPath_matchesContract() {
        MeasurementStartPreflightParityContract.assertChallengeConfirmPathAndPresenterText()
    }

    @Test
    fun pendingSyncRetry_lifecycle_matchesContract() {
        PendingSyncParityContract.assertPendingRetryLifecycle()
    }

    @Test
    fun measurementRun_progressAndReadModel_matchContract() {
        MeasurementRunViewParityContract.assertLegacyProgressionAndReadModel()
    }
}
