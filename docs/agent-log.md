# Agent log

Streamly is built with Claude Code as the agent and a human reviewer approving every commit and
merge. This log records each task: the prompt, how the agent worked, the decisions it made and
why, the problems it found, how it verified the result, and the commits that landed.

**How entries are written.** The agent appends an entry at the end of each task, before staging
it for review, and the entry is committed with the task's docs. From the downloads task on, each
entry links the task's design plan in [`docs/plans/`](plans).

**Backfilled entries.** The log started on October 2, after five tasks had merged. The entries for
the first four tasks were reconstructed from their commit messages and handoff prompts, so they
record outcomes and decisions but not every step. The Shorts entry was written from the session
itself.

## Workflow

1. **Prompt.** Each task starts in a fresh chat from `FRESH_PROMPT.md`, a self-contained handoff
   the agent writes at the end of the previous task (goal, acceptance criteria, relevant files,
   decisions, pitfalls, and the evidence so far).
2. **Design and plan.** From the downloads task on, the agent uses the superpowers skills:
   *brainstorming* to settle open design questions with the reviewer before any code, then
   *writing-plans* to save a dated plan in `docs/plans/`.
3. **Build test-first** (superpowers *test-driven-development*) on a `feature/<task>` branch.
4. **Verify.** Build, `./gradlew check`, and the changed journey on a device or emulator, with the
   evidence recorded. Nothing counts as done on unit tests alone.
5. **Review.** The agent stages focused Conventional Commits and stops; the reviewer approves the
   diff before any commit or merge. Every agent commit carries a `Co-Authored-By` trailer.

## Tasks

### 1. Project setup — October 1

- **Branch:** `feature/project-setup`, merged in `cd6f685`. Rules commits on `develop` before it.
- **Prompt:** set up the module graph and every dependency the assignment needs, and make the
  build an acceptance criterion.
- **What the agent did:** wrote the single rules file (`AGENTS.md`, with per-tool symlinks) and
  the reference docs, then built the module graph (`:domain`, `:data`, `:core:designsystem`,
  `:core:media`, `:shared`, `:androidApp`) with explicit API mode, a pinned version catalog, and a
  Koin bootstrap.
- **Decisions:** Koin over Hilt; `:core:media` owns all Media3 code so playback and downloads share
  one cache; KMP with an Android target only.
- **Process changes the reviewer asked for:** approval before every commit and merge (`6808f7d`),
  Conventional Commits (`8240a7e`), feature branches only for main features and never deleting
  branches (`6c33d6f`).
- **Commits:** `12fd4a3`, `6808f7d`, `8240a7e`, `37aa72d`, `97d746e`, `ded70b5`, `3e32783`,
  `6c33d6f`.

### 2. Session and onboarding — October 1

- **Branch:** `feature/session-onboarding`, merged in `93f2b52`.
- **Prompt:** persisted session, onboarding with Google, email, and guest, and routing from the
  stored session.
- **What the agent did:** a typed `Result`/`DataError`, a DataStore session with mocked auth, the
  theme and on-brand components, MVI onboarding and email sign-in, and Nav3 routing that starts
  at Onboarding or Home.
- **Decisions:** mocked auth with a fixed Google demo profile; a damaged stored session reads as
  signed out; sign-in resets the back stack so Back exits rather than returning to onboarding.
- **Bugs found during device testing and fixed before merge:** the solid pill label was illegible
  in dark theme (`1e701f7`); the email screen's back button was covered by the scrolling content
  and could not be tapped, and Compose resources do not unescape `\'` (`18c75bf`).
- **Commits:** `2bd5d74`, `a3faea5`, `85a427b`, `963c436`, `1e701f7`, `18c75bf`, `02ba027`,
  `fc9d790`.

### 3. Home feed — October 1

- **Branch:** `feature/home-feed`, merged in `c1fb4cb`.
- **Prompt:** the feed with category chips, video cards, loading, empty, and error states, and
  navigation to a player destination.
- **What the agent did:** the video catalog contract, a Ktor `MockEngine` that serves a bundled
  catalog through the real client pipeline, the adaptive feed grid with skeleton loading, and a
  placeholder player route.
