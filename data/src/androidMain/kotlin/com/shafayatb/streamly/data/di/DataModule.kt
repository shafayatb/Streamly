package com.shafayatb.streamly.data.di

import com.shafayatb.streamly.data.network.createApiHttpClient
import com.shafayatb.streamly.data.session.DataStoreSessionRepository
import com.shafayatb.streamly.data.session.createSessionDataStore
import com.shafayatb.streamly.data.shorts.KtorShortsRepository
import com.shafayatb.streamly.data.video.KtorVideoRepository
import com.shafayatb.streamly.data.video.catalog.CatalogMockApi
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.shorts.ShortsRepository
import com.shafayatb.streamly.domain.video.VideoRepository
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

    // The catalog API is mocked (see CatalogMockApi); a real backend only needs another engine.
    single { createApiHttpClient(engine = CatalogMockApi().engine()) }
    single<VideoRepository> { KtorVideoRepository(client = get()) }
    single<ShortsRepository> { KtorShortsRepository(client = get()) }
}
