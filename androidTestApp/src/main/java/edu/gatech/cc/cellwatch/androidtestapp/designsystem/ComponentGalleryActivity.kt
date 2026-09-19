package edu.gatech.cc.cellwatch.androidtestapp.designsystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.text.InputType

/**
 * Every component on one screen, for reviewing the visual baseline.
 *
 * Counterpart to the iOS gallery, so the two platforms can be compared
 * side by side rather than drifting apart a screen at a time. Launched
 * explicitly; it is not part of the product navigation graph.
 */
class ComponentGalleryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Component inventory"

        val scaffold = ScreenScaffold(this)
        scaffold.addContent(
            Components.bodyText(this, "The visual baseline. Spacing scale 4/8/12/16/24/32, palette from frozenApp."),
            Components.divider(this),

            Components.bodyText(this, "Metric rows", muted = true),
            Components.metricRow(this, "Latency", "45.3 ms"),
            Components.metricRow(this, "Download", "17.2 Mbps"),
            Components.metricRow(this, "Upload", "8.4 Mbps"),
            Components.divider(this),

            Components.bodyText(this, "Status cards", muted = true),
            Components.statusCard(this, "Measurement complete. Results saved and synced.", Components.StatusTone.SUCCESS),
            Components.statusCard(this, "3 measurements waiting to upload.", Components.StatusTone.NEUTRAL),
            Components.statusCard(this, "Cellular required. This measurement cannot be submitted.", Components.StatusTone.WARNING),
            Components.divider(this),

            Components.bodyText(this, "Form fields", muted = true),
            Components.formField(this, "Full name"),
            Components.formField(this, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            Components.divider(this),

            Components.bodyText(this, "Progress header", muted = true),
            Components.progressHeader(this, "Measuring download", 45),
            Components.divider(this),

            Components.bodyText(this, "List rows", muted = true),
            Components.listRow(this, "Measurement 19 Sep, 07:41", "Synced", "3 tests"),
            Components.divider(this),
            Components.listRow(this, "Measurement 18 Sep, 17:23", "Pending sync", "3 tests"),
            Components.divider(this),

            Components.bodyText(this, "Empty state", muted = true),
            Components.emptyState(this, "No measurements yet. Take one to see it here."),
        )
        scaffold.addActions(
            Components.primaryButton(this, "Primary action"),
            Components.secondaryButton(this, "Secondary action"),
        )
        setContentView(scaffold)
    }
}
