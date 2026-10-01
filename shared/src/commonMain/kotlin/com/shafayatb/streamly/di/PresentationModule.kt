package com.shafayatb.streamly.di

import com.shafayatb.streamly.app.AppViewModel
import com.shafayatb.streamly.onboarding.OnboardingViewModel
import com.shafayatb.streamly.onboarding.email.EmailSignInViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule: Module = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::EmailSignInViewModel)
}
