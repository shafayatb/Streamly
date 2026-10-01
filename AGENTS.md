# AGENTS.md

This is the repo's single agent-rules entry point. `CLAUDE.md` and any other tool-specific
rule files must be symlinks to this file. Edit this file, never a symlink target, and do not add
separate per-tool rules. The linked docs below are reference material, not additional agent configs.

## Project

**Streamly** is a minimal YouTube-style video app, built as a take-home assignment (brief:
`Test_Android_Developer_Shikho.pdf`, gitignored). It has long-form HLS videos, vertical HLS shorts,
offline downloads, and a profile with sign-out. Treat it as a small production app, not a UI clone.
Network data may be faked, but the architecture, the player lifecycle, and the code quality must be real.

Grading weights: Media3 usage 30%, Architecture 25%, Compose & state 20%, Code quality 10%,
Polish 10%, AI-first workflow 5%. When choosing where to spend effort, follow these weights.

Working delivery target: **October 4, 2026**, as supplied by the project owner. The brief is
internally inconsistent: its cover says **3 days from receipt**, while its timeline page says
**6 days from receipt**. Confirm the actual submission time with the hiring team; plan against
October 4 until told otherwise.

## Hard constraints (from the brief, non-negotiable)

- **Kotlin only.** Use Coroutines + Flow for concurrency. No RxJava.
- **Jetpack Compose for all UI.** No XML layouts and no Fragments.
- **MVVM + MVI.** Each screen has one `ViewModel`. It exposes a single immutable `UiState` through
  `StateFlow` and accepts one sealed `Intent` (action) type. State flows down and intents flow up.
  The UI never calls the data layer directly.
- **Clean architecture.** Separate presentation, domain, and data. The domain layer has **no Android
  or framework imports**: pure Kotlin only.
- **DI with Koin.** The brief allows Hilt or Koin; Koin is this project's choice.
- **Adaptive layouts from the start.** Use `WindowSizeClass`. Never assume a fixed width, and make
  every screen work on phones, foldables, and tablets.
- **Navigation 3 (Nav3)** for all navigation.
- **Ktor** for all HTTP, whether the endpoint is mocked or real.
- **Media3 (ExoPlayer) is the only media stack.** Don't use VideoView, MediaPlayer, or third-party players.

## Read the relevant reference before work

- [Product requirements](docs/agent-reference/product.md): read before implementing a screen,
  player, Shorts, or offline downloads. It contains the brief-derived acceptance details.
- [Implementation guide](docs/agent-reference/implementation.md): read before changing the module
  graph, adding dependencies, or writing architecture and UI code.
- [Verification guide](docs/agent-reference/verification.md): read before implementing or checking
  any app behavior. It contains the build, automated-test, and device-test procedure.
- [Delivery guide](docs/agent-reference/delivery.md): read when starting or finishing a task,
  changing branches, updating `FRESH_PROMPT.md`, or preparing the submission.

## Always-on delivery gates

- After every implementation task, install and exercise the changed journey on an Android device
  or emulator. Run the build and relevant tests. Record the device, steps, and result. If a check
  cannot run, report it as unverified; do not claim that task is complete.
- Use Gitflow: `feature/<task-name>` branches from `develop` and merges back after verification.
  Use `release/<version>` for the final gate, then merge to `main` and back to `develop`. Push or
  create a remote PR only when the user asks.
- At each task boundary, update the local `FRESH_PROMPT.md` with the next task and evidence, then
  stop with a handoff. Keep the README current. Do not commit secrets or the gitignored brief.
