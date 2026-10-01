# Streamly

Streamly is a minimal YouTube-style Android app with long-form HLS videos, vertical HLS shorts,
offline downloads, and a profile with sign-out. It is built with Kotlin Multiplatform and Compose
Multiplatform, with an Android target only.

> **Status:** project setup. The module graph and dependencies are in place, and the app launches
> the starter screen. Features are implemented in later tasks.

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
    shared -. "androidMain only<br/>(video surface)" .-> media
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
| `:core:media` | All Media3 code: the shared normal-video player, the Shorts player pool, `DownloadManager`, and the single download cache that offline playback also reads from. It exposes a Compose video surface to `:shared/androidMain`. |

Dependency rules:

- `:domain` depends on no other module and has no Android, Compose, Ktor, DataStore, Media3, or
  Koin imports.
- Media3 types stay inside `:core:media`. Common UI works with domain state and identifiers, never
  with a Media3 `Player`.
- `:core:media` owns both playback and downloads, so playback and `DownloadManager` share one
  cache. Separate caches would break offline playback.
- Library modules use Kotlin explicit API mode, so every public declaration is a deliberate choice.

### Tech stack

| Concern | Library |
|---|---|
| Language and concurrency | Kotlin 2.4, Coroutines + Flow |
| UI | Compose Multiplatform 1.12, Material 3, Material 3 adaptive (`WindowSizeClass`) |
| Navigation | Navigation 3 with entry-scoped ViewModels |
| Dependency injection | Koin 4.2 |
| Networking | Ktor 3 (OkHttp engine) with kotlinx.serialization |
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

## Shortcuts

None yet. Shortcuts taken during feature work, and the reasons for them, will be listed here.
