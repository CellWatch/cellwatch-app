package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileUiState
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileViewModel

/**
 * First-run profile capture. Android counterpart of
 * `OnboardingScreenViewController`.
 *
 * Same shared [OnboardingProfileViewModel], same component inventory, same
 * template, so the two platforms differ only where the platform forces it.
 */
class OnboardingScreen(
    context: Context,
    private val viewModel: OnboardingProfileViewModel,
    private val onComplete: () -> Unit,
) {

    private val nameField = Components.formField(context, "Full name")
    private val phoneField = Components.formField(context, "Phone (###-###-####)", InputType.TYPE_CLASS_PHONE)
    private val emailField = Components.formField(context, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val acknowledgeSwitch = Switch(context)
    private val feedbackLabel = Components.bodyText(context, "", muted = true)

    val view: View

    init {
        val acknowledgeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                Components.bodyText(context, "I acknowledge the FCC challenge sharing terms."),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(acknowledgeSwitch)
            minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
        }

        val saveButton = Components.primaryButton(context, "Save profile")
        saveButton.setOnClickListener {
            val submission = viewModel.submit()
            render(submission.state)
            if (submission.success) onComplete()
        }

        val scaffold = ScreenScaffold(context)
        scaffold.addContent(
            Components.bodyText(
                context,
                "Tell us who you are before starting measurements. These details accompany every submission.",
                muted = true,
            ),
            nameField,
            phoneField,
            emailField,
            acknowledgeRow,
            feedbackLabel,
        )
        scaffold.addActions(saveButton)
        view = scaffold

        nameField.onTextChanged { render(viewModel.onNameChanged(it), echoFields = false) }
        phoneField.onTextChanged { render(viewModel.onPhoneChanged(it), echoFields = false) }
        emailField.onTextChanged { render(viewModel.onEmailChanged(it), echoFields = false) }
        acknowledgeSwitch.setOnCheckedChangeListener { _, checked ->
            render(viewModel.onAcknowledgementChanged(checked), echoFields = false)
        }

        render(viewModel.loadPersistedProfile())
    }

    /**
     * @param echoFields whether to write state back into the fields. Suppressed
     * while typing: the view model normalises input, and assigning that back
     * mid-edit moves the caret to the end after every keystroke.
     */
    private fun render(state: OnboardingProfileUiState, echoFields: Boolean = true) {
        if (echoFields) {
            nameField.setText(state.name)
            phoneField.setText(state.phone)
            emailField.setText(state.email)
        }
        acknowledgeSwitch.isChecked = state.fccAcknowledged
        feedbackLabel.text = state.feedbackMessage
        feedbackLabel.setTextColor(
            if (state.feedbackIsError) Theme.Palette.WARNING else Theme.Palette.SUCCESS,
        )
    }
}

private fun EditText.onTextChanged(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = action(s?.toString().orEmpty())
    })
}

private fun TextView.setTextColorCompat(color: Int) = setTextColor(color)
