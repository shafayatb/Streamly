# Offline Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real Media3 HLS downloads with live progress, offline playback through the normal
player, and removal, exposed through a Player Download action and a new Downloads tab.

**Architecture:** One `MediaDownloads` object in `:core:media` owns the app's only
`DownloadManager` and writes into the existing `MediaCache` `SimpleCache`. It implements a
pure-Kotlin `DownloadRepository` for the UI and, inside the module, tells `ExoVideoPlayer` which
completed download (and its stream keys) to play. A foreground `StreamlyDownloadService` keeps
downloads running in the background. `PlayerViewModel` and a new `DownloadsViewModel` (MVI) read
the repository.

**Tech Stack:** Kotlin 2.4, Media3 1.11.1 (`DownloadManager`, `DownloadHelper`,
`DownloadService`, `HlsMediaSource`), Compose Multiplatform 1.12, Navigation 3, Koin 4.2,
kotlinx.serialization, kotlin.test + Turbine.

**Spec:** this file. Per `AGENTS.md`, the agreed design and the implementation plan live together
in `docs/plans/`. The design below was agreed with the reviewer section by section on
October 2, 2026.

---

## Agreed design

### Decisions made with the reviewer

| Question | Decision |
|---|---|
| Rendition | The highest HLS variant at or below 854x480, plus its audio. If no variant fits, the lowest one (`DefaultTrackSelector` exceeds constraints when it has to). About 66 MB for the 10.5-minute Big Buck Bunny. |
| Network | Any network (`Requirements.NETWORK`, Media3's default). Queued downloads show "Waiting for network" while offline. No settings screen exists to change it. |
| Removal | A confirmation dialog ("Remove download?"), then an immediate `removeDownload`. The same flow in the Player and in Downloads. |
| Player button | A progress ring with the real percent. Tapping it while queued or downloading cancels the download (no dialog, because nothing finished). Downloaded → tap → remove dialog. Failed → Retry. Live videos: no Download action. |
| Navigation | Downloads becomes the third tab now (Home · Shorts · Downloads). Profile comes next task and will also link here. |
| Approach | A: the download tracker lives in `:core:media`, and `ExoVideoPlayer` asks it for a completed download's `MediaItem`. Stream keys never reach the domain or UI. |

### Why the player must know about downloads

With the network off, `HlsMediaSource` loads the cached master playlist and the track selector
picks a variant from its bandwidth estimate, usually one that was not downloaded, so playback
fails. A completed download therefore plays `DownloadRequest.toMediaItem()`, which carries the
downloaded `StreamKey`s, so `HlsMediaSource` filters the playlist to that variant. The cache key
needs no change: `DownloadManager` and playback both use the default key (the URI). Videos that
are not completely downloaded keep streaming with full ABR.

### Domain contract (`:domain/download`)

`VideoDownload` (id, title, channel name, thumbnail URL, duration, status, percent, bytes),
`DownloadStatus` (`QUEUED`, `WAITING_FOR_NETWORK`, `DOWNLOADING`, `COMPLETED`, `FAILED`,
`REMOVING`), `StorageUsage` (used bytes, free bytes), `DownloadError` (`NETWORK`,
`LIVE_NOT_SUPPORTED`, `UNKNOWN`), and `DownloadRepository` (`downloads`, `storage`,
`download(video)`, `retry(videoId)`, `remove(videoId)`). The full code is in Task 2.

- Media3's `STATE_QUEUED` with an unmet requirement maps to `WAITING_FOR_NETWORK`.
  `STATE_STOPPED` maps to `QUEUED` (the app never sets a stop reason), and `STATE_RESTARTING`
  maps to `DOWNLOADING`.
- Retry re-adds the stored `DownloadRequest`. Media3 restarts a request added again in a terminal
  state, so no new `DownloadHelper` pass is needed. The Downloads list only has a `VideoDownload`,
  not a `Video`.
- Storage is "66 MB used · 3.1 GB free": the used figure is the sum of `bytesDownloaded`, and the
  free figure is `filesDir.usableSpace`. The mockup's "of 8 GB" implies a quota that does not exist.

### `:core:media`

- `MediaCache` exposes its `StandaloneDatabaseProvider`, so `DownloadManager` shares it, and the
  same `SimpleCache` and `upstreamDataSourceFactory` (Media3's `DefaultHttpDataSource`, per the
  October 2 decision).
- `MediaDownloads` (a Koin single, main thread):
  - **Snapshot:** reads the `DownloadIndex` once off the main thread (`getCurrentDownloads()`
    omits completed and failed downloads), then applies `DownloadManager.Listener` events. Events
    that arrive during the first read are buffered and applied after it.
  - **Polling:** progress is polled every 500 ms while any download is `DOWNLOADING`, and polling
    stops when none is.
  - **`download(video)`:** runs `DownloadHelper` with the 480p cap and `DefaultRenderersFactory`,
    stores JSON metadata in `DownloadRequest.data`, and sends the request to the service.
- `StreamlyDownloadService` runs in the foreground (`dataSync`) with a progress notification
  that shows the title and percent, plus completed and failed notifications. `getScheduler()`
  returns `null`, a change from the agreed section 2 found while reading the source: Media3 never
  calls the scheduler on API 31+ (both test devices), where the service simply stays in the
  foreground until the network returns. On API 24–30 the service also stays in the foreground
  while waiting, which avoids the `PlatformScheduler` job service and the boot permission for a
  path the test devices cannot exercise.
- `MainActivity.onCreate` calls `startDownloadService(context)` so that downloads left unfinished
  by a killed process resume (`DownloadService` resumes the paused `DownloadManager` when it is
  created).

### Presentation

- **Player:** `PlayerState.download: DownloadActionUi` and `isRemoveDownloadDialogShown`. The
  intents are `Download`, `CancelDownload`, `RequestRemoveDownload`, `ConfirmRemoveDownload`, and
  `DismissRemoveDownload`. A `download()` failure sends a snackbar message and returns the button
  to Download.
- **Notification permission (Android 13+):** `PlayerRoot` asks for it when Download is tapped.
  The download starts whatever the answer, because only the notification is hidden without it.
- **Downloads tab:** `DownloadsViewModel` and `DownloadsScreen` per mockup 05.
  - States: loading, empty, error with Try again, and content. Content has the storage line, then
    in-progress and failed rows first and completed rows after, newest first within each.
    Removing rows are hidden.
  - A completed row opens the Player on top of Downloads. In-progress rows can be cancelled,
    failed rows can be retried, and completed and failed rows can be removed through the dialog.
  - It uses a grid on medium and wider windows and a list on phones.

### Out of scope

Profile, sign-out, history, settings, a network preference, background audio, and offline
thumbnails beyond what Coil has already cached.

---

## Global Constraints

- Kotlin only; Coroutines + Flow; Compose for all UI; MVVM + MVI (one `ViewModel`, one immutable `UiState` in a `StateFlow`, one sealed intent type per screen).
- `:domain` has no Android, Compose, Ktor, DataStore, Media3, or Koin imports.
- Media3 types stay inside `:core:media`. Never create a second `SimpleCache` or `DatabaseProvider`.
- Library modules use explicit API mode: every public declaration says `public`.
- Media3 `@UnstableApi` opt-ins use `androidx.annotation.OptIn`, not `kotlin.OptIn`.
- Koin `onClose` comes from `org.koin.dsl`; chain it as `single { } onClose { } bind X::class`.
- Every dependency comes from `gradle/libs.versions.toml`; no hard-coded versions.
- Compose resource strings use a typographic apostrophe (’), never `\'`. After deleting a Compose resource, run `./gradlew :shared:clean`.
- Never show fake progress. Every percent comes from `DownloadManager`.
- Commits follow Conventional Commits with the `Co-Authored-By` trailer, and are made **only after the reviewer approves the staged diff**.
- The RTK hook filters output: use `rtk proxy ./gradlew …` and `rtk proxy git …` for raw output. Test results are in `<module>/build/test-results/testAndroidHostTest/`.

## Review Focus

1. **Offline playback of a completed download picks a variant that was not downloaded.** The player must play the download's `MediaItem` with stream keys. No host test can drive Media3 here, so Task 7, step 4 checks it on a device with the network off and `StreamlyDownloads` logging "Playing download".
2. **The app is restored straight onto the Player before the download index has loaded.** `completedMediaItem` falls back to a single-row index read (Task 4). Task 7, step 5 kills the process on the Player and relaunches offline.
3. **Download tapped twice quickly** must start only one `DownloadHelper` pass. Test: `secondTapWhileStartingIsIgnored` (Task 5).
4. **The remove dialog is open when its download disappears** (finished removing, or removed from the other screen). The dialog closes rather than offering to remove nothing. Test: `dialogClosesWhenItsDownloadDisappears` (Task 6).
5. **Corrupt or missing `DownloadRequest.data`** must not crash the list. The title falls back to the video id. Test: `unreadableDataFallsBackToTheVideoId` (Task 3).

---

## File map

| File | Responsibility |
|---|---|
| `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/download/VideoDownload.kt` | `VideoDownload`, `DownloadStatus`, `StorageUsage` |
| `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/download/DownloadRepository.kt` | `DownloadRepository`, `DownloadError` |
| `domain/src/commonTest/kotlin/com/shafayatb/streamly/domain/download/VideoDownloadTest.kt` | `isInProgress` |
| `core/media/build.gradle.kts` | serialization plugin and JSON, Android resources |
| `core/media/src/androidMain/AndroidManifest.xml` | permissions and the service |
| `core/media/src/androidMain/res/values/strings.xml`, `res/drawable/ic_download_notification.xml` | notification channel text and icon |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/cache/MediaCache.kt` | expose `databaseProvider` |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/download/DownloadMetadata.kt` | JSON stored in `DownloadRequest.data` |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/download/DownloadMapping.kt` | `DownloadSnapshot` and the pure Media3 → domain mapping |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/download/MediaDownloads.kt` | owns `DownloadManager`; implements `DownloadRepository` and `OfflineMediaItems` |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/download/OfflineMediaItems.kt` | internal seam that `ExoVideoPlayer` consults |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/download/StreamlyDownloadService.kt` | foreground service, notifications, `startDownloadService` |
| `core/media/src/androidHostTest/kotlin/com/shafayatb/streamly/core/media/download/DownloadMappingTest.kt`, `DownloadMetadataTest.kt` | host tests |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/di/MediaModule.kt` | Koin wiring |
| `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/player/ExoVideoPlayer.kt` | plays completed downloads |
| `androidApp/src/main/kotlin/com/shafayatb/streamly/MainActivity.kt` | resumes unfinished downloads |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/DownloadActionUi.kt` | button state and its derivation |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/{PlayerViewModel,PlayerState,PlayerIntent,PlayerScreen}.kt` | Download action |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/downloads/RemoveDownloadDialog.kt` | shared confirmation dialog |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/core/presentation/NotificationPermission.kt` + `shared/src/androidMain/.../NotificationPermission.android.kt` | expect/actual permission request |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/core/presentation/ErrorUiText.kt` | `DownloadError.toUiText()` |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/core/presentation/VideoFormatting.kt` | `formatBytes` |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/downloads/{DownloadsState,DownloadsIntent,DownloadsEvent,DownloadsViewModel,DownloadsScreen}.kt` | Downloads screen |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/navigation/{Route,TopLevelDestination,AppNavigation}.kt` | tab and route |
| `shared/src/commonMain/kotlin/com/shafayatb/streamly/di/PresentationModule.kt` | ViewModels |
| `shared/src/commonTest/kotlin/com/shafayatb/streamly/testing/FakeDownloadRepository.kt` | fake and `testDownload()` |
| `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/PlayerDownloadTest.kt`, `downloads/DownloadsViewModelTest.kt` | tests |
| `shared/src/commonMain/composeResources/values/strings.xml`, `drawable/ic_download_done.xml`, `ic_close.xml`, `ic_delete.xml` | resources |

---

### Task 1: Plan

**Files:** Create `docs/plans/2026-10-03-downloads.md` (this file).

- [ ] **Step 1:** The reviewer reads this plan and approves it before any production code.
- [ ] **Step 2:** Stage it and propose `docs: plan offline downloads`. Commit only after approval. Following the "Committing a series" note in `FRESH_PROMPT.md`, the commits may instead be built at the end as a series of trees; either way, each commit is built and host-tested on its own.

---

### Task 2: Domain contract

**Files:**
- Create: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/download/VideoDownload.kt`
- Create: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/download/DownloadRepository.kt`
- Test: `domain/src/commonTest/kotlin/com/shafayatb/streamly/domain/download/VideoDownloadTest.kt`

**Interfaces:**
- Produces: `VideoDownload`, `DownloadStatus`, `StorageUsage`, `DownloadError`, and `DownloadRepository`, exactly as below. Every later task uses these names.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.shafayatb.streamly.domain.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class VideoDownloadTest {

    private fun download(status: DownloadStatus) = VideoDownload(
        videoId = "v1",
        title = "Title",
        channelName = "Channel",
        thumbnailUrl = "",
        duration = 10.minutes,
        status = status,
        percent = null,
        bytesDownloaded = 0,
    )

    @Test
    fun onlyUnfinishedDownloadsAreInProgress() {
        val inProgress = DownloadStatus.entries.filter { download(it).isInProgress }
        assertEquals(
            listOf(DownloadStatus.QUEUED, DownloadStatus.WAITING_FOR_NETWORK, DownloadStatus.DOWNLOADING),
            inProgress,
        )
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `rtk proxy ./gradlew :domain:testAndroidHostTest`
Expected: compilation fails with `Unresolved reference: VideoDownload`.

- [ ] **Step 3: Write the contract**

`VideoDownload.kt`:

```kotlin
package com.shafayatb.streamly.domain.download

import kotlin.time.Duration

/** A video saved, or being saved, for offline playback. */
public data class VideoDownload(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** [Duration.ZERO] when the download's stored details do not say. */
    val duration: Duration,
    val status: DownloadStatus,
    /** 0–100 as reported by the downloader, or `null` while it cannot tell yet. */
    val percent: Float?,
    val bytesDownloaded: Long,
) {
    /** Still on its way: it can be cancelled, and it cannot be played offline yet. */
    val isInProgress: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.WAITING_FOR_NETWORK ||
            status == DownloadStatus.DOWNLOADING
}

public enum class DownloadStatus {
    /** Waiting for a free download slot. */
    QUEUED,

    /** Waiting for a network connection; it continues on its own when one returns. */
    WAITING_FOR_NETWORK,
    DOWNLOADING,

    /** Fully saved; plays offline. */
    COMPLETED,

    /** Gave up after retrying; [DownloadRepository.retry] starts it again. */
    FAILED,

    /** Being deleted from the device. */
    REMOVING,
}

/** Space used by downloads and space left on the device, in bytes. */
public data class StorageUsage(
    val usedBytes: Long,
    val freeBytes: Long,
)
```

`DownloadRepository.kt`:

```kotlin
package com.shafayatb.streamly.domain.download

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Error
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlinx.coroutines.flow.Flow

/**
 * Videos saved for offline playback. Downloads continue in the background and survive app
 * restarts. Call it from the main thread.
 */
public interface DownloadRepository {
    /**
     * Every download, newest first, updated as their progress changes. Nothing is emitted until
     * the stored downloads have been read, so a collector is loading until the first value.
     * Collecting again after a [Result.Failure] reads them again.
     */
    public val downloads: Flow<Result<List<VideoDownload>, DataError.Local>>

    public val storage: Flow<StorageUsage>

    /**
     * Reads [video]'s playlist, picks the rendition to save, and queues the download. Returns
     * once it is queued, not when it finishes; progress then arrives through [downloads].
     */
    public suspend fun download(video: Video): EmptyResult<DownloadError>

    /** Starts a [DownloadStatus.FAILED] download again. */
    public fun retry(videoId: String)

    /** Cancels an unfinished download, or deletes a finished one and frees its space. */
    public fun remove(videoId: String)
}

public enum class DownloadError : Error {
    /** The playlist could not be fetched, e.g. while offline. */
    NETWORK,

    /** Live streams have no end, so they cannot be saved. */
    LIVE_NOT_SUPPORTED,
    UNKNOWN,
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `rtk proxy ./gradlew :domain:testAndroidHostTest`
Expected: PASS (6 tests: 5 existing + 1 new).

- [ ] **Step 5: Stage the commit:** `feat(domain): add the download contract`

---

### Task 3: Media download manager, service, and mapping

**Files:**
- Modify: `core/media/build.gradle.kts`, `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/cache/MediaCache.kt`, `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/di/MediaModule.kt`, `androidApp/src/main/kotlin/com/shafayatb/streamly/MainActivity.kt`
- Create: `core/media/src/androidMain/AndroidManifest.xml`, `core/media/src/androidMain/res/values/strings.xml`, `core/media/src/androidMain/res/drawable/ic_download_notification.xml`, and `download/{DownloadMetadata,DownloadMapping,OfflineMediaItems,MediaDownloads,StreamlyDownloadService}.kt` under `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/`
- Test: `core/media/src/androidHostTest/kotlin/com/shafayatb/streamly/core/media/download/{DownloadMappingTest,DownloadMetadataTest}.kt`

**Interfaces:**
- Consumes: the Task 2 contract; `MediaCache.cache` and `MediaCache.upstreamDataSourceFactory`.
- Produces:
  - `internal class MediaDownloads : DownloadRepository, OfflineMediaItems` with `val downloadManager: DownloadManager`.
  - `internal fun interface OfflineMediaItems { fun completedMediaItem(videoId: String): MediaItem? }`.
  - `public class StreamlyDownloadService`.
  - `public fun startDownloadService(context: Context)`.

- [ ] **Step 1: Build setup**

In `core/media/build.gradle.kts`, add `alias(libs.plugins.kotlinSerialization)` to `plugins`. Inside `kotlin { android { … } }`, add `androidResources { enable = true }`. In `androidMain.dependencies`, add `implementation(libs.kotlinx.serialization.json)`.

Run: `rtk proxy ./gradlew :core:media:compileAndroidMain`
Expected: BUILD SUCCESSFUL. If AGP 9.1 rejects `androidResources` in this block, look up the KMP library DSL with context7 (`com.android.kotlin.multiplatform.library androidResources`) and use the documented form.

- [ ] **Step 2: Write the failing metadata test**

```kotlin
package com.shafayatb.streamly.core.media.download

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadMetadataTest {

    @Test
    fun roundTripsThroughTheRequestData() {
        val metadata = DownloadMetadata(
            title = "Media3 in 10 minutes",
            channelName = "CodeLabs",
            thumbnailUrl = "https://example.com/t.jpg",
            durationMs = 600_000,
        )

        assertEquals(metadata, DownloadMetadata.decode(metadata.encode(), videoId = "v1"))
    }

    @Test
    fun unreadableDataFallsBackToTheVideoId() {
        val fallback = DownloadMetadata(title = "v1", channelName = "", thumbnailUrl = "", durationMs = 0)

        assertEquals(fallback, DownloadMetadata.decode(ByteArray(0), videoId = "v1"))
        assertEquals(fallback, DownloadMetadata.decode("not json".encodeToByteArray(), videoId = "v1"))
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `rtk proxy ./gradlew :core:media:testAndroidHostTest`
Expected: compilation fails with `Unresolved reference: DownloadMetadata`.

- [ ] **Step 4: Implement `DownloadMetadata.kt`**

```kotlin
package com.shafayatb.streamly.core.media.download

import com.shafayatb.streamly.domain.video.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the Downloads screen shows for a download, stored in its `DownloadRequest.data` so the
 * list works offline without the catalog.
 */
@Serializable
internal data class DownloadMetadata(
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationMs: Long,
) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(video: Video): DownloadMetadata = DownloadMetadata(
            title = video.title,
            channelName = video.channel.name,
            thumbnailUrl = video.thumbnailUrl,
            durationMs = video.duration?.inWholeMilliseconds ?: 0,
        )

        /** Anything unreadable still lists the download, under its video id. */
        fun decode(data: ByteArray, videoId: String): DownloadMetadata =
            runCatching { json.decodeFromString(serializer(), data.decodeToString()) }
                .getOrElse { DownloadMetadata(title = videoId, channelName = "", thumbnailUrl = "", durationMs = 0) }
    }
}
```

Run: `rtk proxy ./gradlew :core:media:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 5: Write the failing mapping test**

