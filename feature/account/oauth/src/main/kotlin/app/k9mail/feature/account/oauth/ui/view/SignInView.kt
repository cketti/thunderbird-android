package app.k9mail.feature.account.oauth.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.k9mail.core.ui.compose.common.resources.annotatedStringResource
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.feature.account.oauth.R

private const val GOOGLE_OAUTH_SUPPORT_PAGE = "https://support.thunderbird.net/kb/gmail-thunderbird-android"

@Composable
internal fun SignInView(
    onSignInClick: () -> Unit,
    isGoogleSignIn: Boolean,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
        modifier = modifier,
    ) {
        TextBodySmall(
            text = stringResource(id = R.string.account_oauth_sign_in_description),
            textAlign = TextAlign.Center,
        )

        if (isGoogleSignIn) {
            SignInWithGoogleButton(
                onClick = onSignInClick,
                enabled = isEnabled,
            )

            GoogleSignInExtraText()
        } else {
            ButtonFilled(
                text = stringResource(id = R.string.account_oauth_sign_in_button),
                onClick = onSignInClick,
                enabled = isEnabled,
            )
        }
    }
}

@Composable
private fun GoogleSignInExtraText() {
    val extraText = annotatedStringResource(
        id = R.string.account_oauth_google_sign_in_extra_text,
        argument = buildAnnotatedString {
            withStyle(
                style = SpanStyle(
                    color = MainTheme.colors.primary,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                withLink(LinkAnnotation.Url(GOOGLE_OAUTH_SUPPORT_PAGE)) {
                    append(stringResource(R.string.account_oauth_google_sign_in_extra_text_link_text))
                }
            }
        },
    )

    TextBodySmall(
        text = extraText,
        textAlign = TextAlign.Center,
    )
}
