package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.domain.consent.ConsentCopy

/**
 * What happens to the data, shown before anything is collected.
 *
 * Copy is frozenApp's, verbatim, via the shared [ConsentCopy].
 */
class DataUseScreen(
    private val context: Context,
    private val onContinue: () -> Unit,
) {

    private val scaffold = ScreenScaffold(context)

    val view: View get() = scaffold

    init {
        val policyButton = Components.secondaryButton(context, ConsentCopy.PRIVACY_POLICY_LABEL).apply {
            setOnClickListener {
                // Opened externally rather than embedded: the policy is a
                // published document with its own address, and a user should
                // be able to see that address.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(ConsentCopy.PRIVACY_POLICY_URL)),
                    )
                }
            }
        }
        val continueButton = Components.primaryButton(context, ConsentCopy.CONTINUE).apply {
            setOnClickListener { onContinue() }
        }

        scaffold.addContent(
            Components.sectionHeader(context, ConsentCopy.DATA_USE_TITLE),
            Components.bodyText(context, ConsentCopy.DATA_USE_SHARED),
            Components.bodyText(context, ConsentCopy.DATA_USE_RESEARCH),
            Components.bodyText(context, ConsentCopy.DATA_USE_POLICY, muted = true),
            Components.bodyText(context, ConsentCopy.PRIVACY_POLICY_URL, muted = true),
        )
        scaffold.addActions(continueButton, policyButton)
    }
}
