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
    /**
     * Carried from the consent step, where the real acknowledgement - the one
     * naming what the carrier releases to the FCC - is now asked.
     */
    private val acknowledged: Boolean,
    private val onComplete: () -> Unit,
) {

    private val nameField = Components.formField(context, "Full name")
    private val phoneField = Components.formField(context, "Phone (###-###-####)", InputType.TYPE_CLASS_PHONE)
    private val emailField = Components.formField(context, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val feedbackLabel = Components.bodyText(context, "", muted = true)

    val view: View

    init {
        // The acknowledgement is not asked here any more. It used to be a
        // single invented line - "I acknowledge the FCC challenge sharing
        // terms" - that linked to nothing and disclosed nothing. The consent
        // step now asks frozenApp's actual sentence, after showing what is
        // published, so repeating a checkbox here would be asking twice for
        // something already agreed.
        viewModel.onAcknowledgementChanged(acknowledged)

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
            feedbackLabel,
        )
        scaffold.addActions(saveButton)
        view = scaffold

        nameField.onTextChanged { render(viewModel.onNameChanged(it), echoFields = false) }
        phoneField.onTextChanged { render(viewModel.onPhoneChanged(it), echoFields = false) }
        emailField.onTextChanged { render(viewModel.onEmailChanged(it), echoFields = false) }

        render(viewModel.loadPersistedProfile())
        // Re-applied after load: loadPersistedProfile resets the state, which
        // would drop the acknowledgement carried in from the consent step and
        // leave Save permanently disabled.
        render(viewModel.onAcknowledgementChanged(acknowledged))
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
