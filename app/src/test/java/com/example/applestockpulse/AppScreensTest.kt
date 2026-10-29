package com.example.applestockpulse

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test: every screen opens from the dock without a crash, and an alert group can be added.
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

    private fun openFromDock(label: String) {
        compose.onNodeWithTag("dock_$label").performClick()
        compose.waitForIdle()
    }

    @Test
    fun stockIsHomeAndEveryDockScreenOpens() {
        compose.onNodeWithTag("floating_dock").assertExists()
        compose.onNodeWithTag("dock_Stock").assertIsSelected()
        compose.onNodeWithText("Recent changes").assertExists()
        compose.onNodeWithText("iPhone Duo").assertExists()

        compose.onNodeWithText("Recent changes").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No changes yet").assertExists()

        compose.onNodeWithText("Patterns").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not enough data yet").assertExists()

        openFromDock("Alerts")
        compose.onNodeWithTag("dock_Alerts").assertIsSelected()
        compose.onNodeWithText("No alerts yet").assertExists()

        // Semantic click: a touch at the list's bottom edge would land on the floating dock
        compose.onAllNodesWithText("Add group")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Watch devices").assertExists()
        compose.onNodeWithTag("save_rule_button").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(1, WatchRuleStore.get(context).rules.value.size)
        compose.onNodeWithText("Your alerts (1)".uppercase()).assertExists()

        openFromDock("Settings")
        compose.onNodeWithText("Save location").assertExists()

        openFromDock("Help")
        compose.onNodeWithText("Restock times").assertExists()

        openFromDock("Stock")
        compose.onNodeWithTag("dock_Stock").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp")
    fun wideScreenUsesTheSameDock() {
        compose.onNodeWithTag("floating_dock").assertIsDisplayed()
        openFromDock("Help")
        compose.onNodeWithText("Restock times").assertIsDisplayed()
    }
}
