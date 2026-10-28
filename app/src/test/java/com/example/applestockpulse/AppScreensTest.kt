package com.example.applestockpulse

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test: every screen opens without a crash, and a device group can be added.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearRules() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = WatchRuleStore.get(context)
        store.rules.value.forEach { store.delete(it.id) }
    }

    private fun openFromMenu(label: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(label)[0].performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun addDeviceGroupAndVisitEveryScreen() {
        compose.onNodeWithText("Watch your first device").assertExists()

        compose.onAllNodesWithText("Add device")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Watch devices").assertExists()
        compose.onNodeWithTag("save_rule_button").performScrollTo().performClick()
        compose.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(1, WatchRuleStore.get(context).rules.value.size)
        compose.onNodeWithText("Your device groups (1)".uppercase()).assertExists()

        openFromMenu("Browse")
        compose.onAllNodesWithText("Tap a cell for details", substring = true)[0].assertIsDisplayed()

        openFromMenu("Recent changes")
        compose.onNodeWithText("No changes yet").assertIsDisplayed()

        openFromMenu("Patterns")
        compose.onNodeWithText("Not enough data yet").assertIsDisplayed()

        openFromMenu("Settings")
        compose.onNodeWithText("Save location").assertExists()

        openFromMenu("Help")
        compose.onNodeWithText("How it works").assertIsDisplayed()

        openFromMenu("Watching")
        compose.onNodeWithText("Your device groups (1)".uppercase()).assertExists()
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp")
    fun wideScreenShowsPermanentMenu() {
        // No menu button: the menu is always visible on wide screens such as an unfolded Fold
        compose.onAllNodesWithText("Recent changes")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Open menu").assertDoesNotExist()
        compose.onAllNodesWithText("Patterns")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not enough data yet").assertIsDisplayed()
    }
}