- **Decisions:** the repo is private, so the API is mocked in-process rather than hosted; a real
  backend only needs another engine. Every stream and thumbnail URL was checked to respond. A
  `Clock` is injected so ages are testable, and the selected chip survives process death.
- **Commits:** `6f9df53`, `3077b8f`, `fc71ec8`, `76a5f85`, `77c2f8b`.

### 4. HLS player — October 2

- **Branch:** `feature/hls-player`, merged in `18ff789`.
- **Prompt:** the normal-video player: one shared `ExoPlayer`, controls, lifecycle, up next.
- **What the agent did:** the `VideoPlayer` contract, `ExoVideoPlayer` over the app's only
  `SimpleCache`, an attach-only-when-owned video surface, and the MVI player screen with three
  adaptive layouts.
- **Decisions:** the player is an application singleton, so rotation never recreates it; a screen
  controls it only while its own video is loaded. The reviewer decided that HLS segments load
  through Media3's HTTP stack rather than a Ktor data source.
- **Verification:** emulator (Android 16) and Galaxy A04 (Android 14): ABR from 0.83 to
  8.06 Mbps, scrubbing, mute, background pause and resume, rotation without rebuffering, Back
  pausing in 41–114 ms, up next, network errors with retry, and tablet and immersive layouts.
- **Missed:** the leak check ran only while on the player screen, so the leak fixed in task 5
  (`29627fb`) went unnoticed.
- **Commits:** `30b267f`, `51da339`, `fd8efd0`, `2a02d40`, `5acfe38`.

### 5. Shorts with a pooled player — October 2

- **Branch:** `feature/shorts`, merged in `b9ee4d2`.
- **Prompt:** a full-screen vertical pager of HLS shorts that autoplays only the visible item,
  from a pool of at most two players, plus a Home/Shorts navigation shell.
- **How the agent worked:** read the references and mockup 03, searched for public vertical HLS
  streams, then designed and built the change in one pass. It did **not** use the superpowers
  brainstorming, plan, or test-first skills: it treated the handoff prompt as the plan, announced
  its design choices in chat instead of agreeing them first, and wrote tests alongside the code.
  The reviewer flagged this afterwards, which is why later tasks follow the workflow above.
- **Streams:** the only public "portrait HLS" list found had dead links (HTTP 400), and the other
  test-stream lists had no vertical streams. The agent found vertical clips in TheWidlarzGroup's
  open-source video-feed demo, checked them with `curl` and `ffprobe` (720x1280, H.264/AAC, 8 s,
  a single rendition each), and picked eight by their content, skipping a branded clip and a
  mostly black one.
- **Decisions:**
  - The pool hands out a *lease*; a replaced lease is ignored, so a Shorts screen that is still
    leaving cannot pause or release a newer one's players.
  - The pool policy lives in `SlotPool`, independent of Media3, so host tests drive it with fake
    slots. Players are recycled, never created, as the window moves.
  - Only the settled page drives the pool, so a fling loads none of the pages it passes.
  - The pool is application-scoped; its players are screen-scoped (built on the first page,
    released when the ViewModel clears).
  - Home is the root of the back stack and other tabs sit on top, so Back and the Home tab both
    leave Shorts for good. Shorts always uses the dark scheme.
- **Problems found and fixed:**
  - **Activity leak (pre-existing, from task 4).** Six rotations reported `Activities: 2`. The
    agent bisected the trigger (player → Back → any configuration change), analysed the heap dump
    with LeakCanary's `shark-cli` (path: `ExoPlayerImpl.surfaceHolder` → `SurfaceView` → the
    destroyed `MainActivity`), and read media3-ui-compose 1.11.1's `PlayerSurface` source: it
    detaches only while still composed. Fix: `stop()` also calls `clearVideoSurface()`.
  - **Stray tap while offline.** A tap on a failed short paused it, so it stayed paused after
    "Try again" recovered it. Taps are now ignored during a playback error (with a test).
