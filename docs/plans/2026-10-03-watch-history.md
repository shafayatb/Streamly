# Watch History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Profile's "Watch history is coming soon" stub with a real, per-account watch
history that the long-form Player records into and resumes from.

**Architecture:** `:domain` gets the rules (`WatchHistoryEntry`: resume position and progress), a
storage contract (`WatchHistoryStore`), and the account-aware `AccountWatchHistory`, which
implements the `WatchHistoryRepository` that screens use. `:data` stores one JSON list per account
in a new `watch_history` Preferences DataStore. `PlayerViewModel` reads the resume position
before loading and records progress at the moments it already knows about (first play, pause,
background, end, Back, up next, and a 10-second checkpoint). The one media change is a
`startPosition` on `VideoPlayer.load`, passed to Media3's `setMediaItem(item, startPositionMs)`.
A new MVI screen in `:shared` lists, removes, and clears entries.

**Tech Stack:** Kotlin 2.4, Coroutines/Flow, kotlinx.serialization JSON, DataStore Preferences,
Media3 1.11 ExoPlayer, Compose Multiplatform 1.12 / Material 3, Navigation 3, Koin 4.2,
`kotlin.test`, `kotlinx-coroutines-test`, Turbine, Compose UI tests.

**Spec:** this file. The design was agreed in chat on October 3 (summary below).

## Agreed design (brainstorming, October 3)

- **Resume + list (user).**
  - History stores a resume position, and the Player starts from it.
  - Rule: resume only if the saved position is at least **5 s**. Start over if it is in the last
    **10 s** or past **95%** of the length (the video counts as *finished*).
  - Live streams never resume and store no position.
- **What counts as watched (user).** Long-form videos only; Shorts are never recorded.
  - A video enters history the first time it actually plays (`isPlaying`), not when the Player
    opens. A load that fails or never starts records nothing, even on leaving.
  - Live streams are listed (recorded once at first play) with no position and no progress bar.
- **Per account, guest included (user).**
  - Keyed by the existing `Session.accountKey()` (`email:<address>` or `guest`), like download
    owners.
  - Signing out hides the history; signing in as the same account shows it again (mockup 07's
    "sign in again to see your downloads and history"). Resume positions are per account.
  - With no session, writes are dropped and the list is empty.
- **Storage (user).**
  - A new Preferences DataStore file `watch_history`, one string key per account
    (`history/<accountKey>`) holding a JSON list.
  - Each entry snapshots title, channel name, thumbnail URL, and duration, so the screen lists
    offline and after a video leaves the catalog.
  - Newest first; re-watching moves an entry to the top; **at most 100 entries** per account.
  - A value that no longer decodes reads as empty (start fresh). An I/O failure is a
    `DataError.Local` failure.
- **Approach 1 (user).** The Player screen drives recording; a domain service writes it.
  - `AccountWatchHistory` queues writes on an application scope, in call order, so the save made
    in `PlayerViewModel.onCleared` still lands after `viewModelScope` is cancelled.
  - Resume goes through `VideoPlayer.load(video, playWhenReady, startPosition)`. `ExoVideoPlayer`
    uses `setMediaItem(item, startPositionMs)` only for a positive start on a non-live video, so
    live keeps joining at its live edge and the first frame drawn is the resume point (no 0:00
    flash, no extra seek). The offline download item resumes the same way.
- **When the Player records** (only while its screen owns the shared player):
  - The first time the video plays.
  - Every 10 s of position change while playing (checkpoint).
  - On pause, when the screen is hidden (background), and when the video ends (the full length,
    so the next open starts over).
  - Before `stop()` on Back (`onCleared`) and on an up-next pick.
  - A save is skipped when the position equals the last saved one. Each save refreshes
    `watchedAt` and moves the entry to the top.
  - No "Resumed from …" message: the seek bar already shows the position.
- **Screen (user: list, remove one, clear all here).**
  - `Route.WatchHistory` is pushed from Profile's Watch history row: full window with no tab bar,
    like the Player. Back returns to Profile. Guests can open it.
  - A brand header with back, "Watch history", and "Clear all" (hidden when there is nothing to
    clear). Clear all asks "Clear watch history?" (Cancel / Clear); the dialog lives in state, so
    it survives rotation.
  - Rows, as in Downloads: a 120 dp thumbnail with the duration badge (or LIVE) and a coral
    progress bar along its bottom (none for live), the title, "Channel · 2 hours ago" (when it was
    last watched), and an X that removes the row at once (no confirmation, no undo).
  - Tapping a row pushes the Player, which resumes; Back returns to the history.
  - Loading, empty ("No watch history yet" / "Videos you watch will show up here."), and error
    (with Try again) states. A failed remove or clear shows "Couldn’t update your watch history."
    A failed background save is silent (the next checkpoint writes again).
  - One column on phones, the feed's `WindowSizeClass` columns on medium and expanded widths.
- **Profile.** The Watch history row navigates instead of showing a snackbar; Settings keeps its
  "coming soon" stub until `feature/settings`.

## Global Constraints

- Kotlin only, Compose for all UI, MVVM + MVI (one `ViewModel`, one immutable `UiState` as
  `StateFlow`, one sealed intent type, one-off events through a `Channel`).
- `:domain` stays pure Kotlin (only `kotlinx-coroutines-core`); Media3 types stay in
  `:core:media`; DataStore and serialization stay in `:data`.
- Library modules use explicit API mode: write `public` on every public declaration in
  `:domain`, `:data`, `:core:media`, and `:core:designsystem`.
- Every dependency goes through `gradle/libs.versions.toml`. This plan adds none.
- Resume constants: minimum **5 s**, finished margin **10 s**, finished fraction **0.95**;
  checkpoint **10 s**; cap **100** entries per account.
- Compose resource strings use the typographic apostrophe (’), never `\'`.
- Raw tool output: `rtk proxy ./gradlew …`, `rtk proxy git …`. Run Gradle without `-q`.
- No commit or merge without the user's explicit approval. Conventional Commits, with the
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session:` trailers.

## Review Focus

1. **Live streams must still join at the live edge.** If a start position ever reaches
   `setMediaItem` for live, the stream starts at the beginning of its window, minutes behind.
   Expected: live always loads with the default position. Pinned by
   `aLiveVideoAlwaysStartsAtTheLiveEdge` (Task 4) and the device script's live check.
2. **Leaving the Player while the saved position is still being read.** Back during the read
   must not load a video into the shared player after the screen is gone (audio from nowhere).
   Expected: nothing loads. Pinned by `leavingBeforeTheSavedPositionArrivesLoadsNothing` (Task 4).
3. **A screen that has been replaced must not write.** The stale Player of an up-next chain must
   not record its old video at the new video's position. Expected: only the owning screen
   records. Pinned by `aReplacedScreenRecordsNothing` (Task 4).
4. **Accounts must not see each other's history or resume points.** Expected: Jane neither lists
   nor resumes from Anika's entries; signed out sees nothing and records nothing. Pinned by
   `eachAccountSeesOnlyItsOwnHistory`, `resumeComesFromTheCurrentAccountOnly`, and
   `signedOutRecordsNothingAndListsNothing` (Task 2), plus the device script.
5. **Process death and offline.** A video backgrounded at 3:12 and killed must reopen near 3:12,
   and a downloaded video must resume offline from the saved rendition. Expected: both resume.
   Pinned by `savesWhenTheScreenIsHidden` (Task 4) on the host, and device steps 5–6.

---

## File structure

| File | Responsibility |
|---|---|
| `domain/.../domain/history/WatchHistoryEntry.kt` (create) | The entry model and the resume and progress rules. |
| `domain/.../domain/history/WatchHistoryStore.kt` (create) | Storage contract, keyed by account. |
| `domain/.../domain/history/WatchHistoryRepository.kt` (create) | What screens use: entries, resume position, record, remove, clear. |
| `domain/.../domain/history/AccountWatchHistory.kt` (create) | Account-aware implementation with an ordered write queue. |
| `domain/.../commonTest/.../history/WatchHistoryEntryTest.kt`, `AccountWatchHistoryTest.kt`, `HistoryFakes.kt` (create) | Domain tests and an in-memory store. |
| `domain/.../domain/player/VideoPlayer.kt` (modify) | `load(…, startPosition)`. |
| `data/.../data/history/WatchHistoryEntryDto.kt` (create) | The serialized entry and its mappers. |
| `data/.../data/history/DataStoreWatchHistoryStore.kt` (create) | JSON per account in DataStore. |
| `data/.../androidHostTest/.../history/DataStoreWatchHistoryStoreTest.kt` (create) | Store tests on a real DataStore. |
| `data/.../data/di/DataModule.kt` (modify) | Registers the store and `AccountWatchHistory`. |
| `core/media/.../player/ExoVideoPlayer.kt` (modify) | Starts at the given position. |
| `shared/.../player/PlayerViewModel.kt` (modify) | Resume before load; record progress. |
| `shared/.../commonTest/.../player/PlayerHistoryTest.kt` (create) | Player resume and recording tests. |
| `shared/.../commonTest/.../testing/FakeWatchHistoryRepository.kt` (create) | Fake for ViewModel tests. |
| `shared/.../commonTest/.../testing/FakeVideoPlayer.kt` (modify) | Start position, load status. |
| `shared/.../history/WatchHistoryState.kt`, `WatchHistoryIntent.kt`, `WatchHistoryEvent.kt`, `WatchHistoryViewModel.kt` (create) | The screen's MVI. |
| `shared/.../history/WatchHistoryScreen.kt`, `ClearHistoryDialog.kt` (create) | The screen's UI. |
| `shared/.../commonTest/.../history/WatchHistoryViewModelTest.kt` (create) | ViewModel tests. |
| `shared/.../androidDeviceTest/.../history/WatchHistoryScreenTest.kt` (create) | Compose device test. |
| `core/designsystem/.../components/WatchProgressBar.kt` (create) | The coral bar on thumbnails. |
| `shared/.../navigation/Route.kt`, `AppNavigation.kt` (modify) | The new destination. |
| `shared/.../profile/ProfileViewModel.kt`, `ProfileEvent.kt`, `ProfileScreen.kt` (modify) | The row navigates. |
| `shared/.../di/PresentationModule.kt` (modify) | ViewModels. |
| `shared/src/commonMain/composeResources/values/strings.xml` (modify) | History strings; drop `profile_history_soon`. |

(`...` is `src/commonMain/kotlin/com/shafayatb/streamly` unless a source set is named.)

---

### Task 1: Entry model and resume rules (`:domain`)

**Files:**
- Create: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/history/WatchHistoryEntry.kt`
- Test: `domain/src/commonTest/kotlin/com/shafayatb/streamly/domain/history/WatchHistoryEntryTest.kt`