```kotlin
package com.shafayatb.streamly.core.media.download

import androidx.media3.common.C
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class DownloadMappingTest {

    private val metadata = DownloadMetadata("Title", "Channel", "https://example.com/t.jpg", durationMs = 600_000)

    private fun snapshot(
        id: String = "v1",
        state: Int = Download.STATE_DOWNLOADING,
        percent: Float = 42f,
        bytes: Long = 28_000_000,
        startTimeMs: Long = 0,
    ) = DownloadSnapshot(id, state, percent, bytes, startTimeMs, metadata)

    @Test
    fun mapsEveryDownloadState() {
        assertEquals(DownloadStatus.QUEUED, downloadStatusOf(Download.STATE_QUEUED, notMetRequirements = 0))
        assertEquals(DownloadStatus.QUEUED, downloadStatusOf(Download.STATE_STOPPED, notMetRequirements = 0))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_DOWNLOADING, notMetRequirements = 0))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_RESTARTING, notMetRequirements = 0))
        assertEquals(DownloadStatus.COMPLETED, downloadStatusOf(Download.STATE_COMPLETED, notMetRequirements = 0))
        assertEquals(DownloadStatus.FAILED, downloadStatusOf(Download.STATE_FAILED, notMetRequirements = 0))
        assertEquals(DownloadStatus.REMOVING, downloadStatusOf(Download.STATE_REMOVING, notMetRequirements = 0))
    }

    @Test
    fun queuedWithoutANetworkIsWaitingForIt() {
        assertEquals(
            DownloadStatus.WAITING_FOR_NETWORK,
            downloadStatusOf(Download.STATE_QUEUED, notMetRequirements = Requirements.NETWORK),
        )
        // Only queued downloads wait; finished ones are unaffected by the network.
        assertEquals(
            DownloadStatus.COMPLETED,
            downloadStatusOf(Download.STATE_COMPLETED, notMetRequirements = Requirements.NETWORK),
        )
    }

    @Test
    fun mapsASnapshotWithItsMetadata() {
        assertEquals(
            VideoDownload(
                videoId = "v1",
                title = "Title",
                channelName = "Channel",
                thumbnailUrl = "https://example.com/t.jpg",
                duration = 10.minutes,
                status = DownloadStatus.DOWNLOADING,
                percent = 42f,
                bytesDownloaded = 28_000_000,
            ),
            snapshot().toVideoDownload(notMetRequirements = 0),
        )
    }

    @Test
    fun unknownPercentIsNull() {
        assertNull(snapshot(percent = C.PERCENTAGE_UNSET).toVideoDownload(notMetRequirements = 0).percent)
    }

    @Test
    fun completedIsAlwaysAHundredPercent() {
        val completed = snapshot(state = Download.STATE_COMPLETED, percent = C.PERCENTAGE_UNSET)
        assertEquals(100f, completed.toVideoDownload(notMetRequirements = 0).percent)
    }

    @Test
    fun listsNewestFirst() {
        val downloads = listOf(snapshot(id = "old", startTimeMs = 1), snapshot(id = "new", startTimeMs = 2))
            .toVideoDownloads(notMetRequirements = 0)

        assertEquals(listOf("new", "old"), downloads.map { it.videoId })
    }
}
```

