package com.shafayatb.streamly.di

import com.shafayatb.streamly.app.AppViewModel
import com.shafayatb.streamly.home.HomeViewModel
import com.shafayatb.streamly.onboarding.OnboardingViewModel
import com.shafayatb.streamly.onboarding.email.EmailSignInViewModel
import com.shafayatb.streamly.player.PlayerViewModel
import com.shafayatb.streamly.shorts.ShortsViewModel
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule: Module = module {
    single<Clock> { Clock.System }

    viewModelOf(::AppViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::EmailSignInViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ShortsViewModel)
    viewModel { params ->
        PlayerViewModel(videoId = params.get(), videoRepository = get(), videoPlayer = get(), clock = get())
    }
}