- **Verification:** 132 host tests (14 for the pool policy, 18 for the ViewModel); `check` green;
  each of the 10 commits built and tested on its own in a scratch worktree. On the emulator and
  the A04: exactly one AudioTrack playing, 2 `ExoPlayerImpl Init` lines after 18+ swipes,
  background and return, rotation and theme switches with no new player or seek, release about
  0.8 s after Back or the Home tab, a normal video playing alone afterwards, light, dark,
  landscape, and tablet layouts, offline errors with recovery, and `Activities: 1`.
- **Commits:** `fe913fb`, `2b9fcd4`, `5adedd8`, `a6cb8e7`, `29627fb`, `2f4e290`, `fe6b967`,
  `96ade1a`, `9cb1ec1`, `a5058aa`.

### 6. Offline downloads — October 2

- **Branch:** `feature/downloads`.
- **Plan:** [`docs/plans/2026-10-03-downloads.md`](plans/2026-10-03-downloads.md), the agreed design
  and the implementation plan in one file.
- **Prompt:** real Media3 downloads with progress, offline playback through the normal player, and
  removal; a Player Download action and a Downloads screen per mockup 05.
- **How the agent worked:** the first task to follow the full workflow.
  - **Brainstorming (superpowers *brainstorming*).** The agent read the references, mockup 05, and
    the Media3 1.11 offline sources first, then asked the reviewer the five open questions one at a
    time. Decisions: the best rendition up to 480p, any network, a confirmation dialog for removal,
    a progress ring that cancels, and Downloads as the third tab. It then proposed three
    approaches; the reviewer chose a download tracker inside `:core:media` that the player
    consults. The design was approved in four sections.
  - **Plan (superpowers *writing-plans*).** Seven tasks with test-first steps and code, a Review
    Focus list of risks no unit test covers, and the commit sequence. The reviewer approved the
    plan and chose inline execution with one fresh review of the whole branch at the end.
  - **Build (superpowers *executing-plans* + *test-driven-development*).** Each piece of logic
    started with a failing test, watched to fail for the right reason before the code was written:
    the domain model, the Media3 state mapping and stored metadata, the Player download intents,
    `formatBytes`, and `DownloadsViewModel`. A ledger recorded each task and every deviation.
  - **Verify (superpowers *verification-before-completion*).** Build, `check`, and the device
    journeys below, before any claim of completion.
- **Decisions:**
  - **Why the player must know about downloads.** Reading the Media3 sources showed that the cache
    alone does not make offline playback work: with the network off, `HlsMediaSource` picks a
    variant from its bandwidth estimate, usually one that was never downloaded. A completed
    download therefore plays `DownloadRequest.toMediaItem()`, whose stream keys restrict the
    playlist to the saved variant. The check lives in `:core:media`, so stream keys never reach
    the domain or the UI.
  - **One cache, one database.** `DownloadManager` writes into `MediaCache`'s `SimpleCache` and
    shares its `StandaloneDatabaseProvider`; the media cache exposes the provider instead of
    creating a second one.
  - **Progress is polled** every 500 ms only while something is downloading. The download index is
    read once off the main thread (completed and failed downloads are not in
    `getCurrentDownloads()`), and listener events that arrive during that read are buffered and
    applied after it.
  - **No scheduler** (a change from the agreed design, flagged in the plan): Media3 never calls
    `getScheduler()` on Android 12+, where the service stays in the foreground until the network
    returns, so `null` gives the same behavior on every version without a job service or the boot
    permission.
  - **Rulings during the build** (recorded in the ledger): `C.PERCENTAGE_UNSET` is an `Int` in
    Media3 1.11, so any negative percent maps to "unknown"; `FeedMessage`'s action became optional
    for the Downloads empty state; the "Ready to play" green is darker in light mode for contrast;
    the Download action is slightly wider than Like and Share so "Downloaded" fits on a 360 dp phone.