- [ ] **Step 6: Run it and confirm it fails**

Run: `rtk proxy ./gradlew :core:media:testAndroidHostTest`
Expected: compilation fails with `Unresolved reference: DownloadSnapshot`.

- [ ] **Step 7: Implement `DownloadMapping.kt`**

```kotlin
package com.shafayatb.streamly.core.media.download

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [Download]'s values at one moment. A Media3 `Download` reads its progress live, so the same
 * instance changes under a `StateFlow`; copying the values lets equal snapshots be skipped and
 * changed ones be emitted.
 */
internal data class DownloadSnapshot(
    val videoId: String,
    val state: Int,
    val percentDownloaded: Float,
    val bytesDownloaded: Long,
    val startTimeMs: Long,
    val metadata: DownloadMetadata,
)

@OptIn(UnstableApi::class)
internal fun Download.toSnapshot(): DownloadSnapshot = DownloadSnapshot(
    videoId = request.id,
    state = state,
    percentDownloaded = percentDownloaded,
    bytesDownloaded = bytesDownloaded,
    startTimeMs = startTimeMs,
    metadata = DownloadMetadata.decode(request.data, videoId = request.id),
)

/** The app's only download requirement is a network, so any unmet requirement means offline. */
@OptIn(UnstableApi::class)
internal fun downloadStatusOf(state: Int, notMetRequirements: Int): DownloadStatus = when (state) {
    Download.STATE_QUEUED ->
        if (notMetRequirements != 0) DownloadStatus.WAITING_FOR_NETWORK else DownloadStatus.QUEUED
    Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadStatus.DOWNLOADING
    Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
    Download.STATE_FAILED -> DownloadStatus.FAILED
    Download.STATE_REMOVING -> DownloadStatus.REMOVING
    // STATE_STOPPED needs a stop reason, which the app never sets.
    else -> DownloadStatus.QUEUED
}

internal fun DownloadSnapshot.toVideoDownload(notMetRequirements: Int): VideoDownload {
    val status = downloadStatusOf(state, notMetRequirements)
    return VideoDownload(
        videoId = videoId,
        title = metadata.title,
        channelName = metadata.channelName,
        thumbnailUrl = metadata.thumbnailUrl,
        duration = metadata.durationMs.milliseconds,
        status = status,
        percent = when {
            status == DownloadStatus.COMPLETED -> 100f
            percentDownloaded == C.PERCENTAGE_UNSET || percentDownloaded < 0f -> null
            else -> percentDownloaded.coerceAtMost(100f)
        },
        bytesDownloaded = bytesDownloaded,
    )
}

internal fun Collection<DownloadSnapshot>.toVideoDownloads(notMetRequirements: Int): List<VideoDownload> =
    sortedByDescending { it.startTimeMs }.map { it.toVideoDownload(notMetRequirements) }
```

Run: `rtk proxy ./gradlew :core:media:testAndroidHostTest`
Expected: PASS (19 existing + 8 new).

- [ ] **Step 8: Share the database provider.** In `MediaCache.kt`, add `import androidx.media3.database.DatabaseProvider`, replace the inline provider, and update the class KDoc's "Downloads will write" to "Downloads write":

```kotlin
    /** Shared with `DownloadManager`, whose download index lives in the same database. */
    val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(appContext)

    // SimpleCache locks its folder, so the process must create exactly one, for its whole life.
    val cache: Cache = SimpleCache(
        File(appContext.filesDir, "media-cache"),
        NoOpCacheEvictor(),
        databaseProvider,
    )
```

- [ ] **Step 9: Create `OfflineMediaItems.kt`**

```kotlin
package com.shafayatb.streamly.core.media.download

import androidx.media3.common.MediaItem

/** Lets the long-form player play a finished download's saved rendition instead of streaming. */
internal fun interface OfflineMediaItems {
    /**
     * The media item for [videoId]'s completed download, carrying the stream keys of the
     * rendition that was saved, or `null` when it is not completely downloaded.
     */
    fun completedMediaItem(videoId: String): MediaItem?
}
```

- [ ] **Step 10: Create `MediaDownloads.kt`**

```kotlin
package com.shafayatb.streamly.core.media.download

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.shafayatb.streamly.core.media.cache.MediaCache
import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's only [DownloadManager]. It writes into [MediaCache]'s cache, which playback reads
 * first, so a finished download plays offline through the normal player.
 *
 * `DownloadManager` pushes state changes but not progress, so progress is polled while anything
 * is downloading. Call it from the main thread, the manager's own thread.
 */
@OptIn(UnstableApi::class)
internal class MediaDownloads(
    context: Context,
    mediaCache: MediaCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadRepository, OfflineMediaItems {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val upstreamDataSourceFactory = mediaCache.upstreamDataSourceFactory

    val downloadManager: DownloadManager = DownloadManager(
        appContext,
        mediaCache.databaseProvider,
        mediaCache.cache,
        upstreamDataSourceFactory,
        Executors.newFixedThreadPool(DOWNLOAD_THREADS),
    )

    private val helperFactory = DownloadHelper.Factory()
        .setDataSourceFactory(upstreamDataSourceFactory)
        .setRenderersFactory(DefaultRenderersFactory(appContext))
        .setTrackSelectionParameters(
            // Download defaults force the highest bitrate; the size cap makes that the best
            // variant up to 480p. Streams without one fall back to their lowest variant.
            DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon()
                .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT)
                .build(),
        )

    /** `null` until the download index has been read. */
    private val snapshots = MutableStateFlow<Map<String, DownloadSnapshot>?>(null)
    private val requests = mutableMapOf<String, DownloadRequest>()
    private val notMetRequirements = MutableStateFlow(downloadManager.notMetRequirements)

    // Changes reported while the index is being read; applied on top of it afterwards.
    private val pendingChanges = linkedMapOf<String, Download?>()
    private val loadLock = Mutex()
    private var progressPolling: Job? = null

    override val downloads: Flow<Result<List<VideoDownload>, DataError.Local>> = flow {
        val error = ensureLoaded()
        if (error != null) {
            emit(Result.Failure(error))
            return@flow
        }
        emitAll(
            combine(snapshots.filterNotNull(), notMetRequirements) { snapshots, notMet ->
                Result.Success(snapshots.values.toVideoDownloads(notMet))
            }.distinctUntilChanged(),
        )
    }

    override val storage: Flow<StorageUsage> = snapshots.filterNotNull()
        .map { snapshots ->
            StorageUsage(
                usedBytes = snapshots.values
                    .filter { it.state != Download.STATE_REMOVING }
                    .sumOf { it.bytesDownloaded },
                freeBytes = appContext.filesDir.usableSpace,
            )
        }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    init {
        downloadManager.addListener(ManagerListener())
        scope.launch { ensureLoaded() }
    }

    override suspend fun download(video: Video): EmptyResult<DownloadError> {
        if (video.isLive) return Result.Failure(DownloadError.LIVE_NOT_SUPPORTED)
        val helper = helperFactory.create(
            MediaItem.Builder()
                .setMediaId(video.id)
                .setUri(video.hlsUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build(),
        )
        return try {
            helper.prepareAndAwait()
            val request = helper.getDownloadRequest(video.id, DownloadMetadata.of(video).encode())
            DownloadService.sendAddDownload(appContext, StreamlyDownloadService::class.java, request, false)
            Result.Success(Unit)
        } catch (e: DownloadHelper.LiveContentUnsupportedException) {
            Result.Failure(DownloadError.LIVE_NOT_SUPPORTED)
        } catch (e: IOException) {
            Log.w(TAG, "Could not prepare ${video.id}", e)
            Result.Failure(DownloadError.NETWORK)
        } finally {
            helper.release()
        }
    }

    override fun retry(videoId: String) {
        val request = requests[videoId] ?: return
        // Adding a request again restarts a download that failed.
        DownloadService.sendAddDownload(appContext, StreamlyDownloadService::class.java, request, false)
    }

    override fun remove(videoId: String) {
        DownloadService.sendRemoveDownload(appContext, StreamlyDownloadService::class.java, videoId, false)
    }

    override fun completedMediaItem(videoId: String): MediaItem? {
        val loaded = snapshots.value
        val request = if (loaded != null) {
            requests[videoId].takeIf { loaded[videoId]?.state == Download.STATE_COMPLETED }
        } else {
            // Only before the first index read finishes, e.g. a player screen restored at launch:
            // one indexed row, so reading it here is cheaper than playing the wrong rendition.
            downloadManager.downloadIndex.getDownload(videoId)
                ?.takeIf { it.state == Download.STATE_COMPLETED }
                ?.request
        }
        return request?.toMediaItem()?.also { Log.d(TAG, "Playing download $videoId (${it.localConfiguration?.streamKeys?.size} stream keys)") }
    }

    fun release() {
        scope.cancel()
        downloadManager.release()
    }

    /** Reads the index once; returns the failure, if any, so a later collector can try again. */
    private suspend fun ensureLoaded(): DataError.Local? = loadLock.withLock {
        if (snapshots.value != null) return null
        val stored = try {
            withContext(ioDispatcher) {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.download) }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read the download index", e)
            return DataError.Local.UNKNOWN
        }
        stored.forEach { requests[it.request.id] = it.request }
        val loaded = stored.associate { it.request.id to it.toSnapshot() }.toMutableMap()
        pendingChanges.forEach { (id, download) ->
            if (download == null) loaded -= id else loaded[id] = download.toSnapshot()
        }
        pendingChanges.clear()
        snapshots.value = loaded
        updateProgressPolling()
        null
    }

    private fun apply(id: String, download: Download?) {
        val current = snapshots.value
        if (current == null) {
            pendingChanges[id] = download
        } else {
            snapshots.value = if (download == null) current - id else current + (id to download.toSnapshot())
        }
        if (download == null) requests -= id else requests[id] = download.request
        updateProgressPolling()
    }

    private fun updateProgressPolling() {
        val downloading = snapshots.value.orEmpty().values.any { it.state == Download.STATE_DOWNLOADING }
        if (downloading && progressPolling?.isActive != true) {
            progressPolling = scope.launch {
                while (isActive) {
                    delay(PROGRESS_INTERVAL)
                    downloadManager.currentDownloads.forEach { apply(it.request.id, it) }
                }
            }
        } else if (!downloading) {
            progressPolling?.cancel()
            progressPolling = null
        }
    }

    private suspend fun DownloadHelper.prepareAndAwait() = suspendCancellableCoroutine { continuation ->
        prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                continuation.resume(Unit)
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

    private inner class ManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            apply(download.request.id, download)
        }

        override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
            apply(download.request.id, null)
        }

        override fun onRequirementsStateChanged(
            manager: DownloadManager,
            requirements: Requirements,
            notMetRequirements: Int,
        ) {
            this@MediaDownloads.notMetRequirements.value = notMetRequirements
        }
    }

    private companion object {
        const val TAG = "StreamlyDownloads"
        const val DOWNLOAD_THREADS = 4
        const val MAX_VIDEO_WIDTH = 854
        const val MAX_VIDEO_HEIGHT = 480
        val PROGRESS_INTERVAL = 500.milliseconds
    }
}
```

