# Streamly

Streamly is a minimal YouTube-style Android app with long-form HLS videos, vertical HLS shorts,
offline downloads, and a profile with sign-out. It is built with Kotlin Multiplatform and Compose
Multiplatform, with an Android target only.

> **Status:** all seven reference views are done: onboarding, the home feed, Shorts, the player,
> Downloads, Profile, and the sign-out confirmation. Returning users go straight to Home, which loads the video catalog with category
> chips, an adaptive grid, and loading, empty, and error states. Tapping a video plays it over HLS
> with Media3: play/pause, scrubbing, mute, a buffering indicator, a LIVE badge for live streams,
> playback errors with retry, and an up-next list. A bottom bar (a rail on wide windows) switches
> between Home, Shorts, Downloads, and Profile. Shorts is a full-screen vertical pager of HLS shorts that
> autoplays only the visible one from a pool of at most two players. The player's Download action
> saves a video with Media3's `DownloadManager`, with real progress in the player, the Downloads
> tab, and a notification; finished downloads play with the network off and can be removed.
> Downloads belong to the account that saved them: signing out hides them, and signing in again
> as the same account brings them back. Profile shows the account and signs out after a
> confirmation, returning to onboarding. Watch history lists what each account has watched, with
> its progress, and the player resumes a video where that account left off. The app has its own launcher icon, a branded splash
> that stays until the stored session is read, and the mockups' typeface, Baloo Da 2.

## Setup

**Requirements**

- Android Studio with the Android SDK for API 37 (`compileSdk`/`targetSdk` 37, `minSdk` 24).
- JDK 17 or newer to start Gradle. The Gradle daemon runs on JDK 21
  (`gradle/gradle-daemon-jvm.properties`); Gradle can provision it automatically.
- A device or emulator running Android 7.0 (API 24) or newer.

**Commands**

```bash
./gradlew :androidApp:assembleDebug     # build the debug APK
./gradlew :androidApp:installDebug      # install on a connected device or emulator
./gradlew check                         # lint and host tests for every module
./gradlew :shared:testAndroidHostTest   # host tests for one module (same task in each library)
./gradlew :shared:connectedAndroidDeviceTest   # Compose device tests (needs a device or emulator)
```

The debug APK is written to `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Architecture

The app follows clean architecture with MVVM + MVI presentation: each screen has one `ViewModel`
that exposes an immutable `UiState` as a `StateFlow` and accepts a sealed intent type.

### Module graph

```mermaid
graph TD
    app[":androidApp"] --> shared[":shared"]
    app --> data[":data"]
    app --> media[":core:media"]
    shared --> domain[":domain"]
    shared --> designsystem[":core:designsystem"]
    shared -. "androidMain only<br/>(video surfaces)" .-> media
    data --> domain
    media --> domain