**Interfaces:**
- Produces: `WatchHistoryEntry(videoId, title, channelName, thumbnailUrl, duration: Duration?, position: Duration, watchedAt: Instant)` with `isLive`, `isFinished`, `resumePosition: Duration`, `progress: Float?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.shafayatb.streamly.domain.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WatchHistoryEntryTest {

    private fun entry(position: Duration, duration: Duration? = 10.minutes) = WatchHistoryEntry(
        videoId = "v",
        title = "Video",
        channelName = "Channel",
        thumbnailUrl = "",
        duration = duration,
        position = position,
        watchedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun resumesASavedPosition() {
        val entry = entry(3.minutes)

        assertEquals(3.minutes, entry.resumePosition)
        assertEquals(0.3f, entry.progress)
        assertFalse(entry.isFinished)
    }

    @Test
    fun startsOverWhenBarelyStarted() {
        assertEquals(Duration.ZERO, entry(4.seconds).resumePosition)
        assertEquals(5.seconds, entry(5.seconds).resumePosition)
    }

    @Test
    fun past95PercentCountsAsFinished() {
        // 95% of 10 minutes is 9:30.
        assertEquals(9.minutes + 29.seconds, entry(9.minutes + 29.seconds).resumePosition)
        val finished = entry(9.minutes + 30.seconds)
        assertTrue(finished.isFinished)
        assertEquals(Duration.ZERO, finished.resumePosition)
        assertEquals(1f, finished.progress)
    }

    @Test
    fun theLastTenSecondsOfAShortVideoCountAsFinished() {
        // For 2 minutes, the 10-second margin (1:50) comes before 95% (1:54).
        assertEquals(109.seconds, entry(109.seconds, duration = 2.minutes).resumePosition)
        assertTrue(entry(110.seconds, duration = 2.minutes).isFinished)
    }

    @Test
    fun aLongVideoResumesUntil95Percent() {
        assertEquals(56.minutes, entry(56.minutes, duration = 1.hours).resumePosition)
        assertTrue(entry(57.minutes, duration = 1.hours).isFinished)
    }

    @Test
    fun anEndedVideoStartsOver() {
        val ended = entry(10.minutes)

        assertEquals(Duration.ZERO, ended.resumePosition)
        assertEquals(1f, ended.progress)
    }

    @Test
    fun liveNeverResumesAndHasNoProgress() {
        val live = entry(3.minutes, duration = null)

        assertTrue(live.isLive)
        assertFalse(live.isFinished)
        assertEquals(Duration.ZERO, live.resumePosition)
        assertNull(live.progress)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `rtk proxy ./gradlew :domain:testAndroidHostTest --tests "*WatchHistoryEntryTest"`
Expected: FAIL, compilation error `Unresolved reference 'WatchHistoryEntry'`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.shafayatb.streamly.domain.history

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A video an account has watched, with enough of its metadata to list it without the catalog
 * (offline, or after the video leaves the catalog).
 */
public data class WatchHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** `null` for live streams. */
    val duration: Duration?,
    /** Where playback was last saved; always zero for live streams. */
    val position: Duration,
    /** When it was last watched. */
    val watchedAt: Instant,
) {
    public val isLive: Boolean get() = duration == null

    /** Close enough to the end that the next viewing starts over, as video apps do. */
    public val isFinished: Boolean
        get() = duration != null &&
            (position >= duration - FinishedMargin || position >= duration * FinishedFraction)

    /** Where the player should start: the saved position, or zero for live, barely started, or finished videos. */
    public val resumePosition: Duration
        get() = if (isLive || isFinished || position < MinimumResume) Duration.ZERO else position

    /** How much has been watched, 0–1, for a progress bar; `null` for live streams. */
    public val progress: Float?
        get() {
            val total = duration ?: return null
            if (isFinished) return 1f
            return (position / total).toFloat().coerceIn(0f, 1f)
        }

    public companion object {
        public val MinimumResume: Duration = 5.seconds
        public val FinishedMargin: Duration = 10.seconds
        public const val FinishedFraction: Double = 0.95
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `rtk proxy ./gradlew :domain:testAndroidHostTest --tests "*WatchHistoryEntryTest"`
Expected: PASS, 7 tests.

---

### Task 2: Account-scoped history (`:domain`)

**Files:**
- Create: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/history/WatchHistoryStore.kt`
- Create: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/history/WatchHistoryRepository.kt`
- Create: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/history/AccountWatchHistory.kt`
- Create: `domain/src/commonTest/kotlin/com/shafayatb/streamly/domain/history/HistoryFakes.kt`
- Test: `domain/src/commonTest/kotlin/com/shafayatb/streamly/domain/history/AccountWatchHistoryTest.kt`

**Interfaces:**
- Consumes: `WatchHistoryEntry` (Task 1); `SessionRepository`, `Session.accountKey()`, `Video`,
  `Result`, `EmptyResult`, `DataError.Local` (existing). Test helpers `FakeSessions` and
  `video(id, duration)` from `com.shafayatb.streamly.domain.download` (existing, `DownloadFakes.kt`).
- Produces:
  - `interface WatchHistoryStore { fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>>; suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local>; suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local>; suspend fun clear(account: String): EmptyResult<DataError.Local> }`
  - `interface WatchHistoryRepository { val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>>; suspend fun resumePosition(videoId: String): Duration; fun record(video: Video, position: Duration, duration: Duration? = video.duration); suspend fun remove(videoId: String): EmptyResult<DataError.Local>; suspend fun clear(): EmptyResult<DataError.Local> }`
  - `class AccountWatchHistory(store: WatchHistoryStore, sessionRepository: SessionRepository, clock: Clock, scope: CoroutineScope) : WatchHistoryRepository`

- [ ] **Step 1: Write the contracts** (interfaces only, so the test compiles against them)

`WatchHistoryStore.kt`:

```kotlin
package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Persists each account's watch history, newest first. [account] is a `Session.accountKey()`. */
public interface WatchHistoryStore {
    public fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>>

    /** Puts [entry] at the top, replacing any entry for the same video, and keeps the newest 100. */
    public suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local>

    public suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local>

    public suspend fun clear(account: String): EmptyResult<DataError.Local>
}
```

`WatchHistoryRepository.kt`:

```kotlin
package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** The signed-in account's watch history. */
public interface WatchHistoryRepository {
    /** Newest first; empty while nobody is signed in. */
    public val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>>

    /** Where [videoId] should start for this account: zero without a usable entry or when the history cannot be read. */
    public suspend fun resumePosition(videoId: String): Duration

    /**
     * Records that [video] was watched up to [position] and moves it to the top. [duration] is the
     * player's length when known, which can differ slightly from the catalog's. The write happens
     * in the background and outlives the caller; a failure is dropped.
     */
    public fun record(video: Video, position: Duration, duration: Duration? = video.duration)

    public suspend fun remove(videoId: String): EmptyResult<DataError.Local>

    public suspend fun clear(): EmptyResult<DataError.Local>
}
```

- [ ] **Step 2: Write the fake store**

`HistoryFakes.kt`:

