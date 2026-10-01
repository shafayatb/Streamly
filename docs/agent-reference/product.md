# Streamly Product Reference

## Screens

There are seven screens. The mockups in the brief show intent: match the layout, hierarchy, and
states, not the exact pixels.

1. **Onboarding**: sign in with email or a mocked social action, plus Continue as guest. The mockup
   shows both email and Google, so implement both if time permits. Persist the session so returning
   users skip onboarding.
2. **Home feed**: category chips (static is fine), then video cards with thumbnail, title, channel,
   and views/age. Tapping a card opens the Player.
3. **Shorts**: a full-screen vertical pager that autoplays the visible item only. Like, comment, and
   share can be stubs.
4. **Player**: a 16:9 player with controls, metadata below it, a Download action, and an up-next list.
5. **Downloads**: in-progress and completed items with real progress. Completed items play offline,
   and items can be removed.
6. **Profile**: avatar, name, and email, plus links to Downloads, History, and Settings, and Sign out.
7. **Sign-out confirmation**: a dialog. Confirming clears the session and returns to Onboarding.

Provide meaningful **loading, empty, and error states** wherever the screen can enter those states;
the sign-out dialog does not need artificial loading or empty views.

## Media3 rules

- **Normal-video player:** use one shared `ExoPlayer` instance. It must survive config changes and be
  released or reused correctly across navigation.
- **Shorts:** use a separate pool of players. Only the visible short decodes, and **at most 1–2
  players** exist at once. If you use a different strategy, document it in the README.
- **Lifecycle:** pause when the app goes to the background, resume only when the playback screen is
  still active, and release or return players to their owner on screen exit. The normal-video
  instance may be retained for reuse across navigation, but it must stop and detach when leaving
  the player screen. Players must never leak, and audio must never bleed between screens.
- **HLS is the required format** for videos and shorts. Use `media3-exoplayer-hls` with public test
  `.m3u8` streams (for example Mux or Apple samples). Adaptive bitrate must work. No MP4-only shortcuts.
- **Downloads:** use Media3 `DownloadManager` / the offline module. Progress must be real, playback
  must come from local storage, and users must be able to remove a download. Never show a fake
  progress bar.
- **Controls:** play/pause, seek/scrub, mute, and a visible buffering state.
- Media3 is Android-only. Its player, download manager, shared cache, and video surface adapter
  live in `:core:media/androidMain` behind domain interfaces. Never expose Media3 types to the
  domain layer or common UI.
