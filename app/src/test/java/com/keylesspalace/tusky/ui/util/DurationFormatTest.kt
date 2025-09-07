package com.keylesspalace.tusky.ui.util

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class DurationFormatTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    companion object {
        /** Default locale before this test started */
        private lateinit var locale: Locale

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            locale = Locale.getDefault()
            Locale.setDefault(Locale.ENGLISH)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            Locale.setDefault(locale)
        }
    }

    @Test
    fun testDurationFormat() {
        composeTestRule.setContent {
            assertEquals("10 seconds", 10.formatDuration())
            assertEquals("1 hour", 3600.formatDuration())
            assertEquals("2 hours", 7200.formatDuration())
            assertEquals("2 hours 8 seconds", 7208.formatDuration())
            assertEquals("2 hours 1 minute 8 seconds", 7268.formatDuration())
            assertEquals("1 day", 86400.formatDuration())
            assertEquals("365 days", 31536000.formatDuration())
            assertEquals("365 days 17 hours 8 minutes 5 seconds", 31597685.formatDuration())
            assertEquals("1,312 days 9 hours 5 seconds", 113389205.formatDuration())
        }
    }
}
