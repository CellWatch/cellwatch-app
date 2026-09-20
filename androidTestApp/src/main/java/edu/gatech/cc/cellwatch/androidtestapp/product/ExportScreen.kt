package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.view.View
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.domain.app.ExportDocument

/**
 * Write the stored measurements to a file. Android counterpart of
 * `ExportScreenViewController`.
 *
 * Two formats rather than one, because they answer different questions and
 * neither covers the other: see the copy below, which is the whole reason the
 * screen has two buttons.
 */
class ExportScreen(
    private val context: Context,
    private val buildFcc: ((ExportDocument) -> Unit) -> Unit,
    private val buildExtended: ((ExportDocument) -> Unit) -> Unit,
    private val save: (ExportDocument) -> Unit,
    private val onBack: () -> Unit,
) {

    private val scaffold = ScreenScaffold(context)
    private val status = Components.StatusCardView(context)

    val view: View get() = scaffold

    init {
        val fccButton = Components.primaryButton(context, "Export FCC submission file").apply {
            setOnClickListener {
                status.update("Preparing FCC file…", Components.StatusTone.NEUTRAL)
                buildFcc { document -> deliver(document, "FCC submission file") }
            }
        }
        val extendedButton = Components.secondaryButton(context, "Export full data").apply {
            setOnClickListener {
                status.update("Preparing full export…", Components.StatusTone.NEUTRAL)
                buildExtended { document -> deliver(document, "Full export") }
            }
        }
        val backButton = Components.secondaryButton(context, "Back to map").apply {
            setOnClickListener { onBack() }
        }

        scaffold.addContent(
            Components.sectionHeader(context, "FCC submission file"),
            Components.bodyText(
                context,
                "The format the FCC accepts for a challenge submission. It contains only " +
                    "measurements that qualified: taken over cellular, complete, and with " +
                    "submission turned on. If none qualified, this file will be empty.",
                muted = true,
            ),
            Components.divider(context),
            Components.sectionHeader(context, "Full export"),
            Components.bodyText(
                context,
                "Everything this device recorded, including measurements the FCC file leaves " +
                    "out and the reason each one was left out. Also records what the device " +
                    "could not report — missing permissions, unavailable telephony — which the " +
                    "FCC format has no field for.",
                muted = true,
            ),
            status,
        )
        scaffold.addActions(fccButton, extendedButton, backButton)
        status.update("Choose a format to export.", Components.StatusTone.NEUTRAL)
    }

    private fun deliver(document: ExportDocument, label: String) {
        // An empty FCC file is a legitimate outcome, not an error, but saying
        // so up front is kinder than handing over a file with nothing in it.
        val tone = if (document.recordCount == 0) {
            Components.StatusTone.WARNING
        } else {
            Components.StatusTone.SUCCESS
        }
        status.update(
            "$label ready: ${document.recordCount} record(s). Choose where to save it.",
            tone,
        )
        save(document)
    }

    fun onSaved(fileName: String) {
        status.update("Saved to $fileName.", Components.StatusTone.SUCCESS)
    }

    fun onSaveFailed(reason: String) {
        status.update("Export not saved: $reason", Components.StatusTone.WARNING)
    }
}
