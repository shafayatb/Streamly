package com.shafayatb.streamly.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.components.OnBrandButton
import com.shafayatb.streamly.core.designsystem.components.OnBrandErrorText
import com.shafayatb.streamly.core.designsystem.components.OnBrandOutlinedButton
import com.shafayatb.streamly.core.designsystem.components.OnBrandTextButton
import com.shafayatb.streamly.core.designsystem.components.StreamlyLogo
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.singleColumnMaxWidth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.cd_streamly_logo
import streamly.shared.generated.resources.error_storage_unknown
import streamly.shared.generated.resources.onboarding_continue_as_guest
import streamly.shared.generated.resources.onboarding_continue_with_google
import streamly.shared.generated.resources.onboarding_sign_in_with_email
import streamly.shared.generated.resources.onboarding_subtitle
import streamly.shared.generated.resources.onboarding_title

@Composable
fun OnboardingRoot(
    onNavigateToHome: () -> Unit,
    onNavigateToEmailSignIn: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OnboardingEvent.NavigateToHome -> onNavigateToHome()
            OnboardingEvent.NavigateToEmailSignIn -> onNavigateToEmailSignIn()
        }
    }

    OnboardingScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    BrandBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = singleColumnMaxWidth())
                    .fillMaxWidth(),
            ) {
                StreamlyLogo(contentDescription = stringResource(Res.string.cd_streamly_logo))
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(Res.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                OnBrandButton(
                    text = stringResource(Res.string.onboarding_continue_with_google),
                    onClick = { onIntent(OnboardingIntent.ContinueWithGoogle) },
                    enabled = !state.isBusy,
                    isLoading = state.pendingMethod == SignInMethod.GOOGLE,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OnBrandOutlinedButton(
                    text = stringResource(Res.string.onboarding_sign_in_with_email),
                    onClick = { onIntent(OnboardingIntent.SignInWithEmail) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OnBrandTextButton(
                    text = stringResource(Res.string.onboarding_continue_as_guest),
                    onClick = { onIntent(OnboardingIntent.ContinueAsGuest) },
                    enabled = !state.isBusy,
                    isLoading = state.pendingMethod == SignInMethod.GUEST,
                )
                state.error?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    OnBrandErrorText(text = error.asString())
                }
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    StreamlyTheme {
        OnboardingScreen(state = OnboardingState(), onIntent = {})
    }
}

@Preview
@Composable
private fun OnboardingScreenErrorPreview() {
    StreamlyTheme {
        OnboardingScreen(
            state = OnboardingState(error = UiText.Resource(Res.string.error_storage_unknown)),
            onIntent = {},
        )
    }
}
