package com.shafayatb.streamly.onboarding.email

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.components.OnBrandButton
import com.shafayatb.streamly.core.designsystem.components.OnBrandErrorText
import com.shafayatb.streamly.core.designsystem.components.OnBrandTextField
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.singleColumnMaxWidth
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.cd_navigate_back
import streamly.shared.generated.resources.email_sign_in_continue
import streamly.shared.generated.resources.email_sign_in_label
import streamly.shared.generated.resources.email_sign_in_subtitle
import streamly.shared.generated.resources.email_sign_in_title
import streamly.shared.generated.resources.error_email_invalid
import streamly.shared.generated.resources.ic_arrow_back

@Composable
fun EmailSignInRoot(
    onNavigateToHome: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EmailSignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            EmailSignInEvent.NavigateToHome -> onNavigateToHome()
            EmailSignInEvent.NavigateBack -> onNavigateBack()
        }
    }

    EmailSignInScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun EmailSignInScreen(
    state: EmailSignInState,
    onIntent: (EmailSignInIntent) -> Unit,
) {
    BrandBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            IconButton(
                onClick = { onIntent(EmailSignInIntent.NavigateBack) },
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = stringResource(Res.string.cd_navigate_back),
                    tint = Color.White,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 64.dp),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = singleColumnMaxWidth())
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.email_sign_in_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.email_sign_in_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(24.dp))
                    OnBrandTextField(
                        value = state.email,
                        onValueChange = { onIntent(EmailSignInIntent.EmailChanged(it)) },
                        label = stringResource(Res.string.email_sign_in_label),
                        error = state.emailError?.asString(),
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onIntent(EmailSignInIntent.Submit) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    OnBrandButton(
                        text = stringResource(Res.string.email_sign_in_continue),
                        onClick = { onIntent(EmailSignInIntent.Submit) },
                        isLoading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let { error ->
                        Spacer(Modifier.height(16.dp))
                        OnBrandErrorText(text = error.asString(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun EmailSignInScreenPreview() {
    StreamlyTheme {
        EmailSignInScreen(state = EmailSignInState(email = "anika@streamly.app"), onIntent = {})
    }
}

@Preview
@Composable
private fun EmailSignInScreenInvalidPreview() {
    StreamlyTheme {
        EmailSignInScreen(
            state = EmailSignInState(
                email = "anika@",
                emailError = UiText.Resource(Res.string.error_email_invalid),
            ),
            onIntent = {},
        )
    }
}
