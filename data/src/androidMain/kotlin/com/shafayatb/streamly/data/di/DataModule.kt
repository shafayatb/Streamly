package com.shafayatb.streamly.data.di

import com.shafayatb.streamly.data.session.DataStoreSessionRepository
import com.shafayatb.streamly.data.session.createSessionDataStore
import com.shafayatb.streamly.domain.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

public val dataModule: Module = module {
    single {
        createSessionDataStore(
            context = androidContext(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        )
    }
    single<SessionRepository> { DataStoreSessionRepository(dataStore = get()) }
}
