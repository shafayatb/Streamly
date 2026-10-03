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

### 7. Profile, sign-out, and per-account downloads — October 2

- **Branch:** `feature/profile`.
- **Plan:** [`docs/plans/2026-10-03-profile.md`](plans/2026-10-03-profile.md), the agreed design and
  the implementation plan in one file.
- **Prompt:** the Profile screen (mockup 06) and the sign-out confirmation (mockup 07), completing
  the navigation shell: avatar, name, and email from the session, rows for Downloads, Watch
  history, Settings, and Sign out; a dialog that survives rotation; confirming clears the session
  and returns to onboarding.
- **How the agent worked:** the same workflow as task 6.
  - **Brainstorming (superpowers *brainstorming*).** The agent read the references, the task 6 log,
    mockups 06 and 07, and the session and navigation code, then asked the four open questions.
    The reviewer chose a guest "Sign in" row with its own dialog, "coming soon" snackbars for Watch
    history and Settings, and Profile as a fourth tab. For downloads the agent recommended removing
    them all on sign-out (Netflix/Spotify); **the reviewer asked instead to tie downloads to the
    account that saved them**. The agent explained the cost (shared segments, migration, abandoned
    files), the reviewer confirmed, and the agent read the Media3 1.11 sources before proposing
    where ownership lives. The design was approved in three sections.
  - **Plan (superpowers *writing-plans*).** Eight tasks with test-first steps and code, five Review
    Focus risks each tied to a test and a device step, and a seven-commit sequence. The reviewer
    approved it and chose inline execution with one fresh review at the end.
  - **Build (superpowers *executing-plans* + *test-driven-development*).** Every piece of logic
    started with a failing test. Because those first failures were compile errors, the agent also
    broke the code on purpose for the Review Focus rules (no start cancel, no adoption, fail closed,
    no double-confirm guard) and checked that exactly the intended test failed each time. A ledger
    recorded each task and ruling.
  - **Verify (superpowers *verification-before-completion*).** Build, `check`, a device test, and
    the device journeys below, before any claim of completion.
- **Decisions:**
  - **Ownership outside Media3.** `DownloadManager.mergeRequest` (DownloadManager.java:657) puts an
    existing download, even a completed one, back in the queue when its request is added again, so
    owners cannot live in `DownloadRequest.data`: changing them would re-download finished videos
    and stop offline playback. Owners live in their own DataStore file instead.
  - **Rules in the domain.** `AccountDownloadRepository` (pure Kotlin, 21 host tests) wraps the
    device-wide `DeviceDownloads` and implements the existing `DownloadRepository`, so the Player
    and Downloads ViewModels did not change. A shared video keeps one copy with a set of owners;
    its files are deleted only when the last owner removes it. `removeOwner` returns the remaining
    owners from the same DataStore write, so "was that the last owner?" cannot race.
  - **Offline playback per account,** with one deliberate exception: until the owners are read (a
    player restored at launch), the saved copy is trusted, so an owner's offline restore never
    fails on a race.
  - **Rulings during the build** (from the ledger): the remove dialog said "will be deleted from this
    device", which is false for a shared video, so it now says "will be removed from your
    downloads"; DataStore writes share one `tryEdit` helper; the Sign out label uses a darker red
    in light mode for contrast; Profile rows have leading icons, which mockup 06 does not show.
- **Problems found:**
  - The agent's first relaunch test for Review Focus 1 used `am start`, which starts a fresh task at
    Home rather than restoring one; resuming from Recents is the restore path, and it passed there.
  - A tap meant for Tears of Steel landed on Big Buck Bunny's player and opened its remove dialog;
    the agent cancelled it, confirmed nothing changed, and redid the check on the right video.
