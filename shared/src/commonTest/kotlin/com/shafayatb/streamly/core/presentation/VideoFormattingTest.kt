package com.shafayatb.streamly.core.presentation

import com.shafayatb.streamly.home.toCardUi
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.jetbrains.compose.resources.PluralStringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.age_days
import streamly.shared.generated.resources.age_hours
import streamly.shared.generated.resources.age_just_now
import streamly.shared.generated.resources.age_minutes
import streamly.shared.generated.resources.age_months
import streamly.shared.generated.resources.age_weeks
import streamly.shared.generated.resources.age_years
import streamly.shared.generated.resources.video_views

class VideoFormattingTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun countsBelowAThousandAreExact() {
        assertEquals("0", abbreviateCount(0))
        assertEquals("1", abbreviateCount(1))
        assertEquals("999", abbreviateCount(999))
    }

    @Test
    fun countsAreAbbreviatedWithOneTruncatedDecimalBelowTenUnits() {
        assertEquals("1K", abbreviateCount(1_000))
        assertEquals("1.2K", abbreviateCount(1_250))
        assertEquals("1.9K", abbreviateCount(1_999))
        assertEquals("12K", abbreviateCount(12_400))
        assertEquals("999K", abbreviateCount(999_999))
        assertEquals("1M", abbreviateCount(1_000_000))
        assertEquals("1.2M", abbreviateCount(1_284_000))
        assertEquals("34M", abbreviateCount(34_100_000))
        assertEquals("2B", abbreviateCount(2_000_000_000))
    }

    @Test
    fun viewCountUsesThePluralForTheExactCount() {
        assertEquals(UiText.PluralResource(Res.plurals.video_views, 1, listOf("1")), formatViewCount(1))
        assertEquals(UiText.PluralResource(Res.plurals.video_views, 12_400, listOf("12K")), formatViewCount(12_400))
    }

    @Test
    fun ageIsBucketedFromJustNowToYears() {
        assertEquals(UiText.Resource(Res.string.age_just_now), ageOf(30.seconds))
        assertEquals(plural(Res.plurals.age_minutes, 1), ageOf(1.minutes))
        assertEquals(plural(Res.plurals.age_minutes, 59), ageOf(59.minutes + 59.seconds))
        assertEquals(plural(Res.plurals.age_hours, 1), ageOf(1.hours))
        assertEquals(plural(Res.plurals.age_hours, 23), ageOf(23.hours))
        assertEquals(plural(Res.plurals.age_days, 1), ageOf(1.days))
        assertEquals(plural(Res.plurals.age_days, 6), ageOf(6.days))
        assertEquals(plural(Res.plurals.age_weeks, 1), ageOf(7.days))
        assertEquals(plural(Res.plurals.age_weeks, 4), ageOf(29.days))
        assertEquals(plural(Res.plurals.age_months, 1), ageOf(30.days))
        assertEquals(plural(Res.plurals.age_months, 12), ageOf(364.days))
        assertEquals(plural(Res.plurals.age_years, 1), ageOf(365.days))
        assertEquals(plural(Res.plurals.age_years, 3), ageOf(1_100.days))
    }

    @Test
    fun publishTimeInTheFutureReadsAsJustNow() {
        assertEquals(UiText.Resource(Res.string.age_just_now), ageOf(-(2.hours)))
    }

    @Test
    fun durationIsMinutesAndSecondsOrHoursWhenLong() {
        assertEquals("0:05", formatDuration(5.seconds))
        assertEquals("1:00", formatDuration(60.seconds))
        assertEquals("10:34", formatDuration(634.seconds))
        assertEquals("30:00", formatDuration(1_800.seconds))
        assertEquals("1:02:03", formatDuration(1.hours + 2.minutes + 3.seconds))
    }

    @Test
    fun cardUiCombinesTheFormattedFields() {
        val video = testVideo(
            id = "media3",
            title = "Media3 in 10 minutes",
            channelName = "CodeLabs",
            duration = 600.seconds,
            viewCount = 44_100,
            publishedAt = now - 10.days,
        )

        val card = video.toCardUi(now)

        assertEquals("media3", card.id)
        assertEquals("Media3 in 10 minutes", card.title)
        assertEquals("CodeLabs", card.channelName)
        assertEquals("https://example.com/media3.jpg", card.thumbnailUrl)
        assertEquals(UiText.PluralResource(Res.plurals.video_views, 44_100, listOf("44K")), card.views)
        assertEquals(plural(Res.plurals.age_weeks, 1), card.age)
        assertEquals("10:00", card.duration)
    }

    @Test
    fun liveCardHasNoDuration() {
        val card = testVideo(id = "radio", duration = null).toCardUi(now)

        assertNull(card.duration)
        assertTrue(card.isLive)
    }

    private fun ageOf(elapsed: Duration): UiText = formatAge(publishedAt = now - elapsed, now = now)

    private fun plural(id: PluralStringResource, count: Int) = UiText.PluralResource(id, count, listOf(count))

    @Test
    fun formatsByteCountsInDecimalUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("66 KB", formatBytes(65_900))
        assertEquals("4.2 MB", formatBytes(4_210_000))
        assertEquals("66 MB", formatBytes(65_800_000))
        assertEquals("1.2 GB", formatBytes(1_234_000_000))
        assertEquals("31.0 GB", formatBytes(31_000_000_000))
    }
}