```kotlin
package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [WatchHistoryStore] with the real store's ordering rules. */
class FakeWatchHistoryStore : WatchHistoryStore {
    val stored = MutableStateFlow<Map<String, List<WatchHistoryEntry>>>(emptyMap())
    var readFailure: DataError.Local? = null

    /** When set, [upsert] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>> =
        stored.map { all ->
            readFailure?.let { Result.Failure(it) } ?: Result.Success(all[account].orEmpty())
        }

    override suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local> {
        gate?.await()
        val others = stored.value[account].orEmpty().filter { it.videoId != entry.videoId }
        stored.value += account to (listOf(entry) + others)
        return Result.Success(Unit)
    }

    override suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local> {
        stored.value += account to stored.value[account].orEmpty().filter { it.videoId != videoId }
        return Result.Success(Unit)
    }

    override suspend fun clear(account: String): EmptyResult<DataError.Local> {
        stored.value -= account
        return Result.Success(Unit)
    }
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.download.FakeSessions
import com.shafayatb.streamly.domain.download.video
import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AccountWatchHistoryTest {

    private val anika = Session.SignedIn(User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE))
    private val jane = Session.SignedIn(User("Jane Doe", "jane@example.com", AuthProvider.EMAIL))
    private val anikaKey = "email:anika@streamly.app"

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val store = FakeWatchHistoryStore()
    private val sessions = FakeSessions(anika)

    private fun TestScope.history() = AccountWatchHistory(store, sessions, clock, backgroundScope)

    private suspend fun WatchHistoryRepository.ids(): List<String> =
        (entries.first() as Result.Success).data.map { it.videoId }

    @Test
    fun recordsUnderTheSignedInAccount() = runTest(UnconfinedTestDispatcher()) {
        history().record(video("a"), 2.minutes)

        val expected = WatchHistoryEntry(
            videoId = "a",
            title = "Video a",
            channelName = "Channel",
            thumbnailUrl = "",
            duration = 10.minutes,
            position = 2.minutes,
            watchedAt = now,
        )
        assertEquals(mapOf(anikaKey to listOf(expected)), store.stored.value)
    }

    @Test
    fun eachAccountSeesOnlyItsOwnHistory() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 1.minutes)

        sessions.session.value = jane
        assertEquals(emptyList(), history.ids())
        history.record(video("b"), 1.minutes)
        assertEquals(listOf("b"), history.ids())

        sessions.session.value = anika
        assertEquals(listOf("a"), history.ids())
    }

    @Test
    fun signedOutRecordsNothingAndListsNothing() = runTest(UnconfinedTestDispatcher()) {
        sessions.session.value = null
        val history = history()

        history.record(video("a"), 1.minutes)

        assertEquals(emptyMap(), store.stored.value)
        assertEquals(Result.Success(emptyList()), history.entries.first())
    }

    @Test
    fun writesLandInCallOrder() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        val history = history()

        history.record(video("a"), 1.minutes)
        history.record(video("a"), 2.minutes)
        gate.complete(Unit)

        assertEquals(listOf(2.minutes), store.stored.value.getValue(anikaKey).map { it.position })
    }

    @Test
    fun liveIsRecordedWithoutAPosition() = runTest(UnconfinedTestDispatcher()) {
        history().record(video("live", duration = null), 3.minutes)

        val entry = store.stored.value.getValue(anikaKey).single()
        assertEquals(Duration.ZERO, entry.position)
        assertEquals(null, entry.duration)
    }

    @Test
    fun usesThePlayersDurationWhenKnown() = runTest(UnconfinedTestDispatcher()) {
        history().record(video("a"), 9.minutes, duration = 10.minutes + 3.seconds)

        assertEquals(10.minutes + 3.seconds, store.stored.value.getValue(anikaKey).single().duration)
    }

    @Test
    fun resumeComesFromTheCurrentAccountOnly() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 3.minutes)

        assertEquals(3.minutes, history.resumePosition("a"))
        sessions.session.value = jane
        assertEquals(Duration.ZERO, history.resumePosition("a"))
    }

    @Test
    fun resumeIsZeroWhenTheHistoryCannotBeRead() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 3.minutes)
        store.readFailure = DataError.Local.UNKNOWN

        assertEquals(Duration.ZERO, history.resumePosition("a"))
    }

    @Test
    fun removeAndClearActOnTheCurrentAccountOnly() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 1.minutes)
        history.record(video("b"), 1.minutes)
        sessions.session.value = jane
        history.record(video("c"), 1.minutes)

        history.clear()
        sessions.session.value = anika
        history.remove("b")

        assertEquals(listOf("a"), history.ids())
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `rtk proxy ./gradlew :domain:testAndroidHostTest --tests "*AccountWatchHistoryTest"`
Expected: FAIL, compilation error `Unresolved reference 'AccountWatchHistory'`.

- [ ] **Step 5: Write the implementation**

`AccountWatchHistory.kt`:

```kotlin
package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.session.accountKey
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The signed-in account's watch history, kept like its downloads: signing out hides it, signing
 * in again as the same account shows it, and a guest has its own.
 *
 * [record] queues its write in [scope], one at a time in call order, so a save made as a screen
 * closes still lands after that screen's own scope is cancelled. [scope] should live as long as
 * the app.
 */
public class AccountWatchHistory(
    private val store: WatchHistoryStore,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
    scope: CoroutineScope,
) : WatchHistoryRepository {

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) write()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>> =
        sessionRepository.session.flatMapLatest { session ->
            if (session == null) flowOf(Result.Success(emptyList())) else store.entries(session.accountKey())
        }

    override suspend fun resumePosition(videoId: String): Duration =
        when (val result = entries.first()) {
            is Result.Success -> result.data.firstOrNull { it.videoId == videoId }?.resumePosition ?: Duration.ZERO
            is Result.Failure -> Duration.ZERO
        }

    override fun record(video: Video, position: Duration, duration: Duration?) {
        val entry = WatchHistoryEntry(
            videoId = video.id,
            title = video.title,
            channelName = video.channel.name,
            thumbnailUrl = video.thumbnailUrl,
            duration = if (video.isLive) null else duration ?: video.duration,
            position = if (video.isLive) Duration.ZERO else position,
            watchedAt = clock.now(),
        )
        writes.trySend { withAccount { account -> store.upsert(account, entry) } }
    }

    override suspend fun remove(videoId: String): EmptyResult<DataError.Local> =
        withAccount { account -> store.remove(account, videoId) }

    override suspend fun clear(): EmptyResult<DataError.Local> = withAccount { account -> store.clear(account) }

    // With nobody signed in there is no history to change, so the write is dropped, never misfiled.
    private suspend fun withAccount(
        write: suspend (account: String) -> EmptyResult<DataError.Local>,
    ): EmptyResult<DataError.Local> {
        val session = sessionRepository.session.first() ?: return Result.Success(Unit)
        return write(session.accountKey())
    }
}
```

- [ ] **Step 6: Run the domain tests**

Run: `rtk proxy ./gradlew :domain:testAndroidHostTest`
Expected: PASS, 30 existing + 7 + 9 = 46 tests.

- [ ] **Step 7: Stage for commit 2** (`feat(domain): add account-scoped watch history and resume rules`). Do not commit.

---

### Task 3: DataStore storage (`:data`)

**Files:**
- Create: `data/src/commonMain/kotlin/com/shafayatb/streamly/data/history/WatchHistoryEntryDto.kt`
- Create: `data/src/commonMain/kotlin/com/shafayatb/streamly/data/history/DataStoreWatchHistoryStore.kt`
- Modify: `data/src/androidMain/kotlin/com/shafayatb/streamly/data/di/DataModule.kt`
- Test: `data/src/androidHostTest/kotlin/com/shafayatb/streamly/data/history/DataStoreWatchHistoryStoreTest.kt`

**Interfaces:**
- Consumes: `WatchHistoryStore`, `WatchHistoryEntry`, `AccountWatchHistory`, `WatchHistoryRepository` (Task 2); `tryEdit` and `createPreferencesDataStore` (existing).
- Produces: `internal class DataStoreWatchHistoryStore(dataStore: DataStore<Preferences>) : WatchHistoryStore`; Koin binding `single<WatchHistoryRepository>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.shafayatb.streamly.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreWatchHistoryStoreTest {

    private val directory: File = createTempDirectory("history-test").toFile()
    private val storeFile = File(directory, "watch_history.preferences_pb")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun TestScope.newDataStore(job: Job = Job()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
            produceFile = { storeFile },
        )

    private fun entry(id: String, position: Duration = 1.minutes, duration: Duration? = 10.minutes) = WatchHistoryEntry(
        videoId = id,
        title = "Video $id",
        channelName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        duration = duration,
        position = position,
        watchedAt = Instant.fromEpochMilliseconds(1_000),
    )

    private suspend fun DataStoreWatchHistoryStore.ids(account: String) =
        (entries(account).first() as Result.Success).data.map { it.videoId }

    @Test
    fun anAccountWithoutHistoryHasNoEntries() = runTest {
        assertEquals(Result.Success(emptyList()), DataStoreWatchHistoryStore(newDataStore()).entries("guest").first())
    }

    @Test
    fun theNewestIsFirstAndARewatchMovesToTheTop() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())

        store.upsert("guest", entry("a"))
        store.upsert("guest", entry("b"))
        store.upsert("guest", entry("a", position = 4.minutes))

        val entries = (store.entries("guest").first() as Result.Success).data
        assertEquals(listOf("a", "b"), entries.map { it.videoId })
        assertEquals(4.minutes, entries.first().position)
    }

    @Test
    fun historyKeepsTheNewest100() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())

        repeat(105) { store.upsert("guest", entry("v$it")) }

        val ids = store.ids("guest")
        assertEquals(100, ids.size)
        assertEquals("v104", ids.first())
        assertEquals("v5", ids.last())
    }

    @Test
    fun accountsAreKeptApart() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())

        store.upsert("email:a@x.com", entry("a"))
        store.upsert("guest", entry("b"))

        assertEquals(listOf("a"), store.ids("email:a@x.com"))
        assertEquals(listOf("b"), store.ids("guest"))
    }

    @Test
    fun removeDropsOneEntryAndClearDropsOneAccount() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())
        store.upsert("guest", entry("a"))
        store.upsert("guest", entry("b"))
        store.upsert("email:a@x.com", entry("c"))

        assertEquals(Result.Success(Unit), store.remove("guest", "a"))
        assertEquals(listOf("b"), store.ids("guest"))
        assertEquals(Result.Success(Unit), store.clear("guest"))
        assertEquals(emptyList(), store.ids("guest"))
        assertEquals(listOf("c"), store.ids("email:a@x.com"))
    }

    @Test
    fun everyFieldSurvivesARestart() = runTest {
        val firstJob = Job()
        val live = entry("live", position = Duration.ZERO, duration = null)
        DataStoreWatchHistoryStore(newDataStore(firstJob)).run {
            upsert("guest", entry("a", position = 3.minutes))
            upsert("guest", live)
        }
        firstJob.cancel()

        val restarted = DataStoreWatchHistoryStore(newDataStore())

        assertEquals(Result.Success(listOf(live, entry("a", position = 3.minutes))), restarted.entries("guest").first())
    }

    @Test
    fun aValueThatNoLongerDecodesStartsAfresh() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("history/guest")] = "not json" }
        val store = DataStoreWatchHistoryStore(dataStore)

        assertEquals(Result.Success(emptyList()), store.entries("guest").first())
        store.upsert("guest", entry("a"))
        assertEquals(listOf("a"), store.ids("guest"))
    }

    @Test
    fun aReadErrorIsAFailure() = runTest {
        val store = DataStoreWatchHistoryStore(FailingDataStore(IOException("broken")))

        assertEquals(Result.Failure(DataError.Local.UNKNOWN), store.entries("guest").first())
    }

    @Test
    fun aFullDiskIsReported() = runTest {
        val store = DataStoreWatchHistoryStore(FailingDataStore(IOException("No space left on device")))

        assertEquals(Result.Failure(DataError.Local.DISK_FULL), store.upsert("guest", entry("a")))
    }

    private class FailingDataStore(private val error: IOException) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `rtk proxy ./gradlew :data:testAndroidHostTest --tests "*DataStoreWatchHistoryStoreTest"`
Expected: FAIL, compilation error `Unresolved reference 'DataStoreWatchHistoryStore'`.

- [ ] **Step 3: Write the DTO**

