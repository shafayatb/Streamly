package com.shafayatb.streamly.data.di

import com.shafayatb.streamly.data.download.DataStoreDownloadOwnershipRepository
import com.shafayatb.streamly.data.local.createPreferencesDataStore
import com.shafayatb.streamly.data.network.createApiHttpClient
import com.shafayatb.streamly.data.session.DataStoreSessionRepository
import com.shafayatb.streamly.data.shorts.KtorShortsRepository
import com.shafayatb.streamly.data.video.KtorVideoRepository
import com.shafayatb.streamly.data.video.catalog.CatalogMockApi
import com.shafayatb.streamly.domain.download.AccountDownloadRepository
import com.shafayatb.streamly.domain.download.DownloadAccess
import com.shafayatb.streamly.domain.download.DownloadOwnershipRepository
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.shorts.ShortsRepository
import com.shafayatb.streamly.domain.video.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

public val dataModule: Module = module {
    // Each store is built inside its repository, so Koin never holds two unqualified DataStores.
    single<SessionRepository> {
        DataStoreSessionRepository(
            dataStore = createPreferencesDataStore(androidContext(), name = "session", scope = ioScope()),
        )
    }
    single<DownloadOwnershipRepository> {
        DataStoreDownloadOwnershipRepository(
            dataStore = createPreferencesDataStore(androidContext(), name = "download_owners", scope = ioScope()),
        )
    }
    // Application-scoped on the main thread, which the device downloads require.
    single {
        AccountDownloadRepository(
            device = get(),
            ownership = get(),
            sessionRepository = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    } binds arrayOf(DownloadRepository::class, DownloadAccess::class)

    // The catalog API is mocked (see CatalogMockApi); a real backend only needs another engine.
    single { createApiHttpClient(engine = CatalogMockApi().engine()) }
    single<VideoRepository> { KtorVideoRepository(client = get()) }
    single<ShortsRepository> { KtorShortsRepository(client = get()) }
}

private fun ioScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())
