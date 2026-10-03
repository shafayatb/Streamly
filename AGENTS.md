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

Submission deadline: **Sunday, October 4, 2026, 11:59 PM**, confirmed by the hiring team's email.
(The brief itself was inconsistent: 3 days from receipt on its cover, 6 on its timeline page.)
The reply needs the GitHub repository link, the README, a demo video link, and an APK or build
instructions.

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

- **Plan before code, log after.** For every main feature task, even when `FRESH_PROMPT.md`
  reads like a full spec: settle open design questions with the user before writing code
  (superpowers `brainstorming`), save the agreed plan as `docs/plans/YYYY-MM-DD-<task>.md` and
  show it before implementing (`writing-plans`), and build test-first
  (`test-driven-development`). Before staging, append the task's entry to `docs/agent-log.md`:
  prompt, how you worked, decisions and why, problems found, verification evidence, and commits.
  Never decide a user-visible trade-off silently.
- After every implementation task, install and exercise the changed journey on an Android device
  or emulator. Run the build and relevant tests. Record the device, steps, and result. If a check
  cannot run, report it as unverified; do not claim that task is complete.
- **Never commit or merge without the user's explicit go-ahead.** When a change is ready, stage
  it, show `git status` and a summary of `git diff --cached`, propose the commit message(s), and
  stop. The user reviews the diff against these rules first. Approval covers only the commits or
  merge it names; ask again for the next one. Commit messages follow Conventional Commits
  (`feat(player): …`, `fix(downloads): …`); see the delivery guide.
- Use Gitflow: each main feature task (a `FRESH_PROMPT.md` task such as onboarding, the feed, or
  the player) gets a `feature/<task-name>` branch from `develop` that merges back after
  verification. Small changes such as docs, agent rules, or config tweaks are committed directly
  on `develop`, still subject to the approval rule; do not create a branch for them.
  There are no release or hotfix branches (the brief does not ask for them): the final gate runs on
  `develop`, and the verified `develop` merges into `main` with `--no-ff`. Push or create a remote
  PR only when the user asks.
- **Never delete branches**, local or remote, including merged `feature/*` branches. Keep them
  after merging so the branch history stays visible.
- At each task boundary, update the local `FRESH_PROMPT.md` with the next task and evidence, then
  stop with a handoff. Keep the README current. Do not commit secrets or the gitignored brief.
