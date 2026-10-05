package com.example.usagetracker.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.ui.theme.UsageTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real Focus Tasks screen (Room on Robolectric's file system) through add -> list -> tick. */
@RunWith(AndroidJUnit4::class)
class FocusTasksUiTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun clean() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { AppDatabase.getInstance(ctx).focusTaskDao().deleteAll() }
    }

    @Test
    fun addTaskWithTwoSubTasksAndDeadline_showsMeterChipAndProgress() {
        compose.setContent {
            UsageTrackerTheme {
                FocusTasksScreen(onImport = {}, onOpenTemplates = {}, importResult = null, onImportResultShown = {}, onUndoImport = {})
            }
        }
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithTag(TaskSheetTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(TaskSheetTags.TITLE).performTextInput("Write report")
        compose.onNodeWithTag(TaskSheetTags.SAVE).assertIsEnabled()

        compose.onNodeWithText("Tomorrow 9 AM").performClick() // quick deadline chip
        compose.onNodeWithTag(TaskSheetTags.ADD_SUBTASK).performScrollTo().performClick()
        compose.onNodeWithTag(TaskSheetTags.subTask(0)).performTextInput("Outline")
        compose.onNodeWithTag(TaskSheetTags.ADD_SUBTASK).performScrollTo().performClick()
        compose.onNodeWithTag(TaskSheetTags.subTask(1)).performTextInput("Draft")
        compose.onNodeWithTag(TaskSheetTags.SAVE).performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Write report")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tomorrow 9:00 AM").assertExists() // deadline chip, 12-hour clock
        compose.onNodeWithText("0/2").assertExists()
        compose.onNodeWithTag("subtask_progress").assertExists()
        compose.onNodeWithText("0 of 1 task done").assertExists()
        compose.onNodeWithText("0%").assertExists()

        // Expand, tick both sub-tasks: the row says 2/2 but the parent (and the day card) stay open.
        compose.onNodeWithText("Write report").performClick()
        compose.onNodeWithText("Outline").assertExists()
        compose.onNodeWithText("Draft").assertExists()

        val boxes = compose.onAllNodes(isToggleable())
        boxes[1].performClick()
        boxes[2].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("2/2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0 of 1 task done").assertExists() // finishing every step doesn't complete the task

        compose.onAllNodes(isToggleable())[0].performClick() // complete the parent
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("All done for today")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 of 1 task done").assertExists()
        compose.onNodeWithText("100%").assertExists()
    }
}
