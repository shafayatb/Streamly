package com.shafayatb.streamly.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.resolve
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.ic_download
import streamly.shared.generated.resources.ic_history
import streamly.shared.generated.resources.ic_login
import streamly.shared.generated.resources.ic_logout
import streamly.shared.generated.resources.ic_person
import streamly.shared.generated.resources.ic_settings
import streamly.shared.generated.resources.profile_downloads
import streamly.shared.generated.resources.profile_guest_detail
import streamly.shared.generated.resources.profile_guest_name
import streamly.shared.generated.resources.profile_history
import streamly.shared.generated.resources.profile_loading
import streamly.shared.generated.resources.profile_settings
import streamly.shared.generated.resources.profile_sign_in
import streamly.shared.generated.resources.profile_sign_out

/** [bottomInset] is the system bar space the app shell leaves for this screen to keep clear. */
@Composable
fun ProfileRoot(
    bottomInset: Dp,
    onNavigateToDownloads: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ProfileEvent.NavigateToDownloads -> onNavigateToDownloads()
            ProfileEvent.NavigateToOnboarding -> onNavigateToOnboarding()
            is ProfileEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    ProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        bottomInset = bottomInset,
        snackbarHostState = snackbarHostState,
    )
}

/** Phone landscape puts the header beside the rows, so the rows are not pushed off screen. */
@Composable
fun ProfileScreen(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    bottomInset: Dp = 0.dp,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val compactHeight = !currentWindowAdaptiveInfo().windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (compactHeight) {
            Row(modifier = Modifier.fillMaxSize()) {
                ProfileHeader(
                    account = state.account,
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top),
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                )
                ProfileRows(
                    account = state.account,
                    onIntent = onIntent,
                    bottomInset = bottomInset,
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Top),
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ProfileHeader(
                    account = state.account,
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    modifier = Modifier.fillMaxWidth(),
                )
                ProfileRows(
                    account = state.account,
                    onIntent = onIntent,
                    bottomInset = bottomInset,
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset),
        )
    }

    state.dialog?.let { dialog ->
        SignOutDialog(
            dialog = dialog,
            isSigningOut = state.isSigningOut,
            onConfirm = { onIntent(ProfileIntent.ConfirmSignOut) },
            onDismiss = { onIntent(ProfileIntent.DismissDialog) },
        )
    }
}

@Composable
private fun ProfileHeader(account: ProfileAccount, insets: WindowInsets, modifier: Modifier = Modifier) {
    val loading = stringResource(Res.string.profile_loading)
    BrandBackground(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .windowInsetsPadding(insets)
                .padding(horizontal = 16.dp, vertical = 28.dp)
                .then(if (account == ProfileAccount.Loading) Modifier.semantics { contentDescription = loading } else Modifier),
        ) {
            ProfileAvatar(account)
            if (account != ProfileAccount.Loading) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when (account) {
                        is ProfileAccount.SignedIn -> account.name
                        else -> stringResource(Res.string.profile_guest_name)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = when (account) {
                        is ProfileAccount.SignedIn -> account.email
                        else -> stringResource(Res.string.profile_guest_detail)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Decorative: the name is always shown under it. */
@Composable
private fun ProfileAvatar(account: ProfileAccount) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .background(Color.White.copy(alpha = 0.22f), CircleShape)
            .clearAndSetSemantics {},
    ) {
        when (account) {
            is ProfileAccount.SignedIn -> Text(
                text = account.initials,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            ProfileAccount.Guest -> Icon(
                painter = painterResource(Res.drawable.ic_person),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
            ProfileAccount.Loading -> Unit
        }
    }
}

@Composable
private fun ProfileRows(
    account: ProfileAccount,
    onIntent: (ProfileIntent) -> Unit,
    bottomInset: Dp,
    insets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(insets)
            .padding(bottom = bottomInset),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ProfileRow(Res.drawable.ic_download, stringResource(Res.string.profile_downloads)) {
                onIntent(ProfileIntent.OpenDownloads)
            }
            HorizontalDivider()
            ProfileRow(Res.drawable.ic_history, stringResource(Res.string.profile_history)) {
                onIntent(ProfileIntent.OpenHistory)
            }
            HorizontalDivider()
            ProfileRow(Res.drawable.ic_settings, stringResource(Res.string.profile_settings)) {
                onIntent(ProfileIntent.OpenSettings)
            }
            HorizontalDivider()
            when (account) {
                ProfileAccount.Loading -> Unit
                ProfileAccount.Guest -> ProfileRow(
                    icon = Res.drawable.ic_login,
                    label = stringResource(Res.string.profile_sign_in),
                    color = MaterialTheme.colorScheme.primary,
                ) { onIntent(ProfileIntent.RequestSignOut) }
                is ProfileAccount.SignedIn -> ProfileRow(
                    icon = Res.drawable.ic_logout,
                    label = stringResource(Res.string.profile_sign_out),
                    color = signOutColor(),
                ) { onIntent(ProfileIntent.RequestSignOut) }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    icon: DrawableResource,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** The theme's coral is too light for body text on the light background (about 3.3:1). */
@Composable
private fun signOutColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) MaterialTheme.colorScheme.error else Color(0xFFB83A26)

private val PreviewAccount = ProfileAccount.SignedIn("Anika Rahman", "anika@streamly.app", "AR")

@Preview
@Composable
private fun ProfileScreenPreview() {
    StreamlyTheme(darkTheme = false) {
        ProfileScreen(state = ProfileState(account = PreviewAccount), onIntent = {})
    }
}

@Preview
@Composable
private fun ProfileScreenDarkPreview() {
    StreamlyTheme(darkTheme = true) {
        ProfileScreen(state = ProfileState(account = PreviewAccount), onIntent = {})
    }
}

@Preview
@Composable
private fun ProfileScreenGuestPreview() {
    StreamlyTheme(darkTheme = false) {
        ProfileScreen(state = ProfileState(account = ProfileAccount.Guest), onIntent = {})
    }
}

@Preview
@Composable
private fun ProfileScreenSignOutDialogPreview() {
    StreamlyTheme(darkTheme = true) {
        ProfileScreen(state = ProfileState(account = PreviewAccount, dialog = ProfileDialog.SIGN_OUT), onIntent = {})
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ProfileScreenLandscapePreview() {
    StreamlyTheme(darkTheme = false) {
        ProfileScreen(state = ProfileState(account = PreviewAccount), onIntent = {})
    }
}
