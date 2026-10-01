# Streamly Verification Reference

## Commands

```bash
./gradlew :androidApp:assembleDebug        # build the debug APK
./gradlew :androidApp:installDebug         # install on a connected device or emulator
./gradlew :shared:testAndroidHostTest      # shared unit tests (host)
./gradlew check                            # all verification tasks
```

Before you claim a change works, run the build and the relevant tests. Report failures honestly and
include the output. After adding `:domain`, `:data`, and `:core:media`, run their host tests as
well as the `:shared` tests; confirm the exact task names from Gradle.

## Testing

- Unit-test ViewModels (intent in, state out), use cases, repositories, and mappers. Use fakes
  rather than mocks.
- Use `kotlinx-coroutines-test` with a test dispatcher, and Turbine for flows.
- Test where it matters: state reduction, session persistence, download state mapping, and the
  player-pool policy.
- **After every implementation task**, install the current debug build on a connected Android
  device or emulator and exercise the changed user journey from app launch through its visible
  result. Also smoke-test the previously completed core journey that the change could affect.
  Automated Compose/instrumentation tests should cover stable navigation and state flows; they
  complement, rather than replace, an actual device run.
- Check real HLS playback, controls, buffering, Shorts swipes, background/return, rotation, and
  cross-screen audio behavior on a device as those features arrive. For downloads, verify actual
  progress, completion, playback with connectivity off, and removal. Keep a short test record with
  device/emulator, Android version, steps, result, and any logs or screenshots needed to reproduce
  a failure.
- Documentation-only changes do not require reinstalling an unchanged app. Before submission,
  perform a full on-device end-to-end pass covering all seven reference views and record the demo
  from that verified build.
