# Streamly Implementation Reference

## Repository layout

This is a Kotlin Multiplatform / Compose Multiplatform project with an Android target only. That is
the "bonus" path in the brief.

- `:androidApp`: the thin Android application module (`MainActivity`, `Application`, manifest) and
  composition root. It assembles Koin modules from UI, data, and media.
- `:shared`: KMP/Compose app UI: navigation, screen ViewModels, feature screens, and resources.
  Put reusable visual components in `:core:designsystem`, not in a generic `core` package.
- `:domain`: KMP library with pure Kotlin models, repository/player contracts, and use cases. Its
  `commonMain` has no Android, Compose, Ktor, DataStore, Media3, or Koin imports.
- `:data`: KMP library implementing network and session repository contracts. Ktor clients and
  mappers can live in `commonMain`; platform persistence belongs in `androidMain`.
- `:core:designsystem`: KMP/Compose library for theme and UI elements reused across screens.
- `:core:media`: Android-target KMP library. Its `androidMain` owns the normal player, Shorts
  player pool, Media3 `DownloadManager`, and a single download cache shared with offline playback.
  It implements media contracts declared in `:domain` and exposes an Android-only Compose video
  surface adapter for `:shared/androidMain`. Keep Media3 types inside this module; pass stable
  identifiers and domain state to common UI rather than a Media3 `Player`.
- Direction: `:androidApp` → `:shared`, `:data`, and `:core:media`; `:shared/commonMain` →
  `:domain` and `:core:designsystem`; `:shared/androidMain` → `:core:media` for the video surface;
  `:data` and `:core:media` → `:domain`. Do not introduce reverse dependencies or a catch-all
  `:core` module.
- Dependency versions live in `gradle/libs.versions.toml`. Add every dependency through the version
  catalog and never hard-code a version in a build script.

Use the same Android target and Android-KMP library plugin pattern for KMP modules as the current
`:shared` module. Put cross-platform dependencies in `commonMain` and Android-only dependencies in
`androidMain`; make each module's public API explicit. A module must earn its boundary with one
clear responsibility. Add feature modules only if the existing graph becomes difficult to navigate.
Do not add iOS targets for this Android-only assignment. The media module must supply playback
with a cache-backed data source using the same download cache as `DownloadManager`; separate
caches would make the offline test invalid.

## Dependency bootstrap for the first slice

The first implementation prompt must set up the module graph above and add the dependencies needed
for the **whole assignment** before feature work begins. Pin compatible versions in
`gradle/libs.versions.toml`, place each library in the module and source set that uses it, resolve
dependencies, and build the app. Include:

- Coroutines/Flow, Kotlin serialization, `kotlin.test`, `kotlinx-coroutines-test`, and Turbine.
- Compose UI/Material 3, lifecycle ViewModel and lifecycle-aware Compose collection,
  `WindowSizeClass`, Android activity integration, and Compose instrumentation-test support.
- Navigation 3 runtime/UI, entry-scoped ViewModel support, and saveable navigation keys.
- Koin core, Compose/ViewModel integration, and Android application bootstrap.
- Ktor client core, an Android engine, JSON serialization/content negotiation, and only the
  additional client components used by the chosen data source.
- DataStore Preferences for session persistence.
- Media3 ExoPlayer, HLS, playback UI, and the download/cache components needed for real offline
  HLS playback.
- A KMP-compatible image loader if feed thumbnails use remote images.

Check current official artifact documentation and version compatibility before choosing versions.
Avoid redundant dependencies. Keep Android-only Media3 in `:core:media/androidMain`; the domain
module stays framework-free. The first prompt must make dependency
resolution and a successful build an explicit acceptance criterion.

## Code conventions

- Follow the official Kotlin code style (`kotlin.code.style=official`).
- `UiState` is an immutable `data class`. Mark it `@Immutable`/`@Stable` where that helps
  recomposition. Use immutable collections in state.
- Split each screen into a `XxxRoot` composable (gets the ViewModel and collects state) and a
  stateless `XxxScreen(state, onIntent)` that can be previewed.
- Model one-off UI events (navigation, snackbars) explicitly, for example with a `Channel`-backed
  event flow. Never put them in `UiState` as flags that need resetting.
- Collect flows in Compose with `collectAsStateWithLifecycle`.
- Use a typed `Result`/error model in the data and domain layers. Map errors to user-facing text in
  presentation. Never let raw exceptions reach the UI.
- Inject coroutine dispatchers. Never hard-code `Dispatchers.IO` inside classes that need tests.
- State must survive rotation and navigation. Use `rememberSaveable` and `SavedStateHandle` where
  they apply, and keep the player outside the composition.
- Keep comments sparse. Explain *why*, not *what*.