```kotlin
package com.shafayatb.streamly.data.history

import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class WatchHistoryEntryDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** `null` for live streams. */
    val durationMs: Long? = null,
    val positionMs: Long = 0,
    val watchedAtMs: Long,
)

internal fun WatchHistoryEntryDto.toEntry(): WatchHistoryEntry = WatchHistoryEntry(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    duration = durationMs?.milliseconds,
    position = positionMs.milliseconds,
    watchedAt = Instant.fromEpochMilliseconds(watchedAtMs),
)

internal fun WatchHistoryEntry.toDto(): WatchHistoryEntryDto = WatchHistoryEntryDto(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = duration?.inWholeMilliseconds,
    positionMs = position.inWholeMilliseconds,
    watchedAtMs = watchedAt.toEpochMilliseconds(),
)
```

- [ ] **Step 4: Write the store**

```kotlin
package com.shafayatb.streamly.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shafayatb.streamly.data.local.tryEdit
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.history.WatchHistoryStore
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One JSON list per account, newest first, so one write updates an account's whole history atomically. */
internal class DataStoreWatchHistoryStore(
    private val dataStore: DataStore<Preferences>,
) : WatchHistoryStore {

    override fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>> = dataStore.data
        .map<Preferences, Result<List<WatchHistoryEntry>, DataError.Local>> { preferences ->
            Result.Success(decode(preferences[keyFor(account)]).map { it.toEntry() })
        }
        .catch { emit(Result.Failure(DataError.Local.UNKNOWN)) }
        .distinctUntilChanged()

    override suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local> =
        dataStore.tryEdit { preferences ->
            val others = decode(preferences[keyFor(account)]).filter { it.videoId != entry.videoId }
            preferences[keyFor(account)] = encode((listOf(entry.toDto()) + others).take(MAX_ENTRIES))
        }

    override suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local> =
        dataStore.tryEdit { preferences ->
            val remaining = decode(preferences[keyFor(account)]).filter { it.videoId != videoId }
            if (remaining.isEmpty()) preferences.remove(keyFor(account)) else preferences[keyFor(account)] = encode(remaining)
        }

    override suspend fun clear(account: String): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it.remove(keyFor(account)) }

    // A value that no longer decodes starts the account's history afresh rather than failing every read.
    private fun decode(value: String?): List<WatchHistoryEntryDto> =
        if (value == null) {
            emptyList()
        } else {
            try {
                json.decodeFromString(listSerializer, value)
            } catch (e: IllegalArgumentException) {
                emptyList()
            }
        }

    private fun encode(entries: List<WatchHistoryEntryDto>): String = json.encodeToString(listSerializer, entries)

    private fun keyFor(account: String) = stringPreferencesKey(PREFIX + account)

    private companion object {
        const val PREFIX = "history/"
        const val MAX_ENTRIES = 100
        val json = Json { ignoreUnknownKeys = true }
        val listSerializer = ListSerializer(WatchHistoryEntryDto.serializer())
    }
}
```

(`SerializationException` extends `IllegalArgumentException`, so one catch covers malformed JSON
and a wrong shape.)

- [ ] **Step 5: Run it to verify it passes**

Run: `rtk proxy ./gradlew :data:testAndroidHostTest --tests "*DataStoreWatchHistoryStoreTest"`
Expected: PASS, 9 tests.

- [ ] **Step 6: Register it in Koin** (`DataModule.kt`, after the ownership binding)

```kotlin
    single<WatchHistoryStore> {
        DataStoreWatchHistoryStore(
            dataStore = createPreferencesDataStore(androidContext(), name = "watch_history", scope = ioScope()),
        )
    }
    // Application-scoped, so a Player's last save still lands after its screen is gone.
    single<WatchHistoryRepository> {
        AccountWatchHistory(
            store = get(),
            sessionRepository = get(),
            clock = Clock.System,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
```

Imports: `com.shafayatb.streamly.data.history.DataStoreWatchHistoryStore`,
`com.shafayatb.streamly.domain.history.AccountWatchHistory`,
`com.shafayatb.streamly.domain.history.WatchHistoryRepository`,
`com.shafayatb.streamly.domain.history.WatchHistoryStore`, `kotlin.time.Clock`.

- [ ] **Step 7: Build and run the data tests**

Run: `rtk proxy ./gradlew :data:testAndroidHostTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL; 28 + 9 = 37 data tests pass.

- [ ] **Step 8: Stage for commit 3** (`feat(data): store watch history per account in DataStore`). Do not commit.

---

### Task 4: Player resume and recording (`:domain` contract, `:core:media`, `:shared`)

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/shafayatb/streamly/domain/player/VideoPlayer.kt`
- Modify: `core/media/src/androidMain/kotlin/com/shafayatb/streamly/core/media/player/ExoVideoPlayer.kt:61-76`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/di/PresentationModule.kt`
- Modify: `shared/src/commonTest/kotlin/com/shafayatb/streamly/testing/FakeVideoPlayer.kt`
- Create: `shared/src/commonTest/kotlin/com/shafayatb/streamly/testing/FakeWatchHistoryRepository.kt`
- Modify: `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/PlayerViewModelTest.kt:67-73`, `PlayerDownloadTest.kt:52-58` (pass the new argument)
- Test: `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/PlayerHistoryTest.kt`

**Interfaces:**
- Consumes: `WatchHistoryRepository` (Task 2).
- Produces: `VideoPlayer.load(video: Video, playWhenReady: Boolean, startPosition: Duration = Duration.ZERO)`;
  `PlayerViewModel(videoId, videoRepository, videoPlayer, downloadRepository, watchHistory: WatchHistoryRepository, clock)`;
  `FakeWatchHistoryRepository` (used again in Task 5).

- [ ] **Step 1: Write the shared fake**

`FakeWatchHistoryRepository.kt`:

```kotlin
package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.history.WatchHistoryRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

class FakeWatchHistoryRepository : WatchHistoryRepository {

    /** `null` while the history is still being read. */
    val result = MutableStateFlow<Result<List<WatchHistoryEntry>, DataError.Local>?>(Result.Success(emptyList()))

    /** When set, the next collection emits this failure and completes, as DataStore's `data` with `catch` does. */
    var failNextRead: DataError.Local? = null

    override val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>> = flow {
        val failure = failNextRead
        if (failure != null) {
            failNextRead = null
            emit(Result.Failure(failure))
        } else {
            emitAll(result.filterNotNull())
        }
    }

    val resumePositions = mutableMapOf<String, Duration>()

    /** When set, [resumePosition] suspends until it completes. */
    var resumeGate: CompletableDeferred<Unit>? = null

    data class Record(val videoId: String, val position: Duration, val duration: Duration?)

    val records = mutableListOf<Record>()
    val removed = mutableListOf<String>()
    var clears = 0
        private set

    /** When set, [remove] and [clear] fail with it. */
    var writeFailure: DataError.Local? = null

    override suspend fun resumePosition(videoId: String): Duration {
        resumeGate?.await()
        return resumePositions[videoId] ?: Duration.ZERO
    }

    override fun record(video: Video, position: Duration, duration: Duration?) {
        records += Record(video.id, position, duration)
    }

    override suspend fun remove(videoId: String): EmptyResult<DataError.Local> {
        writeFailure?.let { return Result.Failure(it) }
        removed += videoId
        return Result.Success(Unit)
    }

