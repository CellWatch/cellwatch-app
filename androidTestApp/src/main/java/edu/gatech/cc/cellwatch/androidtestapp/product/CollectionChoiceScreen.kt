package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp
import edu.gatech.cc.cellwatch.domain.consent.ConsentCopy
import edu.gatech.cc.cellwatch.domain.consent.ConsentUiState
import edu.gatech.cc.cellwatch.domain.consent.ConsentViewModel
import edu.gatech.cc.cellwatch.domain.model.CollectionMode

/**
 * Collection mode and the FCC acknowledgement. Copy is frozenApp's, verbatim.
 */
class CollectionChoiceScreen(
    private val context: Context,
    private val viewModel: ConsentViewModel,
    private val onContinue: (CollectionMode, Boolean) -> Unit,
) {

    private val scaffold = ScreenScaffold(context)
    private val challengeSwitch = Switch(context)
    private val acknowledgeSwitch = Switch(context)
    private val acknowledgeRow = LinearLayout(context)
    private val fccInfo = Components.bodyText(context, ConsentCopy.FCC_INFO_DESCRIPTION, muted = true)
    private val fccHeader = Components.sectionHeader(context, ConsentCopy.FCC_INFO_TITLE)
    private val modeDetail = Components.bodyText(context, "", muted = true)
    private val status = Components.bodyText(context, "", muted = true)
    private val continueButton = Components.primaryButton(context, "Continue")

    private var rendering = false

    val view: View get() = scaffold

    init {
        challengeSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            render(
                viewModel.onCollectionModeChanged(
                    if (checked) CollectionMode.FCC_CHALLENGE else CollectionMode.TESTING,
                ),
            )
        }
        acknowledgeSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) render(viewModel.onAcknowledgementChanged(checked))
        }
        continueButton.setOnClickListener {
            val state = viewModel.currentState()
            if (state.canContinue) onContinue(state.collectionMode, state.acknowledged)
        }

        buildRow(acknowledgeRow, ConsentCopy.FCC_ACKNOWLEDGEMENT, acknowledgeSwitch)
        val modeRow = LinearLayout(context)
        buildRow(modeRow, ConsentCopy.CHALLENGE_MODE_TITLE, challengeSwitch)

        scaffold.addContent(
            Components.sectionHeader(context, ConsentCopy.COLLECTION_MODE_TITLE),
            Components.bodyText(context, ConsentCopy.COLLECTION_MODE_DESCRIPTION),
            Components.divider(context),
            modeRow,
            modeDetail,
            Components.divider(context),
            fccHeader,
            fccInfo,
            acknowledgeRow,
            status,
        )
        scaffold.addActions(continueButton)
        render(viewModel.currentState())
    }

    private fun buildRow(row: LinearLayout, label: String, control: Switch) {
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
        row.addView(
            Components.bodyText(context, label),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(control)
    }

    private fun render(state: ConsentUiState) {
        rendering = true
        challengeSwitch.isChecked = state.challengeSelected
        acknowledgeSwitch.isChecked = state.acknowledged
        modeDetail.text = if (state.challengeSelected) {
            ConsentCopy.CHALLENGE_MODE_DESCRIPTION
        } else {
            ConsentCopy.TESTING_MODE_DESCRIPTION
        }
        // The FCC section is about what is sent to the FCC; in testing mode
        // nothing is, so asking for the acknowledgement would be asking about
        // something that will not happen.
        val fccRelevant = state.challengeSelected
        fccHeader.visibility = if (fccRelevant) View.VISIBLE else View.GONE
        fccInfo.visibility = if (fccRelevant) View.VISIBLE else View.GONE
        acknowledgeRow.visibility = if (fccRelevant) View.VISIBLE else View.GONE
        status.text = state.statusMessage
        continueButton.isEnabled = state.canContinue
        continueButton.alpha = if (state.canContinue) 1.0f else 0.5f
        rendering = false
    }
}
