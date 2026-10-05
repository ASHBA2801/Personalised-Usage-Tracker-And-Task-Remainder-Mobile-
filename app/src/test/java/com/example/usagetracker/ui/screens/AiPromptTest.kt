package com.example.usagetracker.ui.screens

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usagetracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AiPromptTest {
    private val resource get() = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.ai_prompt)

    @Test
    fun resource_matchesSpecTextExactly() {
        assertEquals(File("src/test/resources/ai_prompt_expected.txt").readText().trimEnd('\n'), resource)
    }

    @Test
    fun todayIsFilledIn() {
        val prompt = buildAiPrompt(resource, LocalDate.of(2026, 10, 5))
        assertTrue(prompt.contains("Today's date is 2026-10-05. Never use dates before today."))
        assertFalse(prompt.contains("{TODAY}"))
    }

    @Test
    fun bundledAssets_arePackaged() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        assertEquals(
            File("src/main/assets/templates/tasks_template.json").readText(),
            assets.open("templates/tasks_template.json").bufferedReader().readText(),
        )
        assertTrue(assets.open("templates/tasks_template.docx").readBytes().size > 1000)
    }
}