Note for the implementer: a status whose snapshot does not change (for example `STATE_QUEUED`) produces an equal map, so `distinctUntilChanged` drops it, as intended. If `Download.getPercentDownloaded`/`getBytesDownloaded` are not Kotlin properties in 1.11.1, call them as methods.

- [ ] **Step 11: Create the service, `StreamlyDownloadService.kt`**

```kotlin
package com.shafayatb.streamly.core.media.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.shafayatb.streamly.core.media.R
import org.koin.android.ext.android.inject

/**
 * Keeps downloads running while the app is in the background, as a `dataSync` foreground service
 * with a progress notification.
 */
@OptIn(UnstableApi::class)
public class StreamlyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    private val mediaDownloads: MediaDownloads by inject()
    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    // Called once per process, so the listener is added once.
    override fun getDownloadManager(): DownloadManager = mediaDownloads.downloadManager.apply {
        addListener(FinishedNotifier(applicationContext, notificationHelper))
    }

    // Media3 never uses a scheduler on API 31+, and without one the service stays in the
    // foreground until the network returns on every API level, which is the behavior we want.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download_notification,
            openAppIntent(this),
            downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING }?.title(),
            downloads,
            notMetRequirements,
        )

    /** Posts "Download complete" or "Download failed" when a download finishes. */
    private class FinishedNotifier(
        private val context: Context,
        private val helper: DownloadNotificationHelper,
    ) : DownloadManager.Listener {
        private var nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1

        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            val notification = when (download.state) {
                Download.STATE_COMPLETED -> helper.buildDownloadCompletedNotification(
                    context, R.drawable.ic_download_notification, openAppIntent(context), download.title(),
                )
                Download.STATE_FAILED -> helper.buildDownloadFailedNotification(
                    context, R.drawable.ic_download_notification, openAppIntent(context), download.title(),
                )
                else -> return
            }
            NotificationUtil.setNotification(context, nextNotificationId++, notification)
        }
    }

    private companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
        const val CHANNEL_ID = "downloads"

        fun Download.title(): String = DownloadMetadata.decode(request.data, request.id).title

        fun openAppIntent(context: Context): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
    }
}

/**
 * Resumes downloads that a killed process left unfinished. Call it while the app is in the
 * foreground; Android does not let a background app start the service.
 */
@OptIn(UnstableApi::class)
public fun startDownloadService(context: Context) {
    try {
        DownloadService.start(context, StreamlyDownloadService::class.java)
    } catch (e: IllegalStateException) {
        // Started from the background; the next download the user starts starts the service.
    }
}
```

- [ ] **Step 12: Add the manifest and resources**

`core/media/src/androidMain/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name="com.shafayatb.streamly.core.media.download.StreamlyDownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>

</manifest>
```

`core/media/src/androidMain/res/values/strings.xml`:

```xml
<resources>
    <string name="download_channel_name">Downloads</string>
    <string name="download_channel_description">Progress of videos saving for offline viewing</string>
</resources>
```

`core/media/src/androidMain/res/drawable/ic_download_notification.xml`: copy `shared/src/commonMain/composeResources/drawable/ic_download.xml`, with `android:fillColor="#FFFFFFFF"`.

- [ ] **Step 13: Wire Koin.** In `MediaModule.kt`, add the imports (`MediaDownloads`, `OfflineMediaItems`, `DownloadRepository`, `org.koin.dsl.binds`), then add before the `ExoVideoPlayer` single:

```kotlin
    // Application-scoped: the only DownloadManager, writing into the one media cache.
    single { MediaDownloads(androidContext(), get()) } onClose { it?.release() } binds
        arrayOf(DownloadRepository::class, OfflineMediaItems::class)
```

- [ ] **Step 14: Resume downloads at launch.** In `MainActivity.onCreate`, after `super.onCreate(savedInstanceState)`, add the line below, and add `import com.shafayatb.streamly.core.media.download.startDownloadService`:

```kotlin
        startDownloadService(this)
```

- [ ] **Step 15: Build and test**

Run: `rtk proxy ./gradlew :androidApp:assembleDebug :core:media:testAndroidHostTest`
Expected: BUILD SUCCESSFUL. Check the merged manifest
(`androidApp/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`)
for the service with `foregroundServiceType="dataSync"` and the three permissions.

- [ ] **Step 16: Stage the commit:** `feat(media): download HLS videos with DownloadManager into the shared cache`

---

### Task 4: Play completed downloads from their saved rendition

**Files:**
- Modify: `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/player/ExoVideoPlayer.kt`, `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/di/MediaModule.kt`

**Interfaces:**
- Consumes: `OfflineMediaItems.completedMediaItem(videoId): MediaItem?` from Task 3.

No host test can drive this, because `MediaItem.Builder().setUri` calls the stubbed `android.net.Uri`. Task 7, steps 4–5 verify it on a device (Review Focus 1 and 2).

- [ ] **Step 1: Take the seam in the constructor.** Add a parameter after `dataSourceFactory`:

```kotlin
    private val offlineMediaItems: OfflineMediaItems,
```

Add `import com.shafayatb.streamly.core.media.download.OfflineMediaItems`.

- [ ] **Step 2: Use it in `load`.** Replace the `val mediaItem = …` block:

```kotlin
        // A finished download plays the rendition it saved: its stream keys keep the player from
        // choosing a variant that is not on the device, which offline would fail.
        val mediaItem = offlineMediaItems.completedMediaItem(video.id) ?: MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.hlsUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
```

- [ ] **Step 3: Wire it.** In `MediaModule.kt`, pass `offlineMediaItems = get()` to `ExoVideoPlayer`.

- [ ] **Step 4: Build and test**

Run: `rtk proxy ./gradlew :androidApp:assembleDebug :core:media:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 5: Stage the commit:** `feat(media): play completed downloads from their saved rendition`

---

### Task 5: Player download action

**Files:**
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/DownloadActionUi.kt`, `shared/src/commonMain/kotlin/com/shafayatb/streamly/downloads/RemoveDownloadDialog.kt`, `shared/src/commonMain/kotlin/com/shafayatb/streamly/core/presentation/NotificationPermission.kt`, `shared/src/androidMain/kotlin/com/shafayatb/streamly/core/presentation/NotificationPermission.android.kt`, `shared/src/commonTest/kotlin/com/shafayatb/streamly/testing/FakeDownloadRepository.kt`, `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/PlayerDownloadTest.kt`, `shared/src/commonMain/composeResources/drawable/ic_download_done.xml`
- Modify: `PlayerViewModel.kt`, `PlayerState.kt`, `PlayerIntent.kt`, `PlayerScreen.kt`, `ErrorUiText.kt`, `PresentationModule.kt`, `PlayerViewModelTest.kt` (constructor only), `strings.xml`, `shared/build.gradle.kts`

**Interfaces:**
- Consumes: `DownloadRepository`, `VideoDownload`, `DownloadStatus`, and `DownloadError` (Task 2).
- Produces:
  - `FakeDownloadRepository` and `testDownload(...)`.
  - `RemoveDownloadDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit)`.
  - `DownloadError.toUiText()`.
  - `@Composable expect fun rememberNotificationPermissionRequest(): () -> Unit`.

- [ ] **Step 1: Write the fake, `FakeDownloadRepository.kt`**

```kotlin
package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

/** A [DownloadRepository] whose downloads the test sets directly, as the downloader would report them. */
class FakeDownloadRepository : DownloadRepository {

    /** `null` while "loading": nothing is emitted until a test sets it. */
    private val result = MutableStateFlow<Result<List<VideoDownload>, DataError.Local>?>(null)

    var collections = 0
        private set

    override val downloads: Flow<Result<List<VideoDownload>, DataError.Local>> = flow {
        collections++
        emitAll(result.filterNotNull())
    }

    val storageUsage = MutableStateFlow(StorageUsage(usedBytes = 0, freeBytes = 3_100_000_000))
    override val storage: Flow<StorageUsage> = storageUsage

    /** What [download] returns. */
    var downloadResult: EmptyResult<DownloadError> = Result.Success(Unit)

    /** When set, [download] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    val requested = mutableListOf<String>()
    val retried = mutableListOf<String>()
    val removed = mutableListOf<String>()

    fun emit(vararg downloads: VideoDownload) {
        result.value = Result.Success(downloads.toList())
    }

    fun fail(error: DataError.Local) {
        result.value = Result.Failure(error)
    }

    override suspend fun download(video: Video): EmptyResult<DownloadError> {
        requested += video.id
        gate?.await()
        return downloadResult
    }

    override fun retry(videoId: String) {
        retried += videoId
    }

    override fun remove(videoId: String) {
        removed += videoId
    }
}

fun testDownload(
    videoId: String,
    status: DownloadStatus = DownloadStatus.COMPLETED,
    percent: Float? = if (status == DownloadStatus.COMPLETED) 100f else null,
    bytesDownloaded: Long = 66_000_000,
    title: String = "Video $videoId",
    duration: Duration = 10.minutes,
): VideoDownload = VideoDownload(
    videoId = videoId,
    title = title,
    channelName = "Channel",
    thumbnailUrl = "https://example.com/$videoId.jpg",
    duration = duration,
    status = status,
    percent = percent,
    bytesDownloaded = bytesDownloaded,
)
```

- [ ] **Step 2: Write the failing Player tests, `PlayerDownloadTest.kt`**

