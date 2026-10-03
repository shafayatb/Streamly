package com.shafayatb.streamly.core.presentation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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

/** "1 view", "999 views", "12K views", "1.5M views". */
fun formatViewCount(count: Long): UiText = UiText.PluralResource(
    id = Res.plurals.video_views,
    quantity = count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
    args = listOf(abbreviateCount(count)),
)

/**
 * Shortens [count] the way video apps do: one truncated decimal below ten units ("1.2K"), whole
 * units above ("12K"). Truncating rather than rounding never overstates a count.
 */
fun abbreviateCount(count: Long): String {
    for ((unit, suffix) in CountUnits) {
        if (count >= unit) {
            val tenths = count / (unit / 10)
            return if (tenths < 100 && tenths % 10 != 0L) {
                "${tenths / 10}.${tenths % 10}$suffix"
            } else {
                "${count / unit}$suffix"
            }
        }
    }
    return count.toString()
}

private val CountUnits = listOf(
    1_000_000_000L to "B",
    1_000_000L to "M",
    1_000L to "K",
)

/** "Just now", "5 minutes ago", ..., "2 years ago". A [publishedAt] after [now] reads as just now. */
fun formatAge(publishedAt: Instant, now: Instant): UiText {
    val elapsed = now - publishedAt
    val days = elapsed.inWholeDays
    return when {
        elapsed < 1.minutes -> UiText.Resource(Res.string.age_just_now)
        elapsed < 1.hours -> elapsed.inWholeMinutes.toAge(Res.plurals.age_minutes)
        elapsed < 1.days -> elapsed.inWholeHours.toAge(Res.plurals.age_hours)
        days < 7 -> days.toAge(Res.plurals.age_days)
        days < 30 -> (days / 7).toAge(Res.plurals.age_weeks)
        days < 365 -> (days / 30).toAge(Res.plurals.age_months)
        else -> (days / 365).toAge(Res.plurals.age_years)
    }
}

private fun Long.toAge(id: PluralStringResource): UiText =
    UiText.PluralResource(id = id, quantity = toInt(), args = listOf(toInt()))

/** "0:45", "10:34", "1:02:03". */
fun formatDuration(duration: Duration): String = duration.toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
    } else {
        "$minutes:${seconds.twoDigits()}"
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/** Sizes in decimal units, as Android's storage settings show them: "4.2 MB", "66 MB", "1.2 GB". */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    // Each bound is where the rounded figure would reach the next unit, so "1000 KB" never shows.
    bytes < 999_500 -> "${roundedDiv(bytes, 1_000)} KB"
    bytes < 9_950_000 -> "${oneDecimal(bytes, 1_000_000)} MB"
    bytes < 999_500_000 -> "${roundedDiv(bytes, 1_000_000)} MB"
    else -> "${oneDecimal(bytes, 1_000_000_000)} GB"
}

private fun roundedDiv(value: Long, unit: Long): Long = (value + unit / 2) / unit

private fun oneDecimal(value: Long, unit: Long): String {
    val tenths = roundedDiv(value * 10, unit)
    return "${tenths / 10}.${tenths % 10}"
}
