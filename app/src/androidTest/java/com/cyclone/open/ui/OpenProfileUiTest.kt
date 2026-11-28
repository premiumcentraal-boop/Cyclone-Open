package com.cyclone.open.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cyclone.open.core.DraftRequest
import com.cyclone.open.core.DraftReviews
import com.cyclone.open.core.ReferenceCatalogReader
import com.cyclone.open.core.Registry
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

    @Test
    fun draftReviewSeparatesReferenceTargetsFromUnavailableRuntimeAtLargeFontScale() {
        val image = ImageRecord(UUID.randomUUID().toString(), "AOSP", 15, "content://test/image", "a".repeat(64), 1)
        val catalog = ReferenceCatalogReader.load(compose.activity)
        val reference = catalog.references.last()
        val request = DraftRequest("Design review", reference.key, image.id, RuntimeKind.LOCAL_VM, 3072)
        val review = DraftReviews.prepare(Registry(images = listOf(image)), catalog, request)
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    OpenGlass.Theme { Column(Modifier.widthIn(max = 320.dp)) { DraftReviewPanel(review) } }
                }
            }
        }
        compose.onNodeWithText("Galaxy S22-class · Virtual reference").assertIsDisplayed()
        compose.onNodeWithText("Local VM · Guest backend unavailable").assertIsDisplayed()
    }

    @Test
    fun runtimeDoctorShowsSeparateBootAndEcosystemGates() {
        compose.onNodeWithText("Health").performClick()
        compose.onNodeWithText("Runtime Doctor").assertIsDisplayed()
        compose.onNodeWithText("Local VM · Blocked").assertExists()
        compose.onNodeWithText("Guest boot · Not run").assertExists()
        compose.onNodeWithText("Google Play licensing · Not run").assertExists()
        compose.onNodeWithText("Certification · Not run").assertExists()
    }
}
