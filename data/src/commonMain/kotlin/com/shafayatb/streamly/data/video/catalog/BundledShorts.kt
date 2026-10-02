package com.shafayatb.streamly.data.video.catalog

/**
 * The Shorts catalog the mock API serves, in the shape a real `GET /shorts` would return. Titles,
 * channels, and counts are demo metadata. Every `hlsUrl` is a public, genuinely vertical
 * (720x1280) HLS stream from TheWidlarzGroup's open-source video-feed demo, checked to respond
 * when it was added.
 */
internal object BundledShorts {
    private const val HOST = "https://69e664383f19480597c5a256--twg-video-feed-demo.netlify.app"

    val JSON: String = """
    {
      "shorts": [
        {
          "id": "night-mode-home-screen",
          "title": "This home screen setup is unreal",
          "channel": { "id": "pixellab", "name": "Pixel Lab" },
          "hlsUrl": "$HOST/v0/index.m3u8",
          "likeCount": 48200,
          "commentCount": 1310
        },
        {
          "id": "film-the-match",
          "title": "Film the match like a pro",
          "channel": { "id": "matchdayclips", "name": "Matchday Clips" },
          "hlsUrl": "$HOST/v1/index.m3u8",
          "likeCount": 12900,
          "commentCount": 402
        },
        {
          "id": "meadow-bunny",
          "title": "Meet the meadow bunny",
          "channel": { "id": "toongarden", "name": "Toon Garden" },
          "hlsUrl": "$HOST/v2/index.m3u8",
          "likeCount": 231000,
          "commentCount": 5800
        },
        {
          "id": "studio-dance-break",
          "title": "Studio dance break",
          "channel": { "id": "movecrew", "name": "Move Crew" },
          "hlsUrl": "$HOST/v3/index.m3u8",
          "likeCount": 87400,
          "commentCount": 2210
        },
        {
          "id": "wireless-charging-visualized",
          "title": "Wireless charging, visualized",
          "channel": { "id": "techbites", "name": "Tech Bites" },
          "hlsUrl": "$HOST/v4/index.m3u8",
          "likeCount": 9100,
          "commentCount": 188
        },
        {
          "id": "pair-programming",
          "title": "Pair programming in 8 seconds",
          "channel": { "id": "devchannel", "name": "DevChannel" },
          "hlsUrl": "$HOST/v6/index.m3u8",
          "likeCount": 3400,
          "commentCount": 96
        },
        {
          "id": "late-night-coding",
          "title": "Late-night coding vibes",
          "channel": { "id": "codelabs", "name": "CodeLabs" },
          "hlsUrl": "$HOST/v8/index.m3u8",
          "likeCount": 15700,
          "commentCount": 530
        },
        {
          "id": "video-in-ar",
          "title": "Video, reimagined in AR",
          "channel": { "id": "streamlylabs", "name": "Streamly Labs" },
          "hlsUrl": "$HOST/v9/index.m3u8",
          "likeCount": 6200,
          "commentCount": 141
        }
      ]
    }
    """.trimIndent()
}