```kotlin
package com.shafayatb.streamly.player

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.testDownload
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.download_error_network

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDownloadTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
    private val video = testVideo(id = "v1")
    private val live = testVideo(id = "live", duration = null)
    private val downloads = FakeDownloadRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        downloads.emit()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(videoId: String = "v1") = PlayerViewModel(
        videoId = videoId,
        videoRepository = FakeVideoRepository(videos = listOf(video, live)),
        videoPlayer = FakeVideoPlayer(),
        downloadRepository = downloads,
        clock = clock,
    )

    private val PlayerViewModel.download get() = state.value.download

    @Test
    fun liveVideosHaveNoDownloadAction() {
        assertEquals(DownloadActionUi.Hidden, viewModel(videoId = "live").download)
    }

    @Test
    fun aVideoNotYetDownloadedOffersDownload() {
        assertEquals(DownloadActionUi.Idle, viewModel().download)
    }

    @Test
    fun startingShowsQueuedUntilTheDownloaderReportsIt() {
        val gate = CompletableDeferred<Unit>()
        downloads.gate = gate
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)
        assertEquals(DownloadActionUi.Queued, viewModel.download)
        assertEquals(listOf("v1"), downloads.requested)

        gate.complete(Unit)
        // Queued with the downloader until the service picks it up.
        assertEquals(DownloadActionUi.Queued, viewModel.download)

        downloads.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = 42.7f))
        assertEquals(DownloadActionUi.Downloading(percent = 42), viewModel.download)
    }

    @Test
    fun secondTapWhileStartingIsIgnored() {
        downloads.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)
        viewModel.onIntent(PlayerIntent.Download)

        assertEquals(listOf("v1"), downloads.requested)
    }

    @Test
    fun aFailedStartExplainsWhyAndOffersDownloadAgain() = runTest {
        downloads.downloadResult = Result.Failure(DownloadError.NETWORK)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.Download)
            assertEquals(PlayerEvent.ShowMessage(UiText.Resource(Res.string.download_error_network)), awaitItem())
        }
        assertEquals(DownloadActionUi.Idle, viewModel.download)
    }

    @Test
    fun followsTheDownloadersStatus() {
        val viewModel = viewModel()

        downloads.emit(testDownload("v1", DownloadStatus.QUEUED))
        assertEquals(DownloadActionUi.Queued, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.WAITING_FOR_NETWORK))
        assertEquals(DownloadActionUi.WaitingForNetwork, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = null))
        assertEquals(DownloadActionUi.Downloading(percent = null), viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.COMPLETED))
        assertEquals(DownloadActionUi.Downloaded, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.FAILED))
        assertEquals(DownloadActionUi.Failed, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.REMOVING))
        assertEquals(DownloadActionUi.Removing, viewModel.download)
        downloads.emit(testDownload("other", DownloadStatus.COMPLETED))
        assertEquals(DownloadActionUi.Idle, viewModel.download)
    }

    @Test
    fun cancellingRemovesWithoutAsking() {
        downloads.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = 10f))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.CancelDownload)

        assertEquals(listOf("v1"), downloads.removed)
        assertFalse(viewModel.state.value.isRemoveDownloadDialogShown)
    }

    @Test
    fun removingADownloadAsksFirst() {
        downloads.emit(testDownload("v1", DownloadStatus.COMPLETED))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.RequestRemoveDownload)
        assertTrue(viewModel.state.value.isRemoveDownloadDialogShown)
        assertTrue(downloads.removed.isEmpty())

        viewModel.onIntent(PlayerIntent.ConfirmRemoveDownload)
        assertEquals(listOf("v1"), downloads.removed)
        assertFalse(viewModel.state.value.isRemoveDownloadDialogShown)
    }

    @Test
    fun dismissingTheDialogKeepsTheDownload() {
        downloads.emit(testDownload("v1", DownloadStatus.COMPLETED))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.RequestRemoveDownload)
        viewModel.onIntent(PlayerIntent.DismissRemoveDownload)

        assertFalse(viewModel.state.value.isRemoveDownloadDialogShown)
        assertTrue(downloads.removed.isEmpty())
    }

    @Test
    fun downloadOnAFailedDownloadRetriesIt() {
        downloads.emit(testDownload("v1", DownloadStatus.FAILED))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)

        assertEquals(listOf("v1"), downloads.retried)
        assertTrue(downloads.requested.isEmpty())
    }
}
```

Also update the existing `PlayerViewModelTest.viewModel()` factory to pass `downloadRepository = FakeDownloadRepository().apply { emit() }` and use named arguments.

- [ ] **Step 3: Run them and confirm they fail**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: compilation fails with `Unresolved reference: DownloadActionUi` / `downloadRepository`.

- [ ] **Step 4: Add the strings** to `strings.xml`. Delete `player_download_unavailable`, then add:

```xml
    <string name="player_download_queued">Queued</string>
    <string name="player_download_waiting">Waiting</string>
    <string name="player_downloaded">Downloaded</string>
    <string name="player_download_retry">Retry</string>
    <string name="player_download_removing">Removing…</string>
    <string name="cd_cancel_download">Cancel download</string>
    <string name="download_remove_title">Remove download?</string>
    <string name="download_remove_body">“%1$s” will be deleted from this device.</string>
    <string name="download_remove_confirm">Remove</string>
    <string name="download_remove_cancel">Cancel</string>
    <string name="download_error_network">Couldn’t start the download. Check your connection and try again.</string>
    <string name="download_error_live">Live streams can’t be downloaded.</string>
    <string name="download_error_unknown">Couldn’t start the download. Please try again.</string>
```

Create `drawable/ic_download_done.xml` in the same vector format as `ic_download.xml`, with `android:pathData="M5,18h14v2H5v-2zM9.6,15.6L4.4,10.4l1.4,-1.4 3.8,3.8 9,-9 1.4,1.4z"`.

In `ErrorUiText.kt`, add the function below, with its imports:

```kotlin
fun DownloadError.toUiText(): UiText = UiText.Resource(
    when (this) {
        DownloadError.NETWORK -> Res.string.download_error_network
        DownloadError.LIVE_NOT_SUPPORTED -> Res.string.download_error_live
        DownloadError.UNKNOWN -> Res.string.download_error_unknown
    },
)
```

- [ ] **Step 5: Create `DownloadActionUi.kt`**

```kotlin
package com.shafayatb.streamly.player

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload

/** The Player's Download action. Every percent comes from the downloader. */
@Immutable
sealed interface DownloadActionUi {
    /** Live streams cannot be downloaded, and nothing shows before the video's details load. */
    data object Hidden : DownloadActionUi
    data object Idle : DownloadActionUi
    data object Queued : DownloadActionUi
    data object WaitingForNetwork : DownloadActionUi

    /** [percent] is `null` while the downloader cannot tell yet. */
    data class Downloading(val percent: Int?) : DownloadActionUi
    data object Downloaded : DownloadActionUi
    data object Failed : DownloadActionUi
    data object Removing : DownloadActionUi
}

/**
 * [isLive] is `null` until the video's details load. [isStarting] covers the gap between a tap
 * and the downloader reporting the new download.
 */
internal fun downloadActionOf(isLive: Boolean?, download: VideoDownload?, isStarting: Boolean): DownloadActionUi =
    when {
        isLive == null || isLive -> DownloadActionUi.Hidden
        download == null -> if (isStarting) DownloadActionUi.Queued else DownloadActionUi.Idle
        else -> when (download.status) {
            DownloadStatus.QUEUED -> DownloadActionUi.Queued
            DownloadStatus.WAITING_FOR_NETWORK -> DownloadActionUi.WaitingForNetwork
            DownloadStatus.DOWNLOADING -> DownloadActionUi.Downloading(download.percent?.toInt())
            DownloadStatus.COMPLETED -> DownloadActionUi.Downloaded
            DownloadStatus.FAILED -> DownloadActionUi.Failed
            DownloadStatus.REMOVING -> DownloadActionUi.Removing
        }
    }
```

- [ ] **Step 6: Extend the state and intents**

In `PlayerState`, add:

```kotlin
    val download: DownloadActionUi = DownloadActionUi.Hidden,
    val isRemoveDownloadDialogShown: Boolean = false,
```

In `PlayerIntent`, add:

```kotlin
    /** Starts the download, or retries one that failed. */
    data object Download : PlayerIntent

    /** Stops an unfinished download and deletes what it saved. */
    data object CancelDownload : PlayerIntent
    data object RequestRemoveDownload : PlayerIntent
    data object ConfirmRemoveDownload : PlayerIntent
    data object DismissRemoveDownload : PlayerIntent
```

- [ ] **Step 7: Update `PlayerViewModel`**

1. Add the constructor parameter `private val downloadRepository: DownloadRepository,` after `videoPlayer`.
2. Add the fields:

```kotlin
    private val downloadEntry = MutableStateFlow<VideoDownload?>(null)
    private val isStartingDownload = MutableStateFlow(false)
    private var loadedVideo: Video? = null
```

3. Replace `state` with:

```kotlin
    val state: StateFlow<PlayerState> = combine(
        screen,
        videoPlayer.state,
        downloadEntry,
        isStartingDownload,
    ) { screen, playback, download, isStarting ->
        val isLive = (screen.content as? PlayerContent.Loaded)?.video?.isLive
        screen.copy(
            playback = playback.toPlaybackUi(videoId, isLiveVideo = isLive == true),
            download = downloadActionOf(isLive, download, isStarting),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, screen.value)
```

4. In `init`, after `loadUpNext()`:

```kotlin
        viewModelScope.launch {
            downloadRepository.downloads.collect { result ->
                val download = (result as? Result.Success)?.data?.firstOrNull { it.videoId == videoId }
                downloadEntry.value = download
                if (download != null) isStartingDownload.value = false
            }
        }
```

5. In `onIntent`, add:

```kotlin
            PlayerIntent.Download -> startDownload()
            PlayerIntent.CancelDownload -> downloadRepository.remove(videoId)
            PlayerIntent.RequestRemoveDownload -> screen.update { it.copy(isRemoveDownloadDialogShown = true) }
            PlayerIntent.ConfirmRemoveDownload -> {
                screen.update { it.copy(isRemoveDownloadDialogShown = false) }
                downloadRepository.remove(videoId)
            }
            PlayerIntent.DismissRemoveDownload -> screen.update { it.copy(isRemoveDownloadDialogShown = false) }
```

6. Add the method:

```kotlin
    private fun startDownload() {
        if (downloadEntry.value?.status == DownloadStatus.FAILED) {
            downloadRepository.retry(videoId)
            return
        }
        val video = loadedVideo ?: return
        if (isStartingDownload.value || downloadEntry.value != null) return
        isStartingDownload.value = true
        viewModelScope.launch {
            downloadRepository.download(video).onFailure { error ->
                isStartingDownload.value = false
                send(PlayerEvent.ShowMessage(error.toUiText()))
            }
        }
    }
```

7. In `loadDetails`'s `onSuccess`, set `loadedVideo = video` before `startPlayback(video)`.
8. Add the imports: `DownloadRepository`, `DownloadStatus`, `VideoDownload`, `Result`, `MutableStateFlow` (already present), and `toUiText` for `DownloadError` (same package as the existing `toUiText`).

