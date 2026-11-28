package com.cyclone.open.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cyclone.open.MainActivity
import com.cyclone.open.core.ImageRecord
import com.cyclone.open.core.PhoneRecord
import com.cyclone.open.core.RuntimeKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class OpenProfileUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun auroraExposesAndroidHomeFromTheRealApp() {
        compose.onNodeWithText("Aurora").performClick()
        compose.onNodeWithText("Return to Android Home").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Return to Android Home").assertDoesNotExist()
    }

    @Test
    fun navigationSelectionSurvivesActivityRecreation() {
        compose.onNodeWithText("Health", ignoreCase = false).performClick()
        compose.onNodeWithText("Node health").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Node health").assertIsDisplayed()
    }

    @Test
    fun selectedDraftAndActionsHaveAccessibilitySemanticsWithReducedMotion() {
        val renamed = AtomicBoolean()
        val phone = PhoneRecord(UUID.randomUUID().toString(), "Harbor", RuntimeKind.LOCAL_VM, "image", 3072)
        compose.runOnUiThread {
            compose.activity.setContent {
                OpenGlass.Theme {
                    CompositionLocalProvider(LocalReducedMotion provides true) {
                        PhoneProfileCard(phone, 15, true, false, {}, { renamed.set(true) }, {})
                    }
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected draft"))
            .assertIsSelected()
        compose.onNodeWithText("Local Android guest backend not implemented. Pixel AVF and graphics require verification.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Rename Harbor").performClick()
        assertTrue(renamed.get())
    }

    @Test
    fun imageSearchFindsAndSelectsReferencesBeyondTheFirstEight() {
        val images = (1..16).map { number ->
            val id = UUID.randomUUID().toString()
            ImageRecord(id, "Image $number", 15, "content://test/$id", "a".repeat(64), 1)
        }
        compose.runOnUiThread {
            compose.activity.setContent {
                var query by remember { mutableStateOf("") }
                var selection by remember { mutableStateOf(images.first().id) }
                OpenGlass.Theme {
                    Column { DraftImagePicker(images, query, selection, true, { query = it }, { selection = it }) }
                }
            }
        }
        compose.onNodeWithText("Search images").performTextInput("Image 16")
        compose.onNode(hasText("Image 16") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .performClick().assertIsSelected()
    }
}
