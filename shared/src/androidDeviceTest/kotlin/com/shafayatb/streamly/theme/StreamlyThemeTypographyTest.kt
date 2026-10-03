package com.shafayatb.streamly.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StreamlyThemeTypographyTest {

    @get:Rule
    val rule = createComposeRule()

    private fun typography(): Typography {
        lateinit var typography: Typography
        rule.setContent { StreamlyTheme { typography = MaterialTheme.typography } }
        rule.waitForIdle()
        return typography
    }

    @Test
    fun everyTextStyleUsesBalooDa2() {
        val styles = with(typography()) {
            mapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium,
                "displaySmall" to displaySmall, "headlineLarge" to headlineLarge,
                "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
            )
        }
        val families = styles.mapValues { it.value.fontFamily }
        val family = families.getValue("bodyLarge")

        assertTrue("No bundled font family: $families", family is FontListFontFamily)
        assertEquals(
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
            (family as FontListFontFamily).fonts.map { it.weight },
        )
        assertEquals(styles.keys.associateWith { family }, families)
    }

    @Test
    fun headingWeightsAreKept() {
        with(typography()) {
            assertEquals(FontWeight.Bold, displaySmall.fontWeight)
            assertEquals(FontWeight.Bold, headlineLarge.fontWeight)
            assertEquals(FontWeight.Bold, headlineMedium.fontWeight)
            assertEquals(FontWeight.Bold, headlineSmall.fontWeight)
            assertEquals(FontWeight.Bold, titleLarge.fontWeight)
            assertEquals(FontWeight.SemiBold, titleMedium.fontWeight)
            assertEquals(FontWeight.SemiBold, labelLarge.fontWeight)
        }
    }
}