In `PresentationModule.kt`, change the player factory to
`PlayerViewModel(videoId = params.get(), videoRepository = get(), videoPlayer = get(), downloadRepository = get(), clock = get())`.

- [ ] **Step 8: Run the tests and confirm they pass**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: PASS (all existing tests plus 10 new ones).

- [ ] **Step 9: Create `RemoveDownloadDialog.kt`**

```kotlin
package com.shafayatb.streamly.downloads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.download_remove_body
import streamly.shared.generated.resources.download_remove_cancel
import streamly.shared.generated.resources.download_remove_confirm
import streamly.shared.generated.resources.download_remove_title

/** Removing a finished download deletes it for good, so it is confirmed first. */
@Composable
fun RemoveDownloadDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.download_remove_title)) },
        text = { Text(stringResource(Res.string.download_remove_body, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.download_remove_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.download_remove_cancel)) }
        },
    )
}
```

- [ ] **Step 10: Add the notification permission (expect/actual)**

Add `implementation(libs.androidx.activity.compose)` to `shared` `androidMain.dependencies`.

`core/presentation/NotificationPermission.kt` (commonMain):

```kotlin
package com.shafayatb.streamly.core.presentation

import androidx.compose.runtime.Composable

/**
 * Returns an action that asks for permission to show notifications where the platform requires
 * it and has not granted it yet. Nothing waits for the answer.
 */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit
```

`NotificationPermission.android.kt` (androidMain):