    override suspend fun clear(): EmptyResult<DataError.Local> {
        writeFailure?.let { return Result.Failure(it) }
        clears++
        return Result.Success(Unit)
    }
}
```

- [ ] **Step 2: Write the failing test**

`PlayerHistoryTest.kt`:

```kotlin
package com.shafayatb.streamly.player

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository.Record
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerHistoryTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-03T00:00:00Z")
    }

    private val video = testVideo(id = "v1", duration = 10.minutes)
    private val next = testVideo(id = "v2", duration = 5.minutes)
    private val live = testVideo(id = "live", duration = null)
    private val repository = FakeVideoRepository(videos = listOf(video, next, live))
    private val player = FakeVideoPlayer()
    private val history = FakeWatchHistoryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(videoId: String = "v1") = PlayerViewModel(
        videoId = videoId,
        videoRepository = repository,
        videoPlayer = player,
        downloadRepository = FakeDownloadRepository().apply { emit() },
        watchHistory = history,
        clock = clock,
    )

    private fun storedViewModel(store: ViewModelStore, videoId: String = "v1"): PlayerViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(videoId) } })
            .get(PlayerViewModel::class)

    private fun positions(videoId: String = "v1") = history.records.filter { it.videoId == videoId }.map { it.position }

    @Test
    fun resumesFromTheSavedPosition() {
        history.resumePositions["v1"] = 3.minutes

        val viewModel = viewModel()

        assertEquals(listOf(3.minutes), player.loadedStartPositions)
        assertEquals("3:00", viewModel.state.value.playback.positionText)
    }

    @Test
    fun startsFromTheBeginningWithoutHistory() {
        viewModel()

        assertEquals(listOf(Duration.ZERO), player.loadedStartPositions)
    }

    @Test
    fun waitsForTheSavedPositionBeforeLoading() {
        val gate = CompletableDeferred<Unit>()
        history.resumeGate = gate
        history.resumePositions["v1"] = 3.minutes

        viewModel()
        assertTrue(player.loadedVideoIds.isEmpty())

        gate.complete(Unit)
        assertEquals(listOf(3.minutes), player.loadedStartPositions)
    }

    @Test
    fun leavingBeforeTheSavedPositionArrivesLoadsNothing() {
        val gate = CompletableDeferred<Unit>()
        history.resumeGate = gate
        val store = ViewModelStore()
        storedViewModel(store)

        store.clear()
        gate.complete(Unit)

        assertTrue(player.loadedVideoIds.isEmpty())
        assertNull(player.state.value.videoId)
    }

    @Test
    fun aLiveVideoAlwaysStartsAtTheLiveEdge() {
        history.resumePositions["live"] = 3.minutes

        viewModel("live")

        assertEquals(listOf(Duration.ZERO), player.loadedStartPositions)
    }

    @Test
    fun recordsOnlyOncePlaybackStarts() {
        player.loadStatus = PlaybackStatus.BUFFERING
        viewModel()
        assertTrue(history.records.isEmpty())

        player.report { it.copy(status = PlaybackStatus.READY) }

        assertEquals(listOf(Record("v1", Duration.ZERO, 10.minutes)), history.records)
    }

    @Test
    fun aVideoThatNeverPlaysIsNotRecordedEvenOnLeaving() {
        player.loadStatus = PlaybackStatus.BUFFERING
        val store = ViewModelStore()
        val viewModel = storedViewModel(store)

        player.report { it.copy(status = PlaybackStatus.IDLE) }
        viewModel.onIntent(PlayerIntent.ScreenHidden)
        store.clear()

        assertTrue(history.records.isEmpty())
    }

    @Test
    fun checkpointsEveryTenSecondsOfPlayback() {
        viewModel()

        listOf(5.seconds, 10.seconds, 15.seconds, 20.seconds).forEach { position ->
            player.report { it.copy(position = position) }
        }

        assertEquals(listOf(Duration.ZERO, 10.seconds, 20.seconds), positions())
    }

    @Test
    fun savesOnPause() {
        val viewModel = viewModel()
        player.report { it.copy(position = 7.seconds) }

        viewModel.onIntent(PlayerIntent.TogglePlayPause)

        assertEquals(listOf(Duration.ZERO, 7.seconds), positions())
    }

    @Test
    fun savesWhenTheScreenIsHidden() {
        val viewModel = viewModel()
        player.report { it.copy(position = 4.seconds) }

        viewModel.onIntent(PlayerIntent.ScreenHidden)

        // The pause and the hide save the same position once.
        assertEquals(listOf(Duration.ZERO, 4.seconds), positions())
    }

    @Test
    fun savesTheFullLengthWhenTheVideoEnds() {
        viewModel()

        player.report { it.copy(status = PlaybackStatus.ENDED, position = 10.minutes) }

        assertEquals(10.minutes, positions().last())
    }

    @Test
    fun savesBeforeStoppingOnBack() {
        val store = ViewModelStore()
        val viewModel = storedViewModel(store)
        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        viewModel.onIntent(PlayerIntent.SeekTo(2.minutes + 5.seconds))

        store.clear()

        assertEquals(2.minutes + 5.seconds, positions().last())
        assertNull(player.state.value.videoId)
    }

    @Test
    fun savesBeforeStoppingForUpNext() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        viewModel.onIntent(PlayerIntent.SeekTo(90.seconds))

        viewModel.onIntent(PlayerIntent.SelectUpNext("v2"))

        assertEquals(90.seconds, positions().last())
        assertNull(player.state.value.videoId)
    }

    @Test
    fun recordsThePlayersDuration() {
        player.loadStatus = PlaybackStatus.BUFFERING
        viewModel()

        player.report { it.copy(status = PlaybackStatus.READY, duration = 10.minutes + 3.seconds) }

        assertEquals(listOf(Record("v1", Duration.ZERO, 10.minutes + 3.seconds)), history.records)
    }

    @Test
    fun liveIsRecordedOnceWithoutCheckpoints() {
        viewModel("live")

        player.report { it.copy(position = 30.seconds) }
        player.report { it.copy(position = 60.seconds) }

        assertEquals(listOf(Record("live", Duration.ZERO, null)), history.records)
    }

    @Test
    fun aReplacedScreenRecordsNothing() {
        val store = ViewModelStore()
        val stale = storedViewModel(store, "v1")
        viewModel("v2")
        val staleRecords = positions("v1")

        player.report { it.copy(position = 40.seconds) }
        stale.onIntent(PlayerIntent.ScreenHidden)
        store.clear()

        assertEquals(staleRecords, positions("v1"))
        assertEquals("v2", player.state.value.videoId)
    }
}
```

- [ ] **Step 3: Update `FakeVideoPlayer`** so the test can compile and express start positions and buffering

Replace its `load` and add the two members:

```kotlin
    /** Every start position passed to [load], in order. */
    val loadedStartPositions = mutableListOf<Duration>()

    /** The status a video reports right after [load]; `BUFFERING` models a stream still filling its buffer. */
    var loadStatus = PlaybackStatus.READY

    override fun load(video: Video, playWhenReady: Boolean, startPosition: Duration) {
        loadedVideoIds += video.id
        loadedStartPositions += startPosition
        _state.update {
            PlaybackState(
                videoId = video.id,
                status = loadStatus,
                playWhenReady = playWhenReady,
                position = startPosition,
                duration = video.duration,
                isLive = video.isLive,
                isMuted = it.isMuted,
            )
        }
    }
```

Pass `watchHistory = FakeWatchHistoryRepository()` in the `viewModel(...)` helpers of
`PlayerViewModelTest` and `PlayerDownloadTest`.

- [ ] **Step 4: Run it to verify it fails**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "*PlayerHistoryTest"`
Expected: FAIL, compilation errors: `'load' overrides nothing` (no `startPosition` yet) and
`No parameter with name 'watchHistory'`.

- [ ] **Step 5: Add the start position to the contract** (`VideoPlayer.kt`)

```kotlin
    /**
     * Replaces whatever is loaded with [video], starting at [startPosition] (the live edge for live
     * streams, which ignore it). Playback begins as soon as enough is buffered if [playWhenReady]
     * is true.
     */
    public fun load(video: Video, playWhenReady: Boolean, startPosition: Duration = Duration.ZERO)
```

- [ ] **Step 6: Start there in `ExoVideoPlayer`**

```kotlin
    override fun load(video: Video, playWhenReady: Boolean, startPosition: Duration) {
        loadedVideoIsLive = video.isLive
        // A finished download plays the rendition it saved: its stream keys keep the player from
        // choosing a variant that is not on the device, which offline would fail.
        val mediaItem = offlineMediaItems.completedMediaItem(video.id) ?: MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.hlsUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        exoPlayer.run {
            // Starting at the position avoids drawing 0:00 first. Live keeps the default position,
            // which is its live edge; any explicit one would start behind it.
            if (startPosition > Duration.ZERO && !video.isLive) {
                setMediaItem(mediaItem, startPosition.inWholeMilliseconds)
            } else {
                setMediaItem(mediaItem)
            }
            this.playWhenReady = playWhenReady
            prepare()
        }
        publishState()
    }
```

- [ ] **Step 7: Resume and record in `PlayerViewModel`**

Add the constructor parameter `private val watchHistory: WatchHistoryRepository` after
`downloadRepository`, and update the class KDoc with one sentence: "It also resumes the video
from the account's watch history and records how far it got."

New fields (next to `resumeWhenShown`):

```kotlin
    // Set once this screen's video has actually played: only then does it belong in the history.
    private var hasPlayed = false
    private var lastRecordedPosition: Duration? = null
    private var wasPlayWhenReady = false
```

In `init`, before `loadDetails()`:

```kotlin
        viewModelScope.launch { videoPlayer.state.collect(::trackHistory) }
```

Change `onCleared`, `selectUpNext`, `onScreenHidden`, `startPlayback`, and the `onSuccess` call:

```kotlin
    override fun onCleared() {
        // The screen is gone for good (Back, or replaced). The player stays for the next screen.
        // Saved first: stopping unloads the video and its position.
        recordProgress()
        if (ownsPlayer) videoPlayer.stop()
    }

    private fun selectUpNext(nextVideoId: String) {
        recordProgress()
        // Silence this video now rather than when the replaced screen finishes leaving.
        if (ownsPlayer) videoPlayer.stop()
        send(PlayerEvent.NavigateToVideo(nextVideoId))
    }

    private fun onScreenHidden() {
        isScreenVisible = false
        val playback = videoPlayer.state.value
        // An ended video keeps playWhenReady; resuming it would restart it from the beginning.
        if (ownsPlayer && playback.playWhenReady && !playback.isEnded) {
            videoPlayer.pause()
            resumeWhenShown = true
        }
        recordProgress()
    }

    private suspend fun startPlayback(video: Video) {
        if (ownsPlayer) return
        val start = if (video.isLive) Duration.ZERO else watchHistory.resumePosition(video.id)
        // A video that finishes loading while the app is in the background waits to be seen.
        videoPlayer.load(video, playWhenReady = isScreenVisible, startPosition = start)
        resumeWhenShown = !isScreenVisible
    }
```

(`loadDetails` already calls `startPlayback(video)` inside `onSuccess` within
`viewModelScope.launch`; `onSuccess` is `inline`, so the suspend call compiles unchanged. A
cleared ViewModel cancels the read, so nothing loads after Back: Review Focus 2.)

The tracking itself:

```kotlin
    /** Records the moments history needs: the first play, a pause, the end, and a checkpoint while playing. */
    private fun trackHistory(playback: PlaybackState) {
        if (playback.videoId != videoId) return
        val paused = wasPlayWhenReady && !playback.playWhenReady
        wasPlayWhenReady = playback.playWhenReady
        when {
            !hasPlayed -> if (playback.isPlaying) {
                hasPlayed = true
                recordProgress(playback)
            }
            paused || playback.isEnded -> recordProgress(playback)
            playback.isPlaying && isCheckpointDue(playback.position) -> recordProgress(playback)
        }
    }

    private fun isCheckpointDue(position: Duration): Boolean {
        val last = lastRecordedPosition ?: return true
        return (position - last).absoluteValue >= HISTORY_CHECKPOINT
    }

    /** Saves where this screen's video is, once it has played and only if the position moved. */
    private fun recordProgress(playback: PlaybackState = videoPlayer.state.value) {
        val video = loadedVideo ?: return
        if (!hasPlayed || playback.videoId != videoId) return
        val position = if (video.isLive) Duration.ZERO else playback.position
        if (position == lastRecordedPosition) return
        lastRecordedPosition = position
        watchHistory.record(video, position, playback.duration)
    }
```

And a file-level constant below the class:

```kotlin
// How often a playing video's position is saved, so a crash or process death loses little.
private val HISTORY_CHECKPOINT = 10.seconds
```