- **Verification:** 209 host tests (domain 30, data 28, shared 124, media 27) and 5 device tests;
  `check` green with only the 7 pre-existing lint warnings. On the emulator (Android 16) and the
  Galaxy A04 (Android 14):
  - Profile for Google ("AR", Anika Rahman), email ("JD", Jane Doe from `jane.doe@example.com`), and
    guest (person icon, "Not signed in", primary "Sign in" with "Leave guest mode?"); light, dark,
    phone landscape (header beside the rows), a 700 dp medium window, and a 2400x1800 tablet.
  - Downloads in Profile opens the Downloads tab, and Back returns to Home; Watch history and
    Settings show their snackbars above the bar.
  - Sign out → Cancel stays signed in; the dialog survives rotation on both devices; confirming
    returns to onboarding with the session cleared and no started player in `dumpsys audio`; Back
    then shows the launcher, and relaunching starts at onboarding.
  - Upgrade: task 6 downloads were adopted by the signed-in account on both devices (guest on the
    emulator, Anika on the A04) and still played offline.
  - Per account: Anika sees none of the guest's downloads; Jane sees none of Anika's; Jane opening
    Anika's download offline gets the error overlay, not the saved copy. A download Jane started
    at 12% kept going after she signed out (59%, then completed) and never appeared for Anika.
  - Shared files: downloading a video another account saved completed at once with no new
    download, and removing it for one account left the cache unchanged (100 MB on the emulator,
    8.4 MB on the A04) and the other account still played it offline.
  - Quick cancel: Download then Cancel within 100 ms left no owner record and no download, and
    nothing was adopted after a relaunch.
  - Restored offline: killed on the Player while offline, then resumed from Recents:
    `Playing download big-buck-bunny (1 stream keys)` at 848x480.
  - Regression: Shorts kept 2 `ExoPlayerImpl … Init` after 10 swipes with one started track and
    none after switching to Profile; `Activities: 1` after six rotations on both devices.
- **Fresh review of the whole branch** (one reviewer subagent on Fable 5.1). No critical issues; it
  confirmed the ownership design, the Koin wiring, and all five Review Focus items. The agent fixed
  two findings, each proven by a test that failed first:
  - **After a failed ownership read, Remove and Retry silently did nothing** for the rest of the
    process, because they relied on a cached snapshot that the failed read froze. They now read the
    account and the owners afresh (`removeWorksAfterAFailedOwnersRead`,
    `retryWorksAfterAFailedOwnersRead`).
  - **A download still being removed at launch was adopted** (graded Minor by the reviewer, raised
    to Important because a removed video would come back and its files never be reclaimed). Adoption
    now skips `REMOVING` (`aDownloadBeingRemovedIsNotAdopted`). On the emulator, removing Big Buck
    Bunny afterwards dropped the cache from 288 MB to 219 MB.
  - A corrupted ownership file would hand every download to the first account seen; this is in the
    README's known gaps. Nine other minor findings were deferred to the release task.
- **Commits:** `1b3e996`, `0ee48d2`, `f5dcf93`, `6c6298c`, `a57e1c6`, `cf78b07`, and this docs commit.

### 8. Polish: launcher icon, splash, and typeface — October 2

- **Branch:** `feature/polish`.
- **Plan:** [`docs/plans/2026-10-02-polish.md`](plans/2026-10-02-polish.md), the agreed design and
  the implementation plan in one file.
- **Prompt:** the handoff asked for the October 4 release gate on `release/1.0`. In brainstorming
  the reviewer re-sequenced the work: **features first, one release**. Three feature tasks come
  before the gate (this polish task, then Watch history, then Settings), and the release fixes
  were narrowed to the user-visible download minors. `release/1.0` was created and left at
  `develop`'s head.