```kotlin
package com.shafayatb.streamly.core.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || LocalInspectionMode.current) return {}
    val context = LocalContext.current
    // The download runs whatever the answer; only its notification needs the permission.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

Use `androidx.core.content.ContextCompat` only if `androidx.core` is already on the `:shared` Android classpath (it comes in through activity-compose). If it is not, use `context.checkSelfPermission(...)`, which is safe here because the API level is at least 33.

- [ ] **Step 11: Wire the Player screen**

In `PlayerRoot`:

```kotlin
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    PlayerScreen(
        state = state,
        onIntent = { intent ->
            if (intent == PlayerIntent.Download) requestNotificationPermission()
            viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
    )
```

In `PlayerScreen`, also show the dialog wherever the screen's root content is composed:

```kotlin
    val loaded = state.content as? PlayerContent.Loaded
    if (state.isRemoveDownloadDialogShown && loaded != null) {
        RemoveDownloadDialog(
            title = loaded.video.title,
            onConfirm = { onIntent(PlayerIntent.ConfirmRemoveDownload) },
            onDismiss = { onIntent(PlayerIntent.DismissRemoveDownload) },
        )
    }
```

Pass `download = state.download` into `VideoDetails`, and replace the disabled Download `ActionButton` with:

```kotlin
            DownloadButton(action = download, onIntent = onIntent)
```

Add this composable next to `ActionButton`:

```kotlin
@Composable
private fun RowScope.DownloadButton(action: DownloadActionUi, onIntent: (PlayerIntent) -> Unit) {
    if (action == DownloadActionUi.Hidden) return
    val inProgress = action is DownloadActionUi.Downloading ||
        action == DownloadActionUi.Queued ||
        action == DownloadActionUi.WaitingForNetwork
    val cancelDescription = stringResource(Res.string.cd_cancel_download)
    FilledTonalButton(
        onClick = {
            onIntent(
                when (action) {
                    DownloadActionUi.Downloaded -> PlayerIntent.RequestRemoveDownload
                    DownloadActionUi.Idle, DownloadActionUi.Failed -> PlayerIntent.Download
                    else -> PlayerIntent.CancelDownload
                },
            )
        },
        enabled = action != DownloadActionUi.Removing,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (action == DownloadActionUi.Downloaded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (action == DownloadActionUi.Downloaded) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        modifier = Modifier
            .weight(1f)
            .semantics { if (inProgress) onClick(label = cancelDescription) { false } },
    ) {
        when (action) {
            is DownloadActionUi.Downloading -> {
                val percent = action.percent
                if (percent == null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DownloadActionUi.Queued, DownloadActionUi.WaitingForNetwork, DownloadActionUi.Removing ->
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else -> Icon(
                painter = painterResource(
                    when (action) {
                        DownloadActionUi.Downloaded -> Res.drawable.ic_download_done
                        DownloadActionUi.Failed -> Res.drawable.ic_replay
                        else -> Res.drawable.ic_download
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (action) {
                is DownloadActionUi.Downloading -> action.percent?.let { "$it%" } ?: stringResource(Res.string.player_download)
                DownloadActionUi.Queued -> stringResource(Res.string.player_download_queued)
                DownloadActionUi.WaitingForNetwork -> stringResource(Res.string.player_download_waiting)
                DownloadActionUi.Downloaded -> stringResource(Res.string.player_downloaded)
                DownloadActionUi.Failed -> stringResource(Res.string.player_download_retry)
                DownloadActionUi.Removing -> stringResource(Res.string.player_download_removing)
                else -> stringResource(Res.string.player_download)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

The `semantics { onClick(label = …) }` gives TalkBack "double-tap to cancel download" while the
download is in progress. Use `androidx.compose.ui.semantics.onClick`. If the label override
conflicts with the button's own click semantics, replace it with
`stateDescription = cancelDescription`. Update the Player previews to pass a `download` value,
remove the `player_download_unavailable` and `stateDescription` imports that are no longer used,
and run `rtk proxy ./gradlew :shared:clean`, because a string was deleted.

- [ ] **Step 12: Build and test**

Run: `rtk proxy ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 13: Stage the commit:** `feat(player): wire the download action`

---

### Task 6: Downloads screen and tab

**Files:**
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/downloads/{DownloadsState,DownloadsIntent,DownloadsEvent,DownloadsViewModel,DownloadsScreen}.kt`, `shared/src/commonTest/kotlin/com/shafayatb/streamly/downloads/DownloadsViewModelTest.kt`, `drawable/ic_close.xml`, `drawable/ic_delete.xml`
- Modify: `VideoFormatting.kt` (+ `VideoFormattingTest.kt`), `Route.kt`, `TopLevelDestination.kt`, `AppNavigation.kt`, `PresentationModule.kt`, `strings.xml`

**Interfaces:**
- Consumes: `DownloadRepository` (Task 2), `FakeDownloadRepository` / `testDownload` / `RemoveDownloadDialog` (Task 5), and the existing `formatDuration`, `FeedMessage`, `FeedLoading`, `feedGridColumns`, and `VideoThumbnail`.
- Produces: `Route.Downloads`, `TopLevelDestination.DOWNLOADS`, `DownloadsRoot(bottomInset, onNavigateToPlayer)`.

- [ ] **Step 1: Write the failing `formatBytes` test** (add it to `VideoFormattingTest.kt`)

```kotlin
    @Test
    fun formatsByteCountsInDecimalUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("66 KB", formatBytes(65_900))
        assertEquals("4.2 MB", formatBytes(4_210_000))
        assertEquals("66 MB", formatBytes(65_800_000))
        assertEquals("1.2 GB", formatBytes(1_234_000_000))
        assertEquals("31.0 GB", formatBytes(31_000_000_000))
    }
```

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: FAIL with `Unresolved reference: formatBytes`.

- [ ] **Step 2: Implement it in `VideoFormatting.kt`**

```kotlin
/** Sizes in decimal units, as Android's storage settings show them: "4.2 MB", "66 MB", "1.2 GB". */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> "${roundedDiv(bytes, 1_000)} KB"
    bytes < 10_000_000 -> "${oneDecimal(bytes, 1_000_000)} MB"
    bytes < 1_000_000_000 -> "${roundedDiv(bytes, 1_000_000)} MB"
    else -> "${oneDecimal(bytes, 1_000_000_000)} GB"
}

private fun roundedDiv(value: Long, unit: Long): Long = (value + unit / 2) / unit

private fun oneDecimal(value: Long, unit: Long): String {
    val tenths = roundedDiv(value * 10, unit)
    return "${tenths / 10}.${tenths % 10}"
}
```

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 3: Add the strings and icons**

```xml
    <string name="nav_downloads">Downloads</string>

    <string name="downloads_title">Downloads</string>
    <string name="downloads_storage">%1$s used · %2$s free</string>
    <string name="downloads_loading">Loading downloads</string>
    <string name="downloads_empty_title">No downloads yet</string>
    <string name="downloads_empty_body">Videos you download play here, even offline.</string>
    <string name="downloads_error_title">Couldn’t load your downloads</string>
    <string name="downloads_queued">Queued</string>
    <string name="downloads_waiting">Waiting for network</string>
    <string name="downloads_progress">%1$s · %2$s</string>
    <string name="downloads_ready">Ready to play</string>
    <string name="downloads_detail">%1$s · %2$s</string>
    <string name="downloads_failed">Download failed</string>
    <string name="downloads_retry">Retry</string>
    <string name="cd_remove_download">Remove download</string>
```

`ic_close.xml` pathData: `M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z`.
`ic_delete.xml` pathData: `M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z`.
Both use the `ic_download.xml` vector format.

- [ ] **Step 4: Create the state, intent, and event types**

`DownloadsState.kt`:

```kotlin
package com.shafayatb.streamly.downloads

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class DownloadsState(
    val content: DownloadsContent = DownloadsContent.Loading,
    /** "66 MB used · 3.1 GB free"; `null` until known. */
    val storage: UiText? = null,
    /** The download the remove dialog is asking about, or `null` when it is closed. */
    val pendingRemoval: DownloadItemUi? = null,
)

@Immutable
sealed interface DownloadsContent {
    data object Loading : DownloadsContent
    data object Empty : DownloadsContent
    data class Loaded(val items: ImmutableList<DownloadItemUi>) : DownloadsContent
    data class Error(val message: UiText) : DownloadsContent
}

@Immutable
data class DownloadItemUi(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val status: DownloadItemStatus,
)

@Immutable
sealed interface DownloadItemStatus {
    data object Queued : DownloadItemStatus
    data object WaitingForNetwork : DownloadItemStatus

    /** [progress] is 0–1, or `null` while the downloader cannot tell (an indeterminate bar). */
    data class Downloading(val progress: Float?, val text: UiText) : DownloadItemStatus
    data object Failed : DownloadItemStatus

    /** [text] is the size and length, e.g. "66 MB · 10:34". */
    data class Completed(val text: UiText) : DownloadItemStatus
}
```

`DownloadsIntent.kt`:

```kotlin
package com.shafayatb.streamly.downloads

sealed interface DownloadsIntent {
    /** Plays a completed download; ignored for one still in progress. */
    data class Open(val videoId: String) : DownloadsIntent
    data class Cancel(val videoId: String) : DownloadsIntent
    data class Retry(val videoId: String) : DownloadsIntent
    data class RequestRemove(val videoId: String) : DownloadsIntent
    data object ConfirmRemove : DownloadsIntent
    data object DismissRemove : DownloadsIntent

    /** Reads the downloads again after they failed to load. */
    data object RetryLoad : DownloadsIntent
}
```

`DownloadsEvent.kt`:

```kotlin
package com.shafayatb.streamly.downloads

sealed interface DownloadsEvent {
    data class NavigateToPlayer(val videoId: String) : DownloadsEvent
}
```

- [ ] **Step 5: Write the failing ViewModel tests, `DownloadsViewModelTest.kt`**

```kotlin
package com.shafayatb.streamly.downloads

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.testDownload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.downloads_detail
import streamly.shared.generated.resources.downloads_progress
import streamly.shared.generated.resources.downloads_storage
import streamly.shared.generated.resources.error_storage_unknown

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val repository = FakeDownloadRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DownloadsViewModel(repository)

    private fun DownloadsViewModel.items() = (state.value.content as DownloadsContent.Loaded).items

    @Test
    fun loadsUntilTheDownloadsAreRead() {
        assertEquals(DownloadsContent.Loading, viewModel().state.value.content)
    }

    @Test
    fun noDownloadsIsEmptyWithTheStorageFigure() {
        repository.emit()
        repository.storageUsage.value = StorageUsage(usedBytes = 0, freeBytes = 3_100_000_000)

        val state = viewModel().state.value

        assertEquals(DownloadsContent.Empty, state.content)
        assertEquals(UiText.Resource(Res.string.downloads_storage, listOf("0 B", "3.1 GB")), state.storage)
    }

    @Test
    fun aReadFailureShowsAnErrorAndTryAgainReadsAgain() {
        repository.fail(DataError.Local.UNKNOWN)
        val viewModel = viewModel()
        assertEquals(
            DownloadsContent.Error(UiText.Resource(Res.string.error_storage_unknown)),
            viewModel.state.value.content,
        )

        repository.emit(testDownload("v1"))
        viewModel.onIntent(DownloadsIntent.RetryLoad)

        assertEquals(listOf("v1"), viewModel.items().map { it.videoId })
        assertEquals(2, repository.collections)
    }

    @Test
    fun anInProgressDownloadShowsItsRealProgress() {
        repository.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = 42.7f, bytesDownloaded = 28_000_000))

        val status = viewModel().items().single().status

        assertEquals(
            DownloadItemStatus.Downloading(
                progress = 0.427f,
                text = UiText.Resource(Res.string.downloads_progress, listOf("42%", "28 MB")),
            ),
            status,
        )
    }

    @Test
    fun anUnknownPercentIsIndeterminate() {
        repository.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = null, bytesDownloaded = 512))

        val status = assertIs<DownloadItemStatus.Downloading>(viewModel().items().single().status)

        assertNull(status.progress)
        assertEquals(UiText.Resource(Res.string.downloads_progress, listOf("…", "512 B")), status.text)
    }

    @Test
    fun completedDownloadsAreReadyToPlayAfterTheOnesInProgress() {
        repository.emit(
            testDownload("done", DownloadStatus.COMPLETED, bytesDownloaded = 66_000_000, duration = 10.minutes + 34.seconds),
            testDownload("queued", DownloadStatus.QUEUED),
            testDownload("failed", DownloadStatus.FAILED),
            testDownload("waiting", DownloadStatus.WAITING_FOR_NETWORK),
        )

        val items = viewModel().items()

        assertEquals(listOf("queued", "failed", "waiting", "done"), items.map { it.videoId })
        assertEquals(DownloadItemStatus.Queued, items[0].status)
        assertEquals(DownloadItemStatus.Failed, items[1].status)
        assertEquals(DownloadItemStatus.WaitingForNetwork, items[2].status)
        assertEquals(
            DownloadItemStatus.Completed(UiText.Resource(Res.string.downloads_detail, listOf("66 MB", "10:34"))),
            items[3].status,
        )
    }

    @Test
    fun downloadsBeingRemovedAreHidden() {
        repository.emit(testDownload("v1", DownloadStatus.REMOVING))

        assertEquals(DownloadsContent.Empty, viewModel().state.value.content)
    }

    @Test
    fun openingACompletedDownloadPlaysIt() = runTest {
        repository.emit(testDownload("done"), testDownload("busy", DownloadStatus.DOWNLOADING))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(DownloadsIntent.Open("busy"))
            viewModel.onIntent(DownloadsIntent.Open("done"))
            assertEquals(DownloadsEvent.NavigateToPlayer("done"), awaitItem())
        }
    }

    @Test
    fun cancelAndRetryGoStraightToTheDownloader() {
        repository.emit(testDownload("busy", DownloadStatus.DOWNLOADING), testDownload("failed", DownloadStatus.FAILED))
        val viewModel = viewModel()

        viewModel.onIntent(DownloadsIntent.Cancel("busy"))
        viewModel.onIntent(DownloadsIntent.Retry("failed"))

        assertEquals(listOf("busy"), repository.removed)
        assertEquals(listOf("failed"), repository.retried)
    }

    @Test
    fun removingAsksFirst() {
        repository.emit(testDownload("v1", title = "Weekly recap"))
        val viewModel = viewModel()

        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))
        assertEquals("Weekly recap", viewModel.state.value.pendingRemoval?.title)
        assertTrue(repository.removed.isEmpty())

        viewModel.onIntent(DownloadsIntent.ConfirmRemove)
        assertEquals(listOf("v1"), repository.removed)
        assertNull(viewModel.state.value.pendingRemoval)
    }

    @Test
    fun dismissingTheDialogKeepsTheDownload() {
        repository.emit(testDownload("v1"))
        val viewModel = viewModel()

        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))
        viewModel.onIntent(DownloadsIntent.DismissRemove)

        assertNull(viewModel.state.value.pendingRemoval)
        assertTrue(repository.removed.isEmpty())
    }

    @Test
    fun dialogClosesWhenItsDownloadDisappears() {
        repository.emit(testDownload("v1"))
        val viewModel = viewModel()
        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))

        repository.emit()

        assertNull(viewModel.state.value.pendingRemoval)
    }
}
```

- [ ] **Step 6: Run them and confirm they fail**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: compilation fails with `Unresolved reference: DownloadsViewModel`.

- [ ] **Step 7: Implement `DownloadsViewModel.kt`**

```kotlin
package com.shafayatb.streamly.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatBytes
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.downloads_detail
import streamly.shared.generated.resources.downloads_progress
import streamly.shared.generated.resources.downloads_storage

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    /** `null` while loading. */
    private val downloads = MutableStateFlow<Result<List<VideoDownload>, DataError.Local>?>(null)
    private val storage = MutableStateFlow<StorageUsage?>(null)
    private val pendingRemovalId = MutableStateFlow<String?>(null)

    val state: StateFlow<DownloadsState> = combine(downloads, storage, pendingRemovalId) { downloads, storage, pendingId ->
        val items = (downloads as? Result.Success)?.data?.toItems()
        DownloadsState(
            content = when (downloads) {
                null -> DownloadsContent.Loading
                is Result.Failure -> DownloadsContent.Error(downloads.error.toUiText())
                is Result.Success -> if (items.isNullOrEmpty()) DownloadsContent.Empty else DownloadsContent.Loaded(items.toImmutableList())
            },
            storage = storage?.let {
                UiText.Resource(Res.string.downloads_storage, listOf(formatBytes(it.usedBytes), formatBytes(it.freeBytes)))
            },
            // A download that disappears closes its dialog rather than offering to remove nothing.
            pendingRemoval = items?.firstOrNull { it.videoId == pendingId },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DownloadsState())

    private val _events = Channel<DownloadsEvent>()
    val events: Flow<DownloadsEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { downloadRepository.storage.collect { storage.value = it } }
    }

    fun onIntent(intent: DownloadsIntent) {
        when (intent) {
            is DownloadsIntent.Open -> open(intent.videoId)
            is DownloadsIntent.Cancel -> downloadRepository.remove(intent.videoId)
            is DownloadsIntent.Retry -> downloadRepository.retry(intent.videoId)
            is DownloadsIntent.RequestRemove -> pendingRemovalId.value = intent.videoId
            DownloadsIntent.ConfirmRemove -> {
                pendingRemovalId.value?.let(downloadRepository::remove)
                pendingRemovalId.value = null
            }
            DownloadsIntent.DismissRemove -> pendingRemovalId.value = null
            DownloadsIntent.RetryLoad -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        downloads.value = null
        loadJob = viewModelScope.launch {
            downloadRepository.downloads.collect { downloads.value = it }
        }
    }

    private fun open(videoId: String) {
        val download = (downloads.value as? Result.Success)?.data?.firstOrNull { it.videoId == videoId }
        if (download?.status != DownloadStatus.COMPLETED) return
        viewModelScope.launch { _events.send(DownloadsEvent.NavigateToPlayer(videoId)) }
    }
}

/** Unfinished and failed downloads first, then finished ones; each group keeps the repository's newest-first order. */
private fun List<VideoDownload>.toItems(): List<DownloadItemUi> = filter { it.status != DownloadStatus.REMOVING }
    .sortedBy { it.status == DownloadStatus.COMPLETED }
    .map { it.toItemUi() }

private fun VideoDownload.toItemUi(): DownloadItemUi = DownloadItemUi(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    status = when (status) {
        DownloadStatus.QUEUED, DownloadStatus.REMOVING -> DownloadItemStatus.Queued
        DownloadStatus.WAITING_FOR_NETWORK -> DownloadItemStatus.WaitingForNetwork
        DownloadStatus.DOWNLOADING -> DownloadItemStatus.Downloading(
            progress = percent?.div(100f),
            text = UiText.Resource(
                Res.string.downloads_progress,
                listOf(percent?.let { "${it.toInt()}%" } ?: "…", formatBytes(bytesDownloaded)),
            ),
        )
        DownloadStatus.FAILED -> DownloadItemStatus.Failed
        DownloadStatus.COMPLETED -> DownloadItemStatus.Completed(
            UiText.Resource(Res.string.downloads_detail, listOf(formatBytes(bytesDownloaded), formatDuration(duration))),
        )
    },
)
```

`sortedBy` is stable, so the newest-first order holds within each group. Check the `formatDuration`
signature in `VideoFormatting.kt`: it takes a `Duration` and returns "10:34".

- [ ] **Step 8: Run the tests and confirm they pass**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: PASS (13 new, including `formatBytes`).

- [ ] **Step 9: Create `DownloadsScreen.kt`** per mockup 05: header, storage line, the list or grid, and the row states.

```kotlin
package com.shafayatb.streamly.downloads

// Imports: follow HomeScreen.kt (layout, lazy grid, material3, resources, koinViewModel,
// collectAsStateWithLifecycle, ObserveAsEvents) plus VideoThumbnail, LinearProgressIndicator, IconButton.

@Composable
fun DownloadsRoot(
    bottomInset: Dp,
    onNavigateToPlayer: (videoId: String) -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DownloadsEvent.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
        }
    }
    DownloadsScreen(state = state, onIntent = viewModel::onIntent, bottomInset = bottomInset)
}

@Composable
fun DownloadsScreen(state: DownloadsState, onIntent: (DownloadsIntent) -> Unit, bottomInset: Dp = 0.dp) {
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val contentModifier = Modifier.fillMaxSize().windowInsetsPadding(horizontalInsets)
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + bottomInset)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DownloadsHeader(storage = state.storage?.asString())
        when (val content = state.content) {
            DownloadsContent.Loading -> FeedLoading(
                columns = feedGridColumns(),
                contentPadding = contentPadding,
                contentDescription = stringResource(Res.string.downloads_loading),
                modifier = contentModifier,
            )
            DownloadsContent.Empty -> FeedMessage(
                icon = Res.drawable.ic_download,
                title = stringResource(Res.string.downloads_empty_title),
                message = stringResource(Res.string.downloads_empty_body),
                modifier = contentModifier,
            )
            is DownloadsContent.Error -> FeedMessage(
                icon = Res.drawable.ic_cloud_off,
                title = stringResource(Res.string.downloads_error_title),
                message = content.message.asString(),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = { onIntent(DownloadsIntent.RetryLoad) },
                modifier = contentModifier,
            )
            is DownloadsContent.Loaded -> LazyVerticalGrid(
                // One column on phones; cells of at least 360 dp on wider windows.
                columns = GridCells.Adaptive(minSize = 360.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = contentModifier,
            ) {
                items(items = content.items, key = { it.videoId }) { item ->
                    DownloadRow(item = item, onIntent = onIntent)
                }
            }
        }
    }
    state.pendingRemoval?.let { item ->
        RemoveDownloadDialog(
            title = item.title,
            onConfirm = { onIntent(DownloadsIntent.ConfirmRemove) },
            onDismiss = { onIntent(DownloadsIntent.DismissRemove) },
        )
    }
}
```

`FeedMessage`'s parameters must match its real signature in `home/FeedStatus.kt:100`. If
`actionLabel`/`onAction` are not optional, give them `null` defaults there. Use 1 loading column
on compact widths and `feedGridColumns()` otherwise.

```kotlin
@Composable
private fun DownloadsHeader(storage: String?) {
    BrandBackground(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(Res.string.downloads_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            if (storage != null) {
                Text(text = storage, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

// Mockup 05's "Ready to play" green.
private val ReadyColor = Color(0xFF1DB954)

@Composable
private fun DownloadRow(item: DownloadItemUi, onIntent: (DownloadsIntent) -> Unit) {
    val status = item.status
    val inProgress = status is DownloadItemStatus.Downloading ||
        status == DownloadItemStatus.Queued ||
        status == DownloadItemStatus.WaitingForNetwork
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = status is DownloadItemStatus.Completed) { onIntent(DownloadsIntent.Open(item.videoId)) }
            .padding(vertical = 4.dp),
    ) {
        VideoThumbnail(url = item.thumbnailUrl, contentDescription = null, modifier = Modifier.width(120.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            DownloadStatusLine(status = status, onRetry = { onIntent(DownloadsIntent.Retry(item.videoId)) })
        }
        IconButton(
            onClick = {
                onIntent(if (inProgress) DownloadsIntent.Cancel(item.videoId) else DownloadsIntent.RequestRemove(item.videoId))
            },
        ) {
            Icon(
                painter = painterResource(if (inProgress) Res.drawable.ic_close else Res.drawable.ic_delete),
                contentDescription = stringResource(if (inProgress) Res.string.cd_cancel_download else Res.string.cd_remove_download),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadStatusLine(status: DownloadItemStatus, onRetry: () -> Unit) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (status) {
        is DownloadItemStatus.Downloading -> {
            val progress = status.progress
            val barModifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
            if (progress == null) {
                LinearProgressIndicator(modifier = barModifier)
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = barModifier)
            }
            Spacer(Modifier.height(4.dp))
            Text(status.text.asString(), style = labelStyle, color = muted)
        }
        DownloadItemStatus.Queued -> Text(stringResource(Res.string.downloads_queued), style = labelStyle, color = muted)
        DownloadItemStatus.WaitingForNetwork -> Text(stringResource(Res.string.downloads_waiting), style = labelStyle, color = muted)
        is DownloadItemStatus.Completed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ReadyColor))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.downloads_ready), style = labelStyle, color = ReadyColor)
            }
            Text(status.text.asString(), style = labelStyle, color = muted)
        }
        DownloadItemStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.downloads_failed), style = labelStyle, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(Res.string.downloads_retry))
            }
        }
    }
}
```

If the theme already defines a success color, use it instead of `ReadyColor`. Check that the
green passes contrast on both the dark and light backgrounds, and darken it for light mode if it
does not. Add `@Preview`s for loaded (one row of each status), empty, and error, each wrapped in
`StreamlyTheme`, as `HomeScreen.kt` does.

- [ ] **Step 10: Add the tab and the route**

1. In `Route.kt`, add `@Serializable data object Downloads : Route`.
2. In `TopLevelDestination`, add `DOWNLOADS(Route.Downloads, Res.string.nav_downloads, Res.drawable.ic_download)` after `SHORTS`, with its imports.
3. In `AppNavigation.kt`, add `subclass(Route.Downloads::class)` to the serializers module and this entry:

```kotlin
                entry<Route.Downloads> {
                    DownloadsRoot(
                        bottomInset = bottomInset,
                        onNavigateToPlayer = { videoId -> backStack.add(Route.Player(videoId)) },
                    )
                }
```

4. In `PresentationModule.kt`, add `viewModelOf(::DownloadsViewModel)`.
5. In `BackStackTest`, check whether any test enumerates `TopLevelDestination.entries`; update its expectations if so.

- [ ] **Step 11: Build and run the whole check**

Run: `rtk proxy ./gradlew :androidApp:assembleDebug check`
Expected: BUILD SUCCESSFUL. Lint shows only the 8 warnings that predate this work, and every
module's host tests pass.

- [ ] **Step 12: Stage the commit:** `feat(downloads): add the Downloads screen and tab`

---

### Task 7: Device verification, README, and agent log

**Files:** Modify `README.md`, `docs/agent-log.md`, and `FRESH_PROMPT.md` (local, gitignored).

- [ ] **Step 1: Install on both devices.** `export ANDROID_SERIAL=emulator-5554`, then run `rtk proxy ./gradlew :androidApp:installDebug`. Repeat for `R58T90FB67Y`. Watch with `adb logcat -s StreamlyDownloads StreamlyPlayer DownloadManager`.
- [ ] **Step 2: Real progress.** Open Big Buck Bunny and tap Download. Allow notifications on the first prompt. Capture screenshots at two points showing the Player ring percent increasing, the Downloads row (percent and MB) matching it, and the notification percent (`adb shell dumpsys notification --noredact | grep -A3 com.shafayatb.streamly`). Check that the downloaded variant is 480p: `adb shell run-as com.shafayatb.streamly ls files/media-cache | head` and the size in Downloads (about 66 MB).
- [ ] **Step 3: Background, rotation, restart.** Start a second download, press Home, wait, and check that the notification percent kept advancing. Return, rotate on Downloads, and check that nothing resets. Run `adb shell am force-stop com.shafayatb.streamly` mid-download, relaunch, and check that the download resumes and the completed one is still listed.
- [ ] **Step 4: Offline playback.** Run `adb shell svc wifi disable; adb shell svc data disable`. From Downloads, open the completed video. It plays; seek to 50% and near the end, and let it end. In logcat, check for `StreamlyDownloads: Playing download big-buck-bunny (… stream keys)`, a `videoInputFormat` of 848x480, and no `loadError` lines. Also check that a video that is *not* downloaded shows the playback error overlay.
- [ ] **Step 5: Restore straight onto the Player (Review Focus 2).** On the offline downloaded Player, run `adb shell am kill com.shafayatb.streamly` (after pressing Home, so the process dies in the background), then relaunch from recents. It must play offline.
- [ ] **Step 6: Removal.** Record the storage line and `adb shell run-as com.shafayatb.streamly du -sh files/media-cache`. Remove the download through the dialog. The storage figure drops and `du` shrinks. Opening the video offline now fails with the error overlay. Re-enable the network.
- [ ] **Step 7: Edge states.** Turn data off and tap Download on a new video: the "Couldn’t start the download" snackbar shows. Start a download, turn the network off mid-way: the row shows "Waiting for network" and the Player shows "Waiting"; turn the network on and it resumes. Cancel from the Player ring and from the row. A live video has no Download action. Check the empty state after removing everything.
- [ ] **Step 8: Regression.** Shorts autoplay with 2 `ExoPlayerImpl … Init` lines after 10+ swipes and one AudioTrack started. A non-downloaded long-form video streams with ABR (several `videoInputFormat` switches). The Activity count is 1 after the player → Back → Downloads → 6 rotations (`am dumpheap` + `dumpsys meminfo`).
- [ ] **Step 9: Layouts.** Downloads and the Player in dark and light, phone landscape (rail), and the 2400x1800 tablet at 320 dpi. Restore the settings afterwards.
- [ ] **Step 10: README.** Update the status line. Add a "Downloads" section under Architecture: the one cache and database provider, the rendition choice and why the player plays the stream keys, polling, the service and its notification, the network policy, no scheduler, and resume at launch. Update Shortcuts and Known gaps: Coil-only offline thumbnails, no network preference, and Player cancel without confirmation.
- [ ] **Step 11: Agent log.** Append the task 6 entry in the task 5 shape: prompt, how the agent worked (brainstorming → this plan → TDD), decisions, problems found, verification evidence, and commits. Link this plan. Fill in the commit hashes after the commits are made, in the docs commit.
- [ ] **Step 12: Stage, show `git status` and a `git diff --cached` summary, propose `docs: document downloads and log task 6`, and stop for approval.** After approval, make the commits as a series. Merge `feature/downloads` into `develop` with `--no-ff` only when told to. Then replace `FRESH_PROMPT.md` with the Profile task, keeping "How to work this task".

---

## Self-review notes

- **Spec coverage:**
  - Domain contract: Task 2.
  - The one `DownloadManager` over the shared cache, database provider, and upstream: Task 3, steps 8 and 10.
  - The 480p rendition and the metadata in `data`: Task 3.
  - The `dataSync` service, its notification, and the permission: Task 3, steps 11–12, and Task 5, step 10.
  - The network policy: the default `Requirements.NETWORK`, documented in Task 7, step 10.
  - Polled progress and the host-tested mapping: Task 3.
  - Offline playback through `playbackDataSourceFactory` with stream keys: Task 4.
  - Live downloads hidden: Tasks 5 and 3.
  - Player intents: Task 5. `DownloadsViewModel` and its screen: Task 6. The third tab: Task 6.
  - Device acceptance: Task 7. README and agent log: Task 7.
- **Changed from the agreed design:** `getScheduler()` returns `null` (see "Agreed design → `:core:media`").
- **Type consistency:** `DownloadRepository.retry/remove(videoId: String)`, `download(video: Video)`, `downloadActionOf(isLive: Boolean?, download: VideoDownload?, isStarting: Boolean)`, and `DownloadActionUi.Downloading(percent: Int?)` are the same in every task that uses them.