Imports to add: `com.shafayatb.streamly.domain.history.WatchHistoryRepository`,
`kotlin.time.Duration`, `kotlin.time.Duration.Companion.seconds`.

- [ ] **Step 8: Wire Koin** (`PresentationModule.kt`, the `PlayerViewModel` factory)

```kotlin
            downloadRepository = get(),
            watchHistory = get(),
            clock = get(),
```

- [ ] **Step 9: Run the player tests**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "*Player*"`
Expected: PASS, the existing `PlayerViewModelTest` and `PlayerDownloadTest` plus 16 new
`PlayerHistoryTest` tests.

- [ ] **Step 10: Build every module that implements or calls `load`**

Run: `rtk proxy ./gradlew :core:media:testAndroidHostTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (27 media tests pass).

- [ ] **Step 11: Stage for commit 4** (`feat(player): resume videos and record watch history`). Do not commit.

---

### Task 5: Watch history ViewModel (`:shared`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/history/WatchHistoryState.kt`
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/history/WatchHistoryIntent.kt`
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/history/WatchHistoryEvent.kt`
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/history/WatchHistoryViewModel.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Test: `shared/src/commonTest/kotlin/com/shafayatb/streamly/history/WatchHistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `WatchHistoryRepository`, `WatchHistoryEntry` (Task 2); `FakeWatchHistoryRepository` (Task 4); `formatAge`, `formatDuration`, `UiText` (existing).
- Produces: `WatchHistoryState`, `WatchHistoryContent`, `HistoryItemUi`, `WatchHistoryIntent`, `WatchHistoryEvent`, `WatchHistoryViewModel(watchHistory, clock)`; strings `history_*`.

- [ ] **Step 1: Add the strings** (after the `profile_*` block; delete `profile_history_soon` in Task 6, together with its last use)

```xml
    <string name="history_title">Watch history</string>
    <string name="history_clear_all">Clear all</string>
    <string name="history_loading">Loading your watch history</string>
    <string name="history_empty_title">No watch history yet</string>
    <string name="history_empty_body">Videos you watch will show up here.</string>
    <string name="history_error_title">Couldn’t load your history</string>
    <string name="history_error_body">Something went wrong reading your watch history. Please try again.</string>
    <string name="history_update_failed">Couldn’t update your watch history. Please try again.</string>
    <string name="history_clear_title">Clear watch history?</string>
    <string name="history_clear_body">Every video will be removed from this account’s history and will start from the beginning next time.</string>
    <string name="history_clear_confirm">Clear</string>
    <string name="history_clear_cancel">Cancel</string>
    <string name="cd_remove_from_history">Remove from watch history</string>
```

- [ ] **Step 2: Write the MVI types**

`WatchHistoryState.kt`:

```kotlin
package com.shafayatb.streamly.history

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class WatchHistoryState(
    val content: WatchHistoryContent = WatchHistoryContent.Loading,
    val isClearDialogShown: Boolean = false,
) {
    val canClear: Boolean get() = content is WatchHistoryContent.Loaded
}

@Immutable
sealed interface WatchHistoryContent {
    data object Loading : WatchHistoryContent
    data object Empty : WatchHistoryContent
    data class Loaded(val items: ImmutableList<HistoryItemUi>) : WatchHistoryContent
    data class Error(val message: UiText) : WatchHistoryContent
}

@Immutable
data class HistoryItemUi(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** "10:34", or `null` for a live stream. */
    val durationText: String?,
    val isLive: Boolean,
    /** 0–1 for the bar on the thumbnail; `null` for live streams. */
    val progress: Float?,
    /** When it was last watched: "2 hours ago". */
    val watched: UiText,
)
```

`WatchHistoryIntent.kt`:

```kotlin
package com.shafayatb.streamly.history

sealed interface WatchHistoryIntent {
    data class Open(val videoId: String) : WatchHistoryIntent
    data class Remove(val videoId: String) : WatchHistoryIntent
    data object RequestClear : WatchHistoryIntent
    data object ConfirmClear : WatchHistoryIntent
    data object DismissClear : WatchHistoryIntent

    /** Reads the history again after it failed to load. */
    data object RetryLoad : WatchHistoryIntent
    data object NavigateBack : WatchHistoryIntent
}
```

`WatchHistoryEvent.kt`:

```kotlin
package com.shafayatb.streamly.history

import com.shafayatb.streamly.core.presentation.UiText

sealed interface WatchHistoryEvent {
    data class NavigateToPlayer(val videoId: String) : WatchHistoryEvent
    data object NavigateBack : WatchHistoryEvent
    data class ShowMessage(val message: UiText) : WatchHistoryEvent
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.shafayatb.streamly.history

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.history_error_body
import streamly.shared.generated.resources.history_update_failed

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryViewModelTest {

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val history = FakeWatchHistoryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WatchHistoryViewModel(history, clock)

    private fun entry(id: String, position: Duration, duration: Duration?, watchedAgo: Duration) = WatchHistoryEntry(
        videoId = id,
        title = "Video $id",
        channelName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        duration = duration,
        position = position,
        watchedAt = now - watchedAgo,
    )

    private fun show(vararg entries: WatchHistoryEntry) {
        history.result.value = Result.Success(entries.toList())
    }

    @Test
    fun showsLoadingUntilTheHistoryArrives() {
        history.result.value = null

        assertEquals(WatchHistoryContent.Loading, viewModel().state.value.content)
    }

    @Test
    fun listsEntriesWithLengthProgressAndWhenTheyWereWatched() {
        show(
            entry("a", position = 3.minutes, duration = 10.minutes, watchedAgo = 2.hours),
            entry("live", position = Duration.ZERO, duration = null, watchedAgo = 5.minutes),
        )

        val expected = WatchHistoryContent.Loaded(
            persistentListOf(
                HistoryItemUi("a", "Video a", "Channel", "https://example.com/a.jpg", "10:00", false, 0.3f, formatAge(now - 2.hours, now)),
                HistoryItemUi("live", "Video live", "Channel", "https://example.com/live.jpg", null, true, null, formatAge(now - 5.minutes, now)),
            ),
        )
        val state = viewModel().state.value
        assertEquals(expected, state.content)
        assertTrue(state.canClear)
    }

    @Test
    fun aFinishedVideoShowsAFullBar() {
        show(entry("a", position = 10.minutes, duration = 10.minutes, watchedAgo = 1.hours))

        val item = (viewModel().state.value.content as WatchHistoryContent.Loaded).items.single()
        assertEquals(1f, item.progress)
    }

    @Test
    fun anEmptyHistoryHasNothingToClear() {
        val state = viewModel().state.value

        assertEquals(WatchHistoryContent.Empty, state.content)
        assertFalse(state.canClear)
    }

    @Test
    fun aReadFailureShowsTheErrorAndRetryReadsAgain() {
        history.failNextRead = DataError.Local.UNKNOWN
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()
        assertEquals(WatchHistoryContent.Error(UiText.Resource(Res.string.history_error_body)), viewModel.state.value.content)

        viewModel.onIntent(WatchHistoryIntent.RetryLoad)

        assertTrue(viewModel.state.value.content is WatchHistoryContent.Loaded)
    }

    @Test
    fun openingARowPlaysIt() = runTest {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.Open("a"))
            assertEquals(WatchHistoryEvent.NavigateToPlayer("a"), awaitItem())
        }
    }

    @Test
    fun removingARowRemovesItFromTheHistory() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))

        viewModel().onIntent(WatchHistoryIntent.Remove("a"))

        assertEquals(listOf("a"), history.removed)
    }

    @Test
    fun aFailedRemoveShowsAMessage() = runTest {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        history.writeFailure = DataError.Local.UNKNOWN
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.Remove("a"))
            assertEquals(WatchHistoryEvent.ShowMessage(UiText.Resource(Res.string.history_update_failed)), awaitItem())
        }
    }

    @Test
    fun clearAllAsksFirst() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()

        viewModel.onIntent(WatchHistoryIntent.RequestClear)
        assertTrue(viewModel.state.value.isClearDialogShown)
        viewModel.onIntent(WatchHistoryIntent.DismissClear)

        assertFalse(viewModel.state.value.isClearDialogShown)
        assertEquals(0, history.clears)
    }

    @Test
    fun confirmingClearsTheHistory() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()

        viewModel.onIntent(WatchHistoryIntent.RequestClear)
        viewModel.onIntent(WatchHistoryIntent.ConfirmClear)

        assertEquals(1, history.clears)
        assertFalse(viewModel.state.value.isClearDialogShown)
    }

    @Test
    fun aFailedClearShowsAMessage() = runTest {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        history.writeFailure = DataError.Local.DISK_FULL
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.RequestClear)
            viewModel.onIntent(WatchHistoryIntent.ConfirmClear)
            assertEquals(WatchHistoryEvent.ShowMessage(UiText.Resource(Res.string.history_update_failed)), awaitItem())
        }
    }

    @Test
    fun theClearDialogClosesWhenTheHistoryEmpties() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()
        viewModel.onIntent(WatchHistoryIntent.RequestClear)

        show()

        assertFalse(viewModel.state.value.isClearDialogShown)
    }

    @Test
    fun backLeavesTheScreen() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.NavigateBack)
            assertEquals(WatchHistoryEvent.NavigateBack, awaitItem())
        }
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "*WatchHistoryViewModelTest"`
Expected: FAIL, compilation error `Unresolved reference 'WatchHistoryViewModel'`.

- [ ] **Step 5: Write the ViewModel**

```kotlin
package com.shafayatb.streamly.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.history.WatchHistoryRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.util.onFailure
import kotlin.time.Clock
import kotlin.time.Instant
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
import streamly.shared.generated.resources.history_error_body
import streamly.shared.generated.resources.history_update_failed

