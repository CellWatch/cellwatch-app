package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.view.View
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.domain.app.ExportDocument
import edu.gatech.cc.cellwatch.domain.export.ExportCopy
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeCopy

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
        val fccButton = Components.primaryButton(context, ExportCopy.EXPORT_FCC_FILE).apply {
            setOnClickListener {
                status.update(ExportCopy.PREPARING_FCC_FILE, Components.StatusTone.NEUTRAL)
                buildFcc { document -> deliver(document, ExportCopy.FCC_FILE_TITLE) }
            }
        }
        val extendedButton = Components.secondaryButton(context, ExportCopy.EXPORT_FULL_DATA).apply {
            setOnClickListener {
                status.update(ExportCopy.PREPARING_FULL_EXPORT, Components.StatusTone.NEUTRAL)
                buildExtended { document -> deliver(document, ExportCopy.FULL_EXPORT_TITLE) }
            }
        }
        val backButton = Components.secondaryButton(context, MapHomeCopy.BACK_TO_MAP).apply {
            setOnClickListener { onBack() }
        }

        scaffold.addContent(
            Components.sectionHeader(context, ExportCopy.FCC_FILE_TITLE),
            Components.bodyText(
                context,
                ExportCopy.FCC_FILE_DESCRIPTION,
                muted = true,
            ),
            Components.divider(context),
            Components.sectionHeader(context, ExportCopy.FULL_EXPORT_TITLE),
            Components.bodyText(
                context,
                ExportCopy.FULL_EXPORT_DESCRIPTION,
                muted = true,
            ),
            status,
        )
        scaffold.addActions(fccButton, extendedButton, backButton)
        status.update(ExportCopy.CHOOSE_FORMAT, Components.StatusTone.NEUTRAL)
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
            ExportCopy.ready(label, document.recordCount),
            tone,
        )
        save(document)
    }

    fun onSaved(fileName: String) {
        status.update(ExportCopy.savedTo(fileName), Components.StatusTone.SUCCESS)
    }

    fun onSaveFailed(reason: String) {
        status.update(ExportCopy.notSaved(reason), Components.StatusTone.WARNING)
    }
}