```

| Module | Responsibility |
|---|---|
| `:androidApp` | Thin Android shell: `MainActivity`, `StreamlyApplication`, and the composition root that starts Koin. |
| `:shared` | Compose UI: navigation (Nav3), screen ViewModels, feature screens, and resources. |
| `:domain` | Pure Kotlin models, repository and player contracts, and use cases. Its only dependency is `kotlinx-coroutines-core`. |
| `:data` | Implements the network and session contracts: Ktor clients, DTOs and mappers, and DataStore session storage. |
| `:core:designsystem` | Theme and reusable Compose components. |
| `:core:media` | All Media3 code: the shared normal-video player, the Shorts player pool, `DownloadManager`, and the single download cache that offline playback also reads from. It exposes Compose video surfaces to `:shared/androidMain`. |

Dependency rules:

- `:domain` depends on no other module and has no Android, Compose, Ktor, DataStore, Media3, or
  Koin imports.
- Media3 types stay inside `:core:media`. Common UI works with domain state and identifiers, never
  with a Media3 `Player`.
- `:core:media` owns both playback and downloads, so playback and `DownloadManager` share one
  cache. Separate caches would break offline playback.
- Library modules use Kotlin explicit API mode, so every public declaration is a deliberate choice.

### Normal-video playback

One `ExoPlayer` plays every long-form video. The domain declares a framework-free contract,
`VideoPlayer` (`load`, `play`, `pause`, `seekTo`, `setMuted`, `retry`, `stop`), which exposes a
`StateFlow<PlaybackState>`. `:core:media` implements it as `ExoVideoPlayer`, so no Media3 type
reaches the domain or common UI.

**Ownership.** Koin creates `ExoVideoPlayer` as an application singleton in `mediaModule`.
The ViewModel and the composition never own the player, so a rotation cannot recreate it. Every
player screen reuses the same instance, whether the video was opened from Home or from up next.
The `ExoPlayer` inside is built on first use and released only when Koin closes (`onClose`).
It holds the application context only. `stop()` also detaches the video surface: media3-ui-compose
detaches its `SurfaceView` only while the screen is still composed, so a popped player screen left
its surface on this app-wide player, and the next rotation leaked the destroyed `Activity` through
it. A heap dump analysed with LeakCanary's `shark-cli` showed the path.

**Lifecycle.** `PlayerViewModel` drives the player through intents. The screen reports
visibility with `ScreenShown` and `ScreenHidden`:

| What happens | Result |
|---|---|
| The app goes to the background | The video pauses. On return it resumes only if it was playing and the player screen is still showing. A video that finishes loading in the background waits until the screen is shown. |
| Rotation, or a dark-mode switch | Nothing. The screen ignores stop events while the activity is changing configuration. Only the video surface detaches and reattaches; playback continues without rebuffering. |
| Back, or the on-screen back arrow | Nav3 drops the popped entry's lifecycle to `CREATED` at once, so audio pauses before the exit animation (about 40–110 ms on the test devices). When the entry's ViewModel clears, it calls `stop()`, which unloads the video, frees the decoders, and detaches the surface. The player instance stays. |
| Up next | The current video stops at once, and the new route replaces the old one, so Back returns to Home. |
| Another screen covers the player (no such destination exists yet) | The video pauses, and resumes if the player screen returns. |

The shared player outlives each screen, so a screen controls it only while its own video is
loaded (`PlaybackState.videoId`). A screen that is being replaced can therefore never pause or
stop the video that replaced it. `VideoSurface` follows the same rule: it attaches only while the
player holds its video, and it keeps the screen on only while that video plays.

**HLS and ABR.** Every catalog item is HLS, played through `HlsMediaSource`. The default track
selector and bandwidth meter handle adaptive bitrate. Debug builds attach Media3's
`EventLogger` under the logcat tag `StreamlyPlayer`, so track switches are visible:
`adb logcat -s StreamlyPlayer | grep videoInputFormat`.

**One cache.** `MediaCache` owns the app's only `SimpleCache` (`StandaloneDatabaseProvider`,
`NoOpCacheEvictor`). Playback reads through a `CacheDataSource` over it, and `DownloadManager`
writes into the same cache, so downloaded videos play offline (see [Offline downloads](#offline-downloads)).
Playback reads but does not write, because a cache that never evicts would otherwise grow with
everything streamed.

**Adaptive layout.** A phone in portrait shows the 16:9 player above the details and up next.
At expanded widths, up next moves to a side column. A short window, such as a phone in landscape,
shows the video full screen in immersive mode.

### Shorts and the player pool

Shorts play from their own pool, separate from the long-form player. The domain declares the
contract, `ShortsPlayerPool`, with no Media3 types. `acquire()` returns a `ShortsPlayerLease`:
`show(visible, upcoming, playWhenReady)`, `play`, `pause`, `setMuted`, `retry`, and `close`. It
exposes a `StateFlow<ShortsPoolState>` holding the visible short's id, the `PlaybackState` of each
short that has a player, and the shared mute.

**Policy: at most two players.**

| Page | Player |
|---|---|
| Visible | Plays, looping (`REPEAT_MODE_ONE`). |
| The next page | Prepared and paused at its start, so a swipe forward starts at once and its first frame is already on screen. |
| Every other page | None. |

Only a *settled* page drives the pool, so a fling across several pages loads none of the pages it
passes. Players are recycled, not created: when the window moves, a player that already holds
the visible or next short keeps it (swiping forward plays the prepared player; swiping back keeps
the short you came from as the paused, rewound neighbour). A player whose short leaves the window
loads the newly needed one. On the last page the spare player is stopped, which frees its
decoders. The short that was playing is paused before another starts, and each player takes
audio focus when it plays, so two shorts, or a short and a long-form video, never sound together.

The policy lives in `SlotPool`, which is independent of Media3 and is covered by host tests with
fake slots: never more than two players, the visible one plays, the next one is prepared, far
pages hold none, recycling across fast swipes and jumps, never two playing at once, shared mute,
and release. `ExoShortsPlayerPool` backs each slot with an `ExoPlayer` built by the same factory
as the long-form player (HLS through the one `MediaCache` data source, audio focus, becoming-noisy).

**Scope.** The pool object is an application singleton in `mediaModule`, like the cache it reads
through, so only one pool can ever hold players and the composition never owns one. Its players
are screen-scoped. `ShortsViewModel` acquires the lease when it is created; the pool builds the
two `ExoPlayer`s on the first settled page and releases them when the ViewModel clears and closes
the lease. A lease that has been replaced is ignored, so a Shorts screen that is still leaving
can never pause or release the players of a newer one. Debug builds log pool events under the
logcat tag `StreamlyShorts`, including `Created pool player <id>` next to Media3's
`ExoPlayerImpl … Init <id>`.

**Lifecycle.** The Shorts screen reuses the player screen's `ScreenVisibilityEffect`:

| What happens | Result |
|---|---|
| The app goes to the background | The visible short pauses, and resumes on return only if it was playing. A short the user paused stays paused. |
| Rotation, or a dark-mode switch | Nothing. Only the surfaces detach and reattach; no player is created, paused, or reloaded. |
| Back, or the Home tab | The short pauses at once (Nav3 drops the popped entry to `CREATED`), and both players are released when the entry's ViewModel clears, about 0.8 s later, after the exit animation. |
| A tap on the video | Pause or resume, with a play indicator while paused. Taps are ignored while the short shows a playback error, so a stray tap cannot leave it paused once "Try again" recovers it. |

`ShortSurface` follows the long-form surface's rule: it attaches only while a pool player holds
its short, so a page whose player was recycled never shows another short's picture. Phones fill
the screen with the short (cropped). Medium and wider windows show it at 9:16, centered with black
bars, with the like, comment, and share actions beside it rather than over it.

### Offline downloads

Downloads use Media3's offline module. The domain declares `DownloadRepository` (`downloads`,
`storage`, `download(video)`, `retry`, `remove`) with a framework-free `VideoDownload` model
(`QUEUED`, `WAITING_FOR_NETWORK`, `DOWNLOADING`, `COMPLETED`, `FAILED`, `REMOVING`, percent, and
bytes). `:core:media` implements it as `MediaDownloads`, an application singleton.

**One cache, one database.** `MediaDownloads` owns the app's only `DownloadManager`. It writes into
`MediaCache`'s `SimpleCache`, shares its `StandaloneDatabaseProvider` (the download index and the
cache index live in the same database), and fetches through the same `DefaultHttpDataSource`
factory as playback. Nothing else creates a cache or database.

**Rendition.** `DownloadHelper` reads the master playlist and picks the best variant at or below
854x480, plus its audio. A 10.5-minute video is about 70 MB instead of about 490 MB at 1080p, and
a stream without such a variant falls back to its lowest one. Live streams cannot be downloaded,
so the player hides the action for them. The video's title, channel, thumbnail URL, and length are
stored as JSON in `DownloadRequest.data`, so the Downloads tab lists them offline without the catalog.

**Offline playback through the normal player.** The cache alone is not enough: with the network
off, `HlsMediaSource` would pick a variant from its bandwidth estimate, usually one that was never
downloaded. So when `ExoVideoPlayer` loads a video that has a *completed* download, it plays
`DownloadRequest.toMediaItem()`, whose stream keys restrict the playlist to the saved rendition.
Everything else, including partly downloaded videos, streams with full ABR as before. The check
lives inside `:core:media`, so stream keys never reach the domain or the UI. Debug builds log
`StreamlyDownloads: Playing download <id> (<n> stream keys)` when this happens.

**Progress.** `DownloadManager` reports state changes through its listener but not progress, so
`MediaDownloads` polls `getCurrentDownloads()` every 500 ms while anything is downloading and stops
when nothing is. Completed and failed downloads are not in `getCurrentDownloads()`, so it reads
the download index once at start (off the main thread) and applies listener events on top. The
progress mapping is host-tested (`DownloadMappingTest`, `DownloadMetadataTest`).

**Service and notifications.** `StreamlyDownloadService` is Media3's `DownloadService` running as a
`dataSync` foreground service (`FOREGROUND_SERVICE_DATA_SYNC` for Android 14+), so downloads
continue in the background. Its notification shows the title and percent, and a separate
notification reports "Download completed" or "Download failed". On Android 13+ the first tap on
Download asks for notification permission; the download runs whatever the answer, because only
the notification needs it. `MainActivity` starts the service at launch, so downloads left
unfinished by a killed process resume.

**Network policy.** Downloads may use any network (`Requirements.NETWORK`, Media3's default). There
is no settings screen yet to offer a Wi-Fi-only switch, and blocking on mobile data with no way
to override it would be worse. While offline, a download shows "Waiting for network" and resumes
on its own. `getScheduler()` returns `null`: Media3 never uses a scheduler on Android 12+, where the
service simply stays in the foreground until the network returns, and doing the same on older
versions avoids a job service and the boot permission for a path the test devices cannot exercise.

**UI.** The Player's Download action shows the real percent in a progress ring; tapping it while
queued or downloading cancels the download. "Downloaded" asks before removing, and "Retry"
restarts a failed download. The Downloads tab (mockup 05) shows the storage used and free space,
unfinished and failed downloads first (with a progress bar, "Waiting for network", or Retry), then
finished ones marked "Ready to play". Rows can be cancelled or removed (with a confirmation
dialog), and tapping a finished one opens the player. Removing a download takes it out of the
account's list at once; its files are deleted when no other account still has it saved (see
below), so the storage figure drops and the video no longer plays offline for that account.

**Downloads per account.** Downloads belong to the account that saved them, like YouTube's:
signing out hides them, and signing in again as the same account shows them. A video's files are
still saved once on the device, so the design keeps two layers:

- `DeviceDownloads` (`MediaDownloads`) is every download on the device, whoever saved it.
- `DownloadOwnershipRepository` (`:data`, its own DataStore file) records which accounts saved each
  video. The account key is `email:<address>` for a signed-in account and `guest` for a guest.
- `AccountDownloadRepository` (pure Kotlin in `:domain`, fully host-tested) combines the session,
  the owners, and the device downloads into the `DownloadRepository` the screens already used, so
  the Player and Downloads ViewModels did not change.

The rules: an account sees only its own downloads and its own storage figure. Downloading a video
another account already saved adds an owner and reuses the files, with no second download. Removing
it drops only that account; the files are deleted when the last owner removes them, because two
accounts' downloads share the same cached segments. The player plays a saved copy only for an
account that owns it and streams for anyone else. Until the owners have been read (a player
restored at launch), it trusts the saved copy, so an owner's offline playback never fails on a
race. Downloads that existed before this change are adopted by the first account that sees them.

Ownership is not stored in `DownloadRequest.data`: Media3's `DownloadManager.mergeRequest` moves an
existing download, even a completed one, back to the queue when its request is added again, so
changing owners there would re-download finished videos and break offline playback.

### Navigation shell

`AppShell` wraps the Nav3 `NavDisplay` with the tab chrome (Home, Shorts, Downloads, and Profile): a bottom `NavigationBar` on compact
and medium widths and a `NavigationRail` on expanded widths, shown only while a tab is on top.
The player and other pushed destinations take the whole window. `NavDisplay` stays at the same
place in the composition whether or not the chrome shows, so no back-stack state is lost.

Home is always the root of the back stack, and every other tab sits on top of it
(`selectTopLevel`). Back from Shorts and the Home tab therefore both pop Shorts, which is what
releases its players; returning to Shorts starts a fresh screen. A video opened from Downloads
plays on top of the Downloads tab, so Back returns there. Home's entry stays in the back
stack the whole time, so its ViewModel and its saved grid position survive switching tabs. The
chrome is dark while Shorts is selected, because Shorts always uses the dark color scheme.

### Profile and sign-out

Profile (mockup 06) is the fourth tab. `ProfileViewModel` reads `SessionRepository.session` and
shows the initials, name, and email under a brand header, then rows for Downloads (which selects
the Downloads tab), Watch history (which opens the history), Settings, and a red Sign out. In phone landscape the header
moves to a side panel; on wider windows the rows stay in a 640 dp column.

Sign out opens the confirmation (mockup 07): "Sign out?" with Cancel and a red Sign out. The
dialog lives in the ViewModel's state, so it survives rotation. Confirming clears the session and
replaces the whole back stack with Onboarding, so Back leaves the app and the next launch starts at
onboarding. A failed sign-out closes the dialog, shows a message, and leaves you signed in; a
second tap while signing out is ignored. Downloads are not deleted: they are hidden until their
account signs in again, and a download in progress keeps going in the background.

A guest sees a person icon, "Guest", and "Not signed in", and a primary "Sign in" row in place of
Sign out. It asks "Leave guest mode?" (guest downloads stay for the next time you continue as a
guest) and then opens onboarding.

### Watch history

Watch history is per account, like downloads: signing out hides it, signing in again as the same
account shows it, and a guest has its own. The player resumes a video where that account left
off.

**Rules** (`WatchHistoryEntry` in `:domain`, host-tested):

- A long-form video enters the history the first time it actually plays, not when the player
  opens, so a stream that fails to load is never listed. Shorts are not recorded.
- It resumes from the saved position if that is at least 5 s. In the last 10 s, or past 95% of
  the length, the video counts as finished and starts over, as in video apps.
- Live streams are listed (with a LIVE badge and no progress bar) but never resume, so they
  always join at the live edge.

**Recording.** `PlayerViewModel` already knows the moments that matter, so it saves the position
the first time the video plays, on pause, when the app goes to the background, when the video
ends, every 10 s of playback, and before it stops the player on Back or an up-next pick. A
crash or process death therefore loses at most about 10 s. A replaced player screen never
writes, by the same `PlaybackState.videoId` rule that keeps it from controlling the player. Only
the first play adds a video to the history; later saves only update its entry, so a video removed
while the player's last save is still on its way stays removed.

**Resume.** Before loading, `PlayerViewModel` reads the saved position and calls
`VideoPlayer.load(video, playWhenReady, startPosition)`. `ExoVideoPlayer` passes it to Media3's
`setMediaItem(item, startPositionMs)`, so the first frame drawn is already the resume point (no
0:00 flash and no extra seek), and a downloaded video resumes from its saved rendition offline.
Live keeps the default position, its live edge. Leaving the screen while the position is still
being read cancels the load, so nothing plays after the screen is gone.

**Storage.** `AccountWatchHistory` (`:domain`) resolves the account from the session and queues
writes, in order, on an application scope, so the save made as the player screen closes still
lands. `DataStoreWatchHistoryStore` (`:data`) keeps one JSON list per account in its own
`watch_history` DataStore file, newest first, at most 100 entries. Each entry stores the title,
channel, thumbnail URL, and length, so the history lists offline and after a video leaves the
catalog. A value that no longer decodes starts that account's history afresh.

**Screen.** Profile's Watch history row opens it (no tab bar; Back returns to Profile). Each row
shows the thumbnail with its length and a coral progress bar, the title, and the channel with
when it was last watched; tapping it opens the player, which resumes. The X removes a row at
once; "Clear all" asks first. Phones show a list, wider windows the feed's grid columns.

### Brand: icon, splash, and typeface

- **Launcher icon.** An adaptive icon drawn from the onboarding mark (`StreamlyLogo`): the frosted
  rounded tile and play glyph on the indigo brand gradient, with a monochrome layer for Android 13+
  themed icons and rendered PNGs for API 24–25.
- **Splash.** `androidx.core:core-splashscreen` shows the mark on indigo through the Android 12
  splash API, which the library backports to API 24 (checked on Android 14 and 16; the API 24–30
  path is not device-tested). `MainActivity` keeps it on screen while `AppViewModel` is still
  reading the stored session, so the first screen drawn is already the right one (Home or
  onboarding), with no placeholder frame. It shows when the app starts, including a restore after
  process death, but not on rotation or when switching back to a running app.
- **Typeface.** Baloo Da 2, the font the mockups use, applied once to the whole Material 3 type
  scale in `StreamlyTheme`, so no screen sets a font. Google Fonts ships it only as a variable
  font, whose weights need API 26, so the app bundles four static instances (Regular, Medium,
  SemiBold, Bold) cut from it with fontTools, with the full glyph set including Bengali. They are
  1.1 MB on disk and add about 0.5 MB to the APK. Its licence (SIL OFL 1.1) is in
  [`core/designsystem/licenses/`](core/designsystem/licenses).

### Tech stack

| Concern | Library |
|---|---|
| Language and concurrency | Kotlin 2.4, Coroutines + Flow |
| UI | Compose Multiplatform 1.12, Material 3, Material 3 adaptive (`WindowSizeClass`) |
| Navigation | Navigation 3 with entry-scoped ViewModels |
| Dependency injection | Koin 4.2 |
| Networking | Ktor 3 with kotlinx.serialization; a `MockEngine` serves the catalog API, OkHttp loads images |
| Persistence | DataStore Preferences |
| Media | Media3 1.11: ExoPlayer, HLS, Compose UI, offline downloads |
| Images | Coil 3 with the Ktor network fetcher |
| Testing | `kotlin.test`, `kotlinx-coroutines-test`, Turbine, Ktor `MockEngine`, Compose UI tests |

All versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## AI-assisted workflow

The project is built with Claude Code as the agent throughout.

- **One rules file.** [`AGENTS.md`](AGENTS.md) is the only agent configuration. `CLAUDE.md`,
  `.cursor/rules/agents.mdc`, `.codex/instructions.md`, and `.antigravity/rules/agents.md` are
  symlinks to it. Detailed references live in [`docs/agent-reference/`](docs/agent-reference).
- **Small, verified tasks.** Each task has one outcome and its own branch (Gitflow:
  `feature/*` → `develop` → `release/*` → `main`). The agent builds the change, runs the tests,
  and exercises it on a device or emulator before the task counts as done.
- **Human review before every commit.** The agent stages each change and stops. I review the diff
  against the rules files before approving the commit or merge.
- **Visible agent involvement.** Agent-authored commits use Conventional Commits and carry a
  `Co-Authored-By` trailer.
- **Fresh context per task.** At each task boundary the agent writes a self-contained handoff
  prompt for the next task, which starts in a new chat.
- **Plan before code.** From the downloads task on, the agent settles open design questions with
  me first (superpowers *brainstorming*), saves a dated plan in [`docs/plans/`](docs/plans), and
  builds test-first.
- **Agent log.** [`docs/agent-log.md`](docs/agent-log.md) records every task: the prompt, the
  agent's decisions and reasoning, the problems it found, the verification evidence, and the
  commits.

## Shortcuts

- **Mocked authentication.** The brief does not require a real auth API. "Continue with Google"
  signs in at once with a fixed demo profile (Anika Rahman, anika@streamly.app). Email sign-in
  asks only for a valid address, with no password, and builds the display name from it
  (`jane.doe@…` becomes "Jane Doe"). The session is stored locally in DataStore.
- **Mocked catalog API.** The repo is private, so there is no hosted JSON to fetch. The video
  catalog is bundled with the app (`BundledCatalog`) and served by `CatalogMockApi`, a Ktor
  `MockEngine` that answers `GET videos`, `videos/{id}`, and `videos/{id}/up-next` after about
  600 ms of simulated latency. Requests still go through the real client pipeline (content
  negotiation, DTOs, mappers, and `safeCall`, which maps failures to `DataError.Network`), so a
  real backend only needs a different engine. `ktor-client-mock` is therefore a production
  dependency of `:data`. Because the catalog is local, the feed also loads offline.
- **Demo metadata over real streams.** Titles, channels, view counts, and dates are made up. Every
  video is a public HLS test stream (Mux, Apple, Shaka, Unified Streaming), including two live
  streams, and every thumbnail is a public image; all were checked to respond when added.
- **Shorts streams.** `GET shorts` is served by the same mock engine from `BundledShorts`. The
  eight shorts are genuinely vertical (720x1280, H.264/AAC, 8 s) public HLS clips from
  TheWidlarzGroup's open-source
  [react-native-video-feed](https://github.com/TheWidlarzGroup/react-native-video-feed) demo,
  hosted on its Netlify deploy, and they end on that studio's logo. Two trade-offs: each has a
  single rendition, so adaptive bitrate is demonstrated by the long-form player rather than
  Shorts, and a third-party deploy could disappear. Every other public "portrait HLS" list found
  had dead links. Swapping in other streams is a JSON-only change; a landscape stream would be
  cropped to fill the page.
- **Static chips.** All, Music, and Live filter the loaded feed locally rather than querying
  the API. Live matches streams with no fixed duration, so a live music stream appears under both.
- **Player actions are stubs, except Download.** Like and Subscribe toggle only for the current
  screen and are not saved. Share shows a "coming soon" message. Download is real (see
  [Offline downloads](#offline-downloads)).
- **Settings is a stub.** Its Profile row shows a "coming soon" message.
- **Shorts actions are stubs.** Like toggles and counts the user's like for as long as the Shorts
  screen lasts. Comment and Share show "coming soon" messages.
- **Media segments use Media3's HTTP stack.** The API goes through Ktor, but HLS playlists and
  segments load through Media3's `DefaultHttpDataSource`. Media3 has no Ktor data source, and
  writing one would add risk to the most heavily graded area without changing behavior.

### Known polish gaps

- Status bar icons are always light, which suits the current brand-colored headers. Screens with
  light headers will need per-screen system bar styling.
- The mockup's two header icons are not shown: search does not exist, and Profile is a tab.
- Downloads of an account that never signs in again stay on the device; nothing reclaims them.
- The download notification shows the title of whatever is downloading, whichever account is
  signed in.
- If the ownership file is ever corrupted, it starts empty and the first account seen adopts every
  download on the device, including other accounts' ones.
- A thumbnail that failed to load while offline keeps its placeholder until its card scrolls out
  and back, or the screen is reopened; Coil does not retry when the connection returns.
- The feed's error state is covered by unit tests but cannot be triggered on a device, because
  the bundled catalog never fails.
- On a phone in landscape, the header and chips take a large share of the height. A collapsing
  header would give the grid more room.
- Playback stops when the app is in the background. There is no `MediaSession`, background
  audio, notification, or picture-in-picture.
- Up next does not autoplay when a video ends; the replay button restarts it.
- The live test streams sometimes rebuffer on the emulator. The buffering indicator shows while
  they do.
- Shorts have no poster image, so a short that has not buffered yet shows black with a spinner.
  The next short is prepared ahead, so this is visible mainly on the first page and after a jump.
- Shorts has no progress bar and does not remember its page after you leave it; it starts from the
  first short each time.
- Selecting the Shorts tab again does not scroll back to the first short.
- Watch history is stored on the device only, per account; with mocked sign-in there is nothing
  to sync it to. Removing a row has no undo, and the history keeps the newest 100 videos.
- Downloads always save the up-to-480p rendition and may use mobile data; there is no quality or
  Wi-Fi-only setting yet.
- Offline, the Downloads tab and the player show a thumbnail only if Coil cached it while online;
  otherwise the placeholder shows. Thumbnails are not saved with the download.
- Cancelling from the player's progress ring or the Downloads row deletes the partial download
  without asking; only finished downloads ask before removal.
- The notification permission is requested from the Download action. If it is denied, downloads
  still run without a notification; a later Download tap asks again until Android stops showing
  the prompt (after a second denial), and there is no in-app explanation of why it is useful.