class WatchHistoryViewModel(
    private val watchHistory: WatchHistoryRepository,
    private val clock: Clock,
) : ViewModel() {

    /** `null` while loading. */
    private val history = MutableStateFlow<Result<List<WatchHistoryEntry>, DataError.Local>?>(null)
    private val isClearDialogRequested = MutableStateFlow(false)

    val state: StateFlow<WatchHistoryState> = combine(history, isClearDialogRequested) { history, clearRequested ->
        val content = when (history) {
            null -> WatchHistoryContent.Loading
            is Result.Failure -> WatchHistoryContent.Error(UiText.Resource(Res.string.history_error_body))
            is Result.Success -> if (history.data.isEmpty()) {
                WatchHistoryContent.Empty
            } else {
                val now = clock.now()
                WatchHistoryContent.Loaded(history.data.map { it.toItemUi(now) }.toImmutableList())
            }
        }
        // A history that empties under the dialog closes it rather than offering to clear nothing.
        WatchHistoryState(content = content, isClearDialogShown = clearRequested && content is WatchHistoryContent.Loaded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WatchHistoryState())

    private val _events = Channel<WatchHistoryEvent>()
    val events: Flow<WatchHistoryEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: WatchHistoryIntent) {
        when (intent) {
            is WatchHistoryIntent.Open -> send(WatchHistoryEvent.NavigateToPlayer(intent.videoId))
            is WatchHistoryIntent.Remove -> update { watchHistory.remove(intent.videoId) }
            WatchHistoryIntent.RequestClear -> isClearDialogRequested.value = true
            WatchHistoryIntent.ConfirmClear -> {
                isClearDialogRequested.value = false
                update { watchHistory.clear() }
            }
            WatchHistoryIntent.DismissClear -> isClearDialogRequested.value = false
            WatchHistoryIntent.RetryLoad -> load()
            WatchHistoryIntent.NavigateBack -> send(WatchHistoryEvent.NavigateBack)
        }
    }

    private fun load() {
        loadJob?.cancel()
        history.value = null
        loadJob = viewModelScope.launch {
            watchHistory.entries.collect { history.value = it }
        }
    }

    private fun update(write: suspend () -> EmptyResult<DataError.Local>) {
        viewModelScope.launch {
            write().onFailure { _events.send(WatchHistoryEvent.ShowMessage(UiText.Resource(Res.string.history_update_failed))) }
        }
    }

    private fun send(event: WatchHistoryEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

private fun WatchHistoryEntry.toItemUi(now: Instant): HistoryItemUi = HistoryItemUi(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationText = duration?.let(::formatDuration),
    isLive = isLive,
    progress = progress,
    watched = formatAge(watchedAt, now),
)
```

(`onFailure` is `inline`, so `_events.send` compiles inside it.)

- [ ] **Step 6: Run it to verify it passes**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "*WatchHistoryViewModelTest"`
Expected: PASS, 13 tests.

---

### Task 6: The screen, navigation, and Profile (`:shared`, `:core:designsystem`)

**Files:**
- Create: `core/designsystem/src/commonMain/kotlin/com/shafayatb/streamly/core/designsystem/components/WatchProgressBar.kt`
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/history/WatchHistoryScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/history/ClearHistoryDialog.kt`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/navigation/Route.kt`, `AppNavigation.kt`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/profile/ProfileEvent.kt`, `ProfileViewModel.kt`, `ProfileScreen.kt` (`ProfileRoot`)
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/di/PresentationModule.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml` (delete `profile_history_soon`)
- Modify: `shared/src/commonTest/kotlin/com/shafayatb/streamly/profile/ProfileViewModelTest.kt:160-171`
- Test: `shared/src/androidDeviceTest/kotlin/com/shafayatb/streamly/history/WatchHistoryScreenTest.kt`

**Interfaces:**
- Consumes: Task 5's types; `FeedLoading`, `FeedMessage`, `feedGridColumns`, `VideoThumbnail`,
  `DurationBadge`, `LiveBadge`, `BrandBackground`, `ObserveAsEvents`, `asString`, `resolve` (existing).
- Produces: `WatchHistoryRoot(onNavigateBack, onNavigateToPlayer)`, `WatchHistoryScreen(state, onIntent, snackbarHostState)`, `Route.WatchHistory`, `ProfileEvent.NavigateToHistory`.

- [ ] **Step 1: Write the failing Profile test** (replace `historyAndSettingsAreComingSoon`)

```kotlin
    @Test
    fun watchHistoryOpensTheHistory() = runTest {
        val viewModel = ProfileViewModel(FakeSessionRepository(anika))

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.OpenHistory)
            assertEquals(ProfileEvent.NavigateToHistory, awaitItem())
        }
    }

    @Test
    fun settingsAreComingSoon() = runTest {
        val viewModel = ProfileViewModel(FakeSessionRepository(anika))

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.OpenSettings)
            assertEquals(ProfileEvent.ShowMessage(UiText.Resource(Res.string.profile_settings_soon)), awaitItem())
        }
    }
```

Remove the now-unused `profile_history_soon` import from the test.

- [ ] **Step 2: Write the failing device test**

```kotlin
package com.shafayatb.streamly.history

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WatchHistoryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val intents = mutableListOf<WatchHistoryIntent>()
    private val item = HistoryItemUi(
        videoId = "a",
        title = "Media3 in 10 minutes",
        channelName = "CodeLabs",
        thumbnailUrl = "",
        durationText = "10:00",
        isLive = false,
        progress = 0.3f,
        watched = UiText.DynamicString("2 hours ago"),
    )
    private val loaded = WatchHistoryState(content = WatchHistoryContent.Loaded(persistentListOf(item)))

    private fun show(state: WatchHistoryState) {
        rule.setContent {
            StreamlyTheme { WatchHistoryScreen(state = state, onIntent = { intents += it }) }
        }
    }

    private fun dialogButton(label: String) =
        rule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test
    fun anEmptyHistoryExplainsItselfAndOffersNoClear() {
        show(WatchHistoryState(content = WatchHistoryContent.Empty))

        rule.onNodeWithText("No watch history yet").assertExists()
        rule.onNodeWithText("Clear all").assertDoesNotExist()
    }

    @Test
    fun tappingARowOpensIt() {
        show(loaded)

        rule.onNodeWithText("Media3 in 10 minutes").performClick()

        assertEquals(listOf<WatchHistoryIntent>(WatchHistoryIntent.Open("a")), intents)
    }

    @Test
    fun theRemoveButtonRemovesThatVideo() {
        show(loaded)

        rule.onNodeWithContentDescription("Remove from watch history").performClick()

        assertEquals(listOf<WatchHistoryIntent>(WatchHistoryIntent.Remove("a")), intents)
    }

    @Test
    fun clearAllAsksAndConfirmingClears() {
        show(loaded.copy(isClearDialogShown = true))

        rule.onNodeWithText("Clear watch history?").assertExists()
        dialogButton("Clear").performClick()

        assertEquals(listOf<WatchHistoryIntent>(WatchHistoryIntent.ConfirmClear), intents)
    }
}
```

- [ ] **Step 3: Run both to verify they fail**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "*ProfileViewModelTest"`
Expected: FAIL, compilation error `Unresolved reference 'NavigateToHistory'`.
(The device test cannot compile until `WatchHistoryScreen` exists; it runs in Step 10.)

- [ ] **Step 4: Profile navigates**

`ProfileEvent.kt`: add `data object NavigateToHistory : ProfileEvent`.
`ProfileViewModel.kt`: `ProfileIntent.OpenHistory -> send(ProfileEvent.NavigateToHistory)` and
drop the `profile_history_soon` import. `ProfileRoot` gains `onNavigateToHistory: () -> Unit`
(after `onNavigateToDownloads`) and handles `ProfileEvent.NavigateToHistory -> onNavigateToHistory()`.
Delete `<string name="profile_history_soon">…</string>` from `strings.xml`.

- [ ] **Step 5: The progress bar** (`WatchProgressBar.kt`)

```kotlin
package com.shafayatb.streamly.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shafayatb.streamly.core.designsystem.theme.StreamlyPalette

/**
 * How much of a video has been watched ([progress], 0–1), drawn along the bottom edge of a
 * [VideoThumbnail]. Fixed colors, like the badges, because the image under it is unknown.
 */
@Composable
public fun WatchProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.4f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(StreamlyPalette.Coral500),
        )
    }
}
```

- [ ] **Step 6: The clear dialog** (`ClearHistoryDialog.kt`)

```kotlin
package com.shafayatb.streamly.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.history_clear_body
import streamly.shared.generated.resources.history_clear_cancel
import streamly.shared.generated.resources.history_clear_confirm
import streamly.shared.generated.resources.history_clear_title

/** Clearing drops every entry and resume point for the account, so it is confirmed first. */
@Composable
fun ClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.history_clear_title)) },
        text = { Text(stringResource(Res.string.history_clear_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.history_clear_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.history_clear_cancel)) }
        },
    )
}
```

- [ ] **Step 7: The screen** (`WatchHistoryScreen.kt`)

```kotlin
package com.shafayatb.streamly.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.components.DurationBadge
import com.shafayatb.streamly.core.designsystem.components.LiveBadge
import com.shafayatb.streamly.core.designsystem.components.VideoThumbnail
import com.shafayatb.streamly.core.designsystem.components.WatchProgressBar
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.feedGridColumns
import com.shafayatb.streamly.core.presentation.resolve
import com.shafayatb.streamly.home.FeedLoading
import com.shafayatb.streamly.home.FeedMessage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.cd_navigate_back
import streamly.shared.generated.resources.cd_remove_from_history
import streamly.shared.generated.resources.history_clear_all
import streamly.shared.generated.resources.history_empty_body
import streamly.shared.generated.resources.history_empty_title
import streamly.shared.generated.resources.history_error_title
import streamly.shared.generated.resources.history_loading
import streamly.shared.generated.resources.history_title
import streamly.shared.generated.resources.ic_arrow_back
import streamly.shared.generated.resources.ic_close
import streamly.shared.generated.resources.ic_cloud_off
import streamly.shared.generated.resources.ic_history
import streamly.shared.generated.resources.player_metadata
import streamly.shared.generated.resources.video_live

@Composable
fun WatchHistoryRoot(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (videoId: String) -> Unit,
    viewModel: WatchHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WatchHistoryEvent.NavigateBack -> onNavigateBack()
            is WatchHistoryEvent.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
            is WatchHistoryEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    WatchHistoryScreen(state = state, onIntent = viewModel::onIntent, snackbarHostState = snackbarHostState)
}