- **Problems found:**
  - Two plan defects caught by the failing-test step: the `C.PERCENTAGE_UNSET` type, and a test that
    compared `42.7f / 100f` with `0.427f` (Float rounding). Both were fixed in the test or the
    mapping, not by weakening the behavior.
  - On the Galaxy A04 (360 dp wide), "Downloaded" was truncated to "Downloa…". The agent widened the
    Download action and re-checked it on the device.
  - The early device smoke test (run before the Downloads screen existed) confirmed that the media
    layer worked offline before any UI was built on it.
- **Verification:** 164 host tests (32 new: domain 1, media 8, Player downloads 10, Downloads
  ViewModel 12, `formatBytes` 1); `./gradlew check` green with only the 7 lint warnings that predate
  this work. On the emulator (Android 16) and the Galaxy A04 (Android 14):
  - Real progress: the Player ring, the Downloads row, and the notification agreed (for example 18%
    in the player and 20% in the notification; 5% · 4.5 MB in the row and 4% in the notification).
  - Background: 19% → 38% in 15 s (emulator) and 26% → 78% (A04) as a `dataSync` foreground service.
  - Force-stop mid-download, relaunch: resumed from 45% to 52%; completed downloads still listed.
    Rotation and the remove dialog survive configuration changes.
  - Offline: `Playing download big-buck-bunny (1 stream keys)` at 848x480; seek to 619 s, played to
    `ENDED` at 634.6 s; seek to 317 s; zero `loadError` lines. The fMP4 stream with separate audio
    played its 768x432 variant with 2 stream keys; the Shaka stream 640x480 with 3.
  - Process killed on the Player while offline, then relaunched: restored onto the Player and played
    the download (Review Focus 2).
  - Removal: the cache went from 78 MB to 8.6 MB (emulator) and 77 MB to 8.4 MB (A04); the storage
    line dropped; the removed video then failed offline with the error overlay.
  - "Waiting for network" in the row and the notification while offline; the "Couldn’t start the
    download" message when starting offline; cancel from a row; live videos have no Download action;
    the download ran with notifications denied.
  - Regression: Shorts kept 2 `ExoPlayerImpl … Init` lines after 10 swipes with one AudioTrack
    started; a non-downloaded video streamed its top variant (1680x750) with ABR; `Activities: 1`
    after player → Back → Downloads → 6 rotations on both devices. Dark, light, phone landscape
    (rail with a two-column grid), and a 2400x1800 tablet.
- **Fresh review of the whole branch** (one reviewer subagent on the most capable model, as the
  reviewer agreed at plan time). No critical issues; it confirmed the layering, the shared cache
  and database, the threading against the Media3 sources, and all five Review Focus items. The
  agent fixed four findings; three were proven by a test or device check that failed first:
  - **TalkBack could not cancel from the Player.** The accessibility label used `onClick { false }`,
    which replaced the button's click. A new Compose device test
    (`DownloadButtonAccessibilityTest`) failed with no intent sent, and passed after `action = null`.
    The test also needed Espresso 3.7.0, because the version Compose's test library pulls in crashes
    on Android 16.
  - **Cancel was ignored while a download was starting, and Back dropped the start.** The
    `DownloadHelper` pass now runs in `MediaDownloads`' application scope, `remove()` cancels a
    start in progress, and the Player cancels its own wait (`cancellingWhileStartingStopsTheStart`).
    On the emulator: Download then Cancel within 150 ms sent no download to the service, and
    Download then Back within 150 ms still downloaded (29% · 38 MB in the Downloads tab).
  - **Unexpected errors while starting would crash** instead of showing a message; they now map to
    `DownloadError.UNKNOWN`. This one has no test: `MediaDownloads` needs Media3 and Android, so it
    is not host-tested.
  - **Medium windows (600–840 dp) showed one stretched column.** The grid now uses the same
    `WindowSizeClass` columns as the feed; checked at 700 dp (one column before, two after).
  - Six minor findings were deferred and listed in the handoff (for example an unguarded fallback
    index read and a `formatBytes` unit boundary).
- **Final count:** 165 host tests and 1 device test; `./gradlew check` green.
- **Commits:** `82d2ac2`, `9bf2390`, `c99e8c3`, `bd5db06`, `59e4770`, `6831ed5`, and this docs commit.