- **How the agent worked:** the same workflow as tasks 6 and 7.
  - **Brainstorming.** The agent answered a Gitflow question (a release branch is cut from
    `develop`, then merged into `main` and back into `develop`; `develop` never merges into `main`
    directly), and explained that the brief only asks for *links* to History and Settings before
    the reviewer chose to build them. For polish it read the brief's PDF: `pdffonts` showed the
    mockups' typeface is **Baloo Da 2**, used for body text too. It also found that the launcher
    icon was still the Android Studio template robot, and the reviewer added a branded icon to the
    scope. The design was approved in two sections.
  - **Plan.** Five tasks with a failing device test first, a Review Focus list (Baloo's tall line
    metrics, truncation at 360 dp, the splash never hanging or re-appearing, themed and legacy
    icons, existing tests), and a five-commit sequence. The reviewer chose inline execution with
    one fresh review at the end.
  - **Build (superpowers *executing-plans* + *test-driven-development*).** The typography test
    failed for the right reason (`No bundled font family: {displayLarge=FontFamily.SansSerif, …}`)
    before the fonts existed, and passed after. A ledger recorded every step and ruling.
- **Decisions:**
  - **Static font instances.** Google Fonts ships Baloo Da 2 only as a variable font, whose weights
    need API 26 (`minSdk` is 24). The agent cut Regular, Medium, SemiBold, and Bold with fontTools,
    after checking that the OFL declares no Reserved Font Name. The full glyph set, including
    Bengali, stays; the APK grows by about 0.5 MB.
  - **One place for the font.** Material 3's `Typography(fontFamily = …)` constructor applies the
    family to every style, so no screen changed.
  - **The splash waits for the session.** `MainActivity` takes `AppViewModel` from Koin and keeps
    the splash while its state is `Loading`, so the first frame is already Home or onboarding.
  - **The icon is the onboarding mark** (the reviewer's choice): `StreamlyLogo`'s tile and glyph on
    the brand gradient, a monochrome cut-out for themed icons, and PNGs rendered with Pillow.
  - **A benchmark build at the release gate** (the reviewer's suggestion during verification): a
    non-debuggable, R8-minified, debug-signed build type for timing and the demo, added on
    `release/1.0` where the APK and demo are chosen.
- **Problems found:**
  - **A stale resource merge.** With `minSdk` 24, AGP strips `-v24`, so the template's
    `drawable-v24/ic_launcher_foreground.xml` and the new `drawable/` one compile to the same
    output. The incremental merge added the new file and then deleted the old one's output, and the
    build cache stored the result. `mergeDebugResources --rerun` fixed it.
  - **The A04's 6-second cold start.** Holding the splash makes `am start -W` report about 5.9 s on
    the Galaxy A04, so the agent built `develop` in a scratch worktree and compared recordings: tap
    to Home took about 6.4 s before (4.4 s of the system's robot splash, then the plain gradient) and
    about 6.0 s after. The time is the debug build's process start, not the session read.
- **Verification:**
  - **Tests.** `./gradlew check` green: 209 host tests (domain 30, data 28, shared 124, media 27),
    lint 0 errors and 4 warnings (down from 7: the `drawable-v24` and two monochrome-icon warnings
    are gone). `:shared:connectedAndroidDeviceTest` on the emulator: 131 tests, 0 failures,
    including the 7 Compose device tests (5 before, plus the 2 typography tests).
  - **Cold start**, recorded with `screenrecord` and read frame by frame. Emulator signed in: the
    splash for about 2.2 s, then Home, with no onboarding or blank frame. Signed out: the splash,
    then onboarding. Rotation, a dark/light switch, and Home-and-return never show it again.
    Process death (`am kill` while backgrounded on the Player, then Recents): a new process, the
    splash, then the same Player.
  - **Icon.** The emulator drawer and home screen, the A04 (One UI's squircle mask), and Android 16
    themed icons (the tile with the glyph cut out), switched back off afterwards.
  - **Font at 360 dp** on the A04 in dark and light: Home (chips, cards, tab bar), the Player
    ("Downloaded" fits, up next, the LIVE badge, controls, the offline error), Downloads, the
    remove dialog, Shorts, Profile, the sign-out dialog, onboarding, and email sign-in (error,
    typed text, floating label), plus a snackbar. No clipping, labels centred, no new ellipsis.
    Emulator phone landscape and a 2400x1800 tablet.
  - **Regression.** Shorts kept 2 `ExoPlayerImpl … Init` after six swipes; a long-form video
    played at 1280x720 with one started AudioTrack.
  - **Unverified:** the API 24–25 PNG icons and the splash backport on API 24–30 (only API 34 and
    36 images are installed).
- **Fresh review of the whole branch** (one reviewer subagent on Fable 5.1). No critical issues; it
  confirmed the font instances, the OFL position, the splash wiring (it cannot hang, because the
  session flow maps errors to "signed out"), and the icon geometry. One Important finding: the
  plan's process-death check had no recorded result. The agent ran it (above) and also closed the
  themed-icon check. It corrected two overstated README claims (the API 24–30 splash, which is
  untested, and the font size, which is 0.5 MB in the APK, not 1.1 MB). Four minor findings were
  deferred: the typography test pins the weights but not the font files; very large font scales
  could let the tallest accented capitals draw outside the tightest styles; `App`'s unused
  `koinViewModel()` default; and the light-only post-splash window theme, which predates this task.
- **Commits:** `ab20b55`, `d68b57b`, `a30e051`, `31cb319`, and this docs commit.

### 9. Watch history and resume — October 3

- **Branch:** `feature/watch-history`.
- **Plan:** [`docs/plans/2026-10-03-watch-history.md`](plans/2026-10-03-watch-history.md), the agreed
  design and the implementation plan in one file.
- **Prompt:** replace Profile's "Watch history is coming soon" stub with a real history. The
  handoff listed the open questions (what counts as watched, per account or not, resume position,
  storage, the screen, offline) and asked the agent not to decide them silently.
- **How the agent worked:** the same workflow as tasks 6–8.
  - **Brainstorming (superpowers *brainstorming*).** The agent read the references, the task 6–8
    log, and the player, session, Profile, download-ownership, and navigation code. It classified
    the task as architectural and asked one question at a time. The reviewer chose:
    - **Resume and a list**, not a list only, because resume is graded Media3 work.
    - **Long-form only, recorded when playback actually starts**; live listed without a position.
    - **Per account with guest history**, like downloads (mockup 07's "sign in again to see your
      downloads and history").
    - **DataStore with one JSON list per account**, with a metadata snapshot so it lists offline.
    - **A screen with remove-one and Clear all.**

    The agent proposed three places for the logic. The reviewer chose the first: the Player screen
    drives recording and a domain service writes it; the alternatives were a global recorder
    watching the shared player, or persistence inside `:core:media`. The design was approved in
    three sections.
  - **Plan (superpowers *writing-plans*).** Seven tasks with test-first steps and code, five Review
    Focus risks each tied to a named test (live edge, Back during the read, replaced screens,
    accounts, process death and offline), a ten-step device script, and a six-commit sequence. The
    reviewer approved it and chose inline execution with one fresh review at the end.
  - **Build (superpowers *executing-plans* + *test-driven-development*).** Every piece started with a
    failing test. The first failures were compile errors, so the agent also broke the code on
    purpose for the Review Focus rules (no live guard, no owner check, saving after `stop()`, and
    in the Compose test a Clear all that always shows and a no-op remove) and checked that exactly
    the intended test failed each time. A ledger recorded each task and ruling.
- **Decisions:**
  - **Resume through `setMediaItem(item, startPositionMs)`.** `VideoPlayer.load` gained a
    `startPosition`. The first frame drawn is the resume point, with no 0:00 flash and no extra
    seek, and the offline download item resumes the same way. Live never receives a position
    (guarded in both the ViewModel and `ExoVideoPlayer`), so it joins at the live edge.
  - **The rules live in one pure class.** `WatchHistoryEntry` decides resume (at least 5 s; in the
    last 10 s or past 95% it counts as finished and starts over) and the progress bar.
  - **An ordered write queue on an application scope.** `AccountWatchHistory` sends writes through
    a channel consumed in order, so the save made in `PlayerViewModel.onCleared` lands after
    `viewModelScope` is cancelled, and two saves never land out of order.
  - **Recording moments the screen already knows:** first play, pause, background, end, a 10 s
    checkpoint, up next, and Back, each skipped if the position has not moved.
  - **Rulings during the build** (from the ledger): the device run counts 165 tests, not the plan's
    135, because it also runs every shared common test; the new strings went to the end of the
    file rather than inside the Profile block.
- **Problems found:**
  - On the emulator the first try at Acoustic → up next "Lo-fi radio, live" → Back after 8 s left
    no entry. Two repeats recorded it. The likely cause is that the live stream had not started
    within 8 s, and by design a video that never plays is not recorded; it was not reproduced.
  - The agent's first remove tap in the race check landed on the row's padding, and a tap during
    the Player's exit animation lands on the outgoing Player; both were redone with measured
    coordinates.
- **Verification:**
  - **Tests.** `./gradlew check` green: 269 host tests (domain 48, data 39, shared 155, media 27),
    lint 0 errors and the 4 version notices. `:shared:connectedAndroidDeviceTest` on the emulator:
    166 tests, 0 failures, including the 4 new `WatchHistoryScreenTest` tests.
  - **Emulator (Android 16, Anika):**
    - The empty state with no Clear all and no tab bar; Back returns to Profile.
    - Weekly recap played to 0:39, then reopened from History: the controls read 0:39 from the
      first frame, and the stream's burned-in clock read 00:00:40.56 about 1 s later; a screen
      recording showed no 0:00 frame. Saved at 1:28 by an up-next pick and resumed there.
    - Live: listed with LIVE and no bar, stored with no position; reopened at the live edge (the
      stream's clock 14 s behind the device clock, normal HLS latency).
    - Played to the end: a full bar, and reopening started at 0:00.
    - Process death: backgrounded at 2:06, `am kill`, reopened from Recents in a new process on the
      Player at 2:11.
    - Offline: downloaded Acoustic, played to 0:33, network off; History listed it, and reopening
      logged `Playing download acoustic-one-take (3 stream keys)` and resumed at about 0:33.
    - Remove and clear: the X removed a row at once; the Clear all dialog survived rotation;
      Cancel kept everything; Clear emptied the list and the store.
    - Accounts: after sign-out the guest's history was empty and the guest's Acoustic started at 0
      (Anika had 13.9 s); signing in again as Anika showed only her entry, and she resumed from her
      44 s while the guest's 8.6 s stayed separate.
    - Layout: a 700 dp window showed two columns, a 2400x1800 tablet and phone landscape three.
    - Regression: Shorts kept 2 players after six swipes and never entered the history; one
      started AudioTrack while playing and none after Back; `Activities: 1` after six rotations.
  - **Galaxy A04 (Android 14, 360 dp, abc@gmail.com):** the empty state; Tears of Steel played 22 s
    and resumed offline at 0:19 from its download (`Playing download tears-of-steel (1 stream
    keys)`); dark and light History; an up-next pick added Big Buck Bunny on top and the X removed
    it; `Activities: 1` after six rotations, with no started audio after Back.
- **Fresh review of the whole branch** (one reviewer subagent on Fable). No critical or important
  issues; it confirmed all five Review Focus items, the layering, and the Media3 usage. The agent
  re-graded one minor finding to important and fixed it, proven by tests that failed first:
  - **A video removed right after leaving the Player could come back,** because the Player's last
    saves inserted the entry again. Now only the first play adds a video; later saves call
    `updateProgress`, which `DataStoreWatchHistoryStore.update` applies only if the entry still
    exists, checked inside the same DataStore edit. Five tests (domain, data, and Player) pin it,
    and reverting each layer failed exactly its test. On the emulator, removing a row right after
    Back kept it removed.
  - Six minor findings were deferred to the handoff: the write loop has no per-item isolation,
    a double tap pushes History twice, the progress bar has no TalkBack semantics, hiding the
    Player writes twice, the device test starts with the dialog already shown, and `DataModule`
    uses `Clock.System`.
- **Commits:** `ed901bd`, `208fc0d`, `0585258`, `5debf6b`, `987f68b`, and this docs commit.