/** A pushed destination with no tab bar, so it keeps the system navigation bar clear itself. */
@Composable
fun WatchHistoryScreen(
    state: WatchHistoryState,
    onIntent: (WatchHistoryIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val contentModifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navigationBar)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HistoryHeader(
                canClear = state.canClear,
                onBack = { onIntent(WatchHistoryIntent.NavigateBack) },
                onClear = { onIntent(WatchHistoryIntent.RequestClear) },
            )
            when (val content = state.content) {
                WatchHistoryContent.Loading -> FeedLoading(
                    columns = feedGridColumns(),
                    contentPadding = contentPadding,
                    contentDescription = stringResource(Res.string.history_loading),
                    modifier = contentModifier,
                )
                WatchHistoryContent.Empty -> FeedMessage(
                    icon = Res.drawable.ic_history,
                    title = stringResource(Res.string.history_empty_title),
                    message = stringResource(Res.string.history_empty_body),
                    modifier = contentModifier,
                )
                is WatchHistoryContent.Error -> FeedMessage(
                    icon = Res.drawable.ic_cloud_off,
                    title = stringResource(Res.string.history_error_title),
                    message = content.message.asString(),
                    actionLabel = stringResource(Res.string.action_retry),
                    onAction = { onIntent(WatchHistoryIntent.RetryLoad) },
                    modifier = contentModifier,
                )
                is WatchHistoryContent.Loaded -> LazyVerticalGrid(
                    // The feed's window-size columns: one on phones, two on medium, three on expanded.
                    columns = GridCells.Fixed(feedGridColumns()),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = contentModifier,
                ) {
                    items(items = content.items, key = { it.videoId }, contentType = { "history" }) { item ->
                        HistoryRow(item = item, onIntent = onIntent)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (state.isClearDialogShown) {
        ClearHistoryDialog(
            onConfirm = { onIntent(WatchHistoryIntent.ConfirmClear) },
            onDismiss = { onIntent(WatchHistoryIntent.DismissClear) },
        )
    }
}

@Composable
private fun HistoryHeader(canClear: Boolean, onBack: () -> Unit, onClear: () -> Unit) {
    BrandBackground(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = stringResource(Res.string.cd_navigate_back),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(Res.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (canClear) {
                TextButton(onClick = onClear) {
                    Text(stringResource(Res.string.history_clear_all), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItemUi, onIntent: (WatchHistoryIntent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onIntent(WatchHistoryIntent.Open(item.videoId)) }
            .padding(vertical = 4.dp),
    ) {
        VideoThumbnail(url = item.thumbnailUrl, contentDescription = null, modifier = Modifier.width(120.dp)) {
            // The badge sits above the progress bar so the bar never hides it.
            val badgeModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = if (item.progress != null) 10.dp else 6.dp)
            if (item.isLive) {
                LiveBadge(text = stringResource(Res.string.video_live), modifier = badgeModifier)
            } else {
                item.durationText?.let { DurationBadge(text = it, modifier = badgeModifier) }
            }
            item.progress?.let { WatchProgressBar(progress = it, modifier = Modifier.align(Alignment.BottomStart)) }
        }
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
                text = stringResource(Res.string.player_metadata, item.channelName, item.watched.asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onIntent(WatchHistoryIntent.Remove(item.videoId)) }) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.cd_remove_from_history),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PreviewItems = persistentListOf(
    HistoryItemUi("media3", "Media3 in 10 minutes", "CodeLabs", "", "10:00", false, 0.3f, UiText.DynamicString("2 hours ago")),
    HistoryItemUi("live", "Late-night synthwave session", "Neon Hours", "", null, true, null, UiText.DynamicString("Yesterday")),
    HistoryItemUi("done", "Festival highlights", "Crowd Pulse", "", "4:12", false, 1f, UiText.DynamicString("3 days ago")),
)

@Preview
@Composable
private fun WatchHistoryScreenPreview() {
    StreamlyTheme {
        WatchHistoryScreen(state = WatchHistoryState(content = WatchHistoryContent.Loaded(PreviewItems)), onIntent = {})
    }
}

@Preview
@Composable
private fun WatchHistoryEmptyPreview() {
    StreamlyTheme {
        WatchHistoryScreen(state = WatchHistoryState(content = WatchHistoryContent.Empty), onIntent = {})
    }
}
```

- [ ] **Step 8: Navigation and DI**

`Route.kt`: add

```kotlin
    @Serializable
    data object WatchHistory : Route
```

`AppNavigation.kt`: register `subclass(Route.WatchHistory::class)` in `navigationConfiguration`;
in `entry<Route.Profile>` pass `onNavigateToHistory = { backStack.add(Route.WatchHistory) }`; add

```kotlin
                entry<Route.WatchHistory> {
                    WatchHistoryRoot(
                        onNavigateBack = { backStack.popIfNotRoot() },
                        onNavigateToPlayer = { videoId -> backStack.add(Route.Player(videoId)) },
                    )
                }
```

`TopLevelDestination.of(Route.WatchHistory)` is `null`, so `AppShell` hides the tab bar there
with no change.

`PresentationModule.kt`: `viewModelOf(::WatchHistoryViewModel)`.

- [ ] **Step 9: Run every host test and build**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL; shared 124 + 16 + 13 + 1 (Profile: one test replaced by two) = 154.

- [ ] **Step 10: Run the device tests** (emulator connected)

Run: `rtk proxy ./gradlew :shared:connectedAndroidDeviceTest`
Expected: 131 + 4 = 135 tests, 0 failures. Parse
`shared/build/outputs/androidTest-results/connected/` with Python `xml.etree`.

- [ ] **Step 11: Stage for commit 5** (`feat(history): add the watch history screen`). Do not commit.

---

### Task 7: Verify, review, and document

- [ ] **Step 1: Whole-project check**

Run: `rtk proxy ./gradlew check`
Expected: green. Lint 0 errors and no new warnings (4 version notices today).

- [ ] **Step 2: Device script on the emulator (`emulator-5554`, Anika) and the Galaxy A04
  (`R58T90FB67Y`, abc@gmail.com)**, recording each result. Measure tap coordinates from a
  screenshot first; the history row's Profile y is 987 on the emulator and 570 on the A04.

  1. **Empty state.** Profile → Watch history: "No watch history yet", no Clear all, no tab bar;
     Back returns to Profile.
  2. **Recording and resume.** Home → open Big Buck Bunny, let it play to about 1:00, Back.
     History lists it first with a bar near 10%, "Just now" / "1 minute ago". Tap it: the
     Player opens at about 1:00 and the first frame is not 0:00 (screen record, read frames).
     `adb logcat -s StreamlyPlayer` shows no seek after the load.
  3. **Live.** Open a live stream for 20 s, Back: it is listed with LIVE and no bar. Reopen it:
     it plays at the live edge (the LIVE badge state and position match a fresh open).
  4. **Finish.** Seek a video to its last 5 s, let it end, Back: a full bar. Reopen: starts at 0:00.
  5. **Background and process death (Review Focus 5).** Play to about 2:00, press Home,
     `adb shell am kill com.shafayatb.streamly` (no download service running), reopen from
     Recents: the restored Player resumes near 2:00.
  6. **Offline resume (Review Focus 5).** A completed download (Tears of Steel on the A04,
     Acoustic one-take on the emulator), play to about 1:30, Back; Wi-Fi and data off; reopen from
     History: `Playing download …` in `StreamlyDownloads` and playback from about 1:30. Re-enable
     the network.
  7. **Remove and clear.** X on a row removes it at once; Clear all → Cancel keeps everything;
     Clear all → Clear empties the list; the dialog survives rotation.
  8. **Accounts (Review Focus 4).** On the emulator: sign out, continue as guest: an empty
     history; play something; sign out, Google sign-in as Anika: Anika's list is back, without
     the guest's video, and Anika's resume point is unchanged. Inspect
     `adb shell run-as com.shafayatb.streamly cat files/datastore/watch_history.preferences_pb | strings`.
  9. **Adaptive and themes.** Phone landscape, a 700 dp medium window (two columns), a 2400x1800
     tablet (three columns), dark and light on the A04 at 360 dp (no clipped text in rows or
     header).
  10. **Regression.** Shorts: 2 `ExoPlayerImpl … Init` after six swipes, and no Shorts in
      History. A long-form video: one started AudioTrack. `Activities: 1` after Player → Back →
      History → six rotations (`am dumpheap` + `dumpsys meminfo`).

- [ ] **Step 3: Fresh review.** One reviewer subagent on the most capable model reviews the whole
  branch (`git diff develop...`) against this plan and the Review Focus list. Fix every critical
  and important finding with a test that fails first; list the minor ones in the handoff.

- [ ] **Step 4: Documents.**
  - README: replace "Watch history and Settings are stubs" with Settings only; add a
    "Watch history" section under Architecture (rules, per account, storage, the player hook,
    the screen); update the status paragraph and test counts; add known gaps if the review
    leaves any.
  - `docs/agent-log.md`: task 9 entry in the shape of tasks 6–8, linking this plan.
  - `FRESH_PROMPT.md`: next task `feature/settings`, with this task's evidence.

- [ ] **Step 5: Stage commit 6** (`docs: document watch history`), show `git status`, a
  `git diff --cached` summary, and all proposed messages, then stop for approval.

## Commit sequence (each after the user's approval)

1. `docs: plan watch history`: this file.
2. `feat(domain): add account-scoped watch history and resume rules`: Tasks 1–2.
3. `feat(data): store watch history per account in DataStore`: Task 3.
4. `feat(player): resume videos and record watch history`: Task 4 (contract, ExoVideoPlayer,
   PlayerViewModel, fakes, Koin).
5. `feat(history): add the watch history screen`: Tasks 5–6.
6. `docs: document watch history`: README, agent log, and handoff, with the commit hashes.

Each commit's tree is built and verified separately (temporary index, scratch worktree:
`./gradlew :androidApp:assembleDebug testAndroidHostTest`), as in tasks 6–8.
