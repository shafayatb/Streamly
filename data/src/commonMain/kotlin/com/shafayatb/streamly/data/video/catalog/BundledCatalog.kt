package com.shafayatb.streamly.data.video.catalog

/**
 * The catalog the mock API serves, in the same JSON shape a real `GET /videos` would return.
 * Titles, channels, and counts are demo metadata; every `hlsUrl` is a public HLS test stream and
 * every `thumbnailUrl` a public image, both checked to respond when they were added.
 */
internal object BundledCatalog {
    val JSON: String = """
    {
      "videos": [
        {
          "id": "big-buck-bunny",
          "title": "Big Buck Bunny",
          "description": "A giant rabbit takes on three bullying rodents in Blender Studio's classic open movie.",
          "channel": { "id": "blender-studio", "name": "Blender Studio" },
          "thumbnailUrl": "https://peach.blender.org/wp-content/uploads/bbb-splash.png",
          "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
          "durationSeconds": 634,
          "viewCount": 1284000,
          "publishedAt": "2026-06-14T10:00:00Z",
          "category": "film"
        },
        {
          "id": "tears-of-steel",
          "title": "Tears of Steel",
          "description": "Warriors and scientists make a last stand in Amsterdam to save the world from destructive robots.",
          "channel": { "id": "blender-studio", "name": "Blender Studio" },
          "thumbnailUrl": "https://mango.blender.org/wp-content/gallery/4k-renders/01_thom_celia_bridge.jpg",
          "hlsUrl": "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
          "durationSeconds": 734,
          "viewCount": 862000,
          "publishedAt": "2026-08-02T18:00:00Z",
          "category": "film"
        },
        {
          "id": "weekly-recap",
          "title": "Weekly recap: what shipped",
          "description": "Everything that landed this week, from new player controls to a faster home feed.",
          "channel": { "id": "dev-channel", "name": "DevChannel" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
          "durationSeconds": 600,
          "viewCount": 12400,
          "publishedAt": "2026-09-28T16:30:00Z",
          "category": "tech"
        },
        {
          "id": "media3-in-10-minutes",
          "title": "Media3 in 10 minutes",
          "description": "A quick tour of ExoPlayer, HLS playback, and offline downloads with Jetpack Media3.",
          "channel": { "id": "codelabs", "name": "CodeLabs" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1518770660439-4636190af475?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
          "durationSeconds": 600,
          "viewCount": 44100,
          "publishedAt": "2026-09-21T09:00:00Z",
          "category": "tech"
        },
        {
          "id": "adaptive-streaming-explained",
          "title": "Adaptive streaming, explained",
          "description": "How HLS switches between renditions as your bandwidth changes, shown on Apple's reference stream.",
          "channel": { "id": "codelabs", "name": "CodeLabs" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
          "durationSeconds": 1800,
          "viewCount": 98700,
          "publishedAt": "2026-07-09T12:00:00Z",
          "category": "tech"
        },
        {
          "id": "synthwave-session",
          "title": "Late-night synthwave session",
          "description": "Six minutes of retro synths and neon basslines, recorded in one evening.",
          "channel": { "id": "neon-hours", "name": "Neon Hours" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://storage.googleapis.com/shaka-demo-assets/bbb-dark-truths-hls/hls.m3u8",
          "durationSeconds": 372,
          "viewCount": 230500,
          "publishedAt": "2026-09-12T21:00:00Z",
          "category": "music"
        },
        {
          "id": "acoustic-one-take",
          "title": "Acoustic sessions: one take",
          "description": "A single-take acoustic performance, recorded live in the studio.",
          "channel": { "id": "studio-nine", "name": "Studio Nine" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
          "durationSeconds": 60,
          "viewCount": 5820,
          "publishedAt": "2026-09-30T20:00:00Z",
          "category": "music"
        },
        {
          "id": "festival-highlights",
          "title": "Festival highlights",
          "description": "The best moments from this summer's open-air stages.",
          "channel": { "id": "crowd-pulse", "name": "Crowd Pulse" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://test-streams.mux.dev/pts_shift/master.m3u8",
          "durationSeconds": 165,
          "viewCount": 3410000,
          "publishedAt": "2026-03-18T15:00:00Z",
          "category": "music"
        },
        {
          "id": "lofi-radio-live",
          "title": "Lo-fi radio, live",
          "description": "Beats to study and code to, streaming around the clock.",
          "channel": { "id": "neon-hours", "name": "Neon Hours" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://storage.googleapis.com/shaka-live-assets/player-source.m3u8",
          "viewCount": 1920,
          "publishedAt": "2026-09-30T06:00:00Z",
          "category": "music"
        },
        {
          "id": "studio-feed-live",
          "title": "Streamly studio feed",
          "description": "A round-the-clock test broadcast from the Streamly studio.",
          "channel": { "id": "streamly-live", "name": "Streamly Live" },
          "thumbnailUrl": "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=960&h=540&fit=crop&q=75",
          "hlsUrl": "https://demo.unified-streaming.com/k8s/live/stable/live.isml/.m3u8",
          "viewCount": 870,
          "publishedAt": "2026-10-01T06:00:00Z",
          "category": "tech"
        }
      ]
    }
    """.trimIndent()
}
