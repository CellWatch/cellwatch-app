package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp
import edu.gatech.cc.cellwatch.domain.app.ProductDiagnostics
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeCopy
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingCopy
import edu.gatech.cc.cellwatch.domain.profile.ProfileCopy
import edu.gatech.cc.cellwatch.domain.settings.SettingsCopy
import edu.gatech.cc.cellwatch.domain.settings.SettingsProfileUiState
import edu.gatech.cc.cellwatch.domain.settings.SettingsProfileViewModel

/**
 * Edit the profile, choose the collection mode, and see what the app is
 * talking to. Android counterpart of `SettingsScreenViewController`.
 *
 * On the same shared [SettingsProfileViewModel] the harness already uses, so
 * validation and phone formatting behave identically in both.
 */
class SettingsScreen(
    private val context: Context,
    private val viewModel: SettingsProfileViewModel,
    private val diagnosticsProvider: ((ProductDiagnostics) -> Unit) -> Unit,
    private val onSaved: () -> Unit,
    private val onBack: () -> Unit,
) {

    private val scaffold = ScreenScaffold(context)
    private val nameField = Components.formField(context, OnboardingCopy.FULL_NAME)
    private val phoneField = Components.formField(context, OnboardingCopy.PHONE_HINT, InputType.TYPE_CLASS_PHONE)
    private val emailField = Components.formField(context, OnboardingCopy.EMAIL, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val acknowledgeSwitch = Switch(context)
    private val challengeSwitch = Switch(context)
    private val feedback = Components.bodyText(context, "", muted = true)
    private val diagnosticsText = Components.bodyText(context, SettingsCopy.LOADING, muted = true)

    /** Guards the text watchers while the view model writes values back. */
    private var rendering = false

    val view: View get() = scaffold

    init {
        val saveButton = Components.primaryButton(context, SettingsCopy.SAVE_SETTINGS).apply {
            setOnClickListener {
                val submission = viewModel.submit()
                render(submission.state)
                if (submission.success) onSaved()
            }
        }
        val backButton = Components.secondaryButton(context, MapHomeCopy.BACK_TO_MAP).apply {
            setOnClickListener { onBack() }
        }

        nameField.watch { render(viewModel.onNameChanged(it)) }
        phoneField.watch { render(viewModel.onPhoneChanged(it)) }
        emailField.watch { render(viewModel.onEmailChanged(it)) }
        acknowledgeSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) render(viewModel.onAcknowledgementChanged(checked))
        }
        challengeSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            render(
                viewModel.onCollectionModeChanged(
                    if (checked) CollectionMode.FCC_CHALLENGE else CollectionMode.TESTING,
                ),
            )
        }

        scaffold.addContent(
            Components.bodyText(
                context,
                SettingsCopy.DETAILS_NOTE,
                muted = true,
            ),
            nameField,
            phoneField,
            emailField,
            switchRow(ProfileCopy.FCC_ACK_LABEL, acknowledgeSwitch),
            Components.divider(context),
            switchRow(SettingsCopy.SUBMIT_TO_CHALLENGE, challengeSwitch),
            Components.bodyText(
                context,
                SettingsCopy.SUBMIT_NOTE,
                muted = true,
            ),
            feedback,
            Components.divider(context),
            Components.sectionHeader(context, SettingsCopy.ABOUT_THIS_INSTALL),
            diagnosticsText,
        )
        scaffold.addActions(saveButton, backButton)

        render(viewModel.loadPersistedProfile())
        diagnosticsProvider { diagnostics -> renderDiagnostics(diagnostics) }
    }

    private fun switchRow(label: String, control: Switch): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
        addView(
            Components.bodyText(context, label),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(control)
    }

    private fun render(state: SettingsProfileUiState) {
        rendering = true
        if (nameField.text.toString() != state.name) nameField.setText(state.name)
        if (phoneField.text.toString() != state.phone) phoneField.setText(state.phone)
        if (emailField.text.toString() != state.email) emailField.setText(state.email)
        acknowledgeSwitch.isChecked = state.fccAcknowledged
        challengeSwitch.isChecked = state.collectionMode == CollectionMode.FCC_CHALLENGE
        feedback.text = state.feedbackMessage
        feedback.setTextColor(
            if (state.feedbackIsError) Theme.Palette.WARNING else Theme.Palette.TEXT_SECONDARY,
        )
        rendering = false
    }

    private fun renderDiagnostics(d: ProductDiagnostics) {
        diagnosticsText.text = buildString {
            appendLine("${d.appName} ${d.appVersion}")
            appendLine(SettingsCopy.deviceId(d.deviceId))
            appendLine(SettingsCopy.measurementServer(d.msakMode, d.msakEndpoint))
            append(SettingsCopy.uploadTarget(d.supabaseMode))
        }
    }

    private fun android.widget.EditText.watch(onChanged: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // Re-entrancy guard: render() writes into these fields, and
                // without it every programmatic set re-enters the view model.
                if (!rendering) onChanged(s?.toString().orEmpty())
            }
        })
    }
}
