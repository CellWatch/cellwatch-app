package edu.gatech.cc.cellwatch.domain.consent

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

data class ConsentUiState(
    val collectionMode: CollectionMode = CollectionMode.FCC_CHALLENGE,
    val acknowledged: Boolean = false,
    val statusMessage: String,
    val canContinue: Boolean,
) {
    val challengeSelected: Boolean get() = collectionMode == CollectionMode.FCC_CHALLENGE
}

/**
 * The consent step, before any contact details are asked for.
 *
 * The acknowledgement is only required in challenge mode: it is about the
 * carrier releasing customer information to the FCC, which does not happen if
 * nothing is submitted. Requiring it in testing mode would be asking for
 * agreement to something that will not occur.
 */
class ConsentViewModel {
    private var collectionMode: CollectionMode = CollectionMode.FCC_CHALLENGE
    private var acknowledged: Boolean = false

    fun currentState(): ConsentUiState = project()

    fun onCollectionModeChanged(mode: CollectionMode): ConsentUiState {
        collectionMode = mode
        return project()
    }

    fun onAcknowledgementChanged(value: Boolean): ConsentUiState {
        acknowledged = value
        return project()
    }

    private fun project(): ConsentUiState {
        val needsAcknowledgement = collectionMode == CollectionMode.FCC_CHALLENGE
        val satisfied = !needsAcknowledgement || acknowledged
        return ConsentUiState(
            collectionMode = collectionMode,
            acknowledged = acknowledged,
            statusMessage = when {
                satisfied && needsAcknowledgement -> ConsentCopy.CHANGE_LATER
                satisfied -> ConsentCopy.TESTING_SUMMARY
                else -> ConsentCopy.ACKNOWLEDGEMENT_REQUIRED
            },
            canContinue = satisfied,
        )
    }
}
