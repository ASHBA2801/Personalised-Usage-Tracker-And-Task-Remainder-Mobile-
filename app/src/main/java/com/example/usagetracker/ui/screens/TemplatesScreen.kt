package com.example.usagetracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.usagetracker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Fills in today's date (yyyy-MM-dd) so the AI never schedules tasks in the past. */
fun buildAiPrompt(template: String, today: LocalDate): String = template.replace("{TODAY}", today.toString())

@Composable
fun TemplatesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun save(asset: String, uri: Uri?) {
        uri ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { copyAsset(context, asset, uri) }
            Toast.makeText(context, if (ok) "Template saved" else "Couldn't save the template", Toast.LENGTH_SHORT).show()
        }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        save("templates/tasks_template.json", it)
    }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) {
        save("templates/tasks_template.docx", it)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Templates and AI prompt", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plan many tasks at once: fill in a template (or let an AI assistant write one), then tap Import on the Focus screen.",
            style = MaterialTheme.typography.bodyLarge,
        )
        ActionCard(
            title = "JSON file",
            body = "A list of tasks. Only \"title\" is required. Optional: date (YYYY-MM-DD), deadline " +
                "(YYYY-MM-DD or YYYY-MM-DD HH:mm), priority (high, medium, low), estimated_minutes, tags, notes and subtasks.",
            button = "Save JSON template",
            onClick = { saveJson.launch("tasks_template.json") },
        )
        ActionCard(
            title = "Word file (.docx)",
            body = "A table with one task per row and the columns Task, Date, Deadline, Priority, Estimated Minutes, Tags, " +
                "Notes and Sub-tasks. Only Task is required. Put each sub-task on its own line; start it with [x] if it's done.",
            button = "Save Word template",
            onClick = { saveDocx.launch("tasks_template.docx") },
        )
        ActionCard(
            title = "Ask an AI assistant",
            body = "Copies a prompt that asks an AI chat app to turn your plan into the JSON format. Paste it there, " +
                "replace the last line with your plan, save the answer as a .json file and import it.",
            button = "Copy AI prompt",
            onClick = {
                val prompt = buildAiPrompt(context.getString(R.string.ai_prompt), LocalDate.now())
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AI prompt", prompt))
                // Android 13+ shows its own "copied" confirmation.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "AI prompt copied", Toast.LENGTH_SHORT).show()
                }
            },
        )
        Text(
            "Dates before today are moved to today. Files are read once on this device and are not stored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

private fun copyAsset(context: Context, asset: String, target: Uri): Boolean = try {
    context.assets.open(asset).use { input ->
        context.contentResolver.openOutputStream(target, "wt")?.use { input.copyTo(it) } ?: return false
    }
    true
} catch (_: Exception) {
    false
}
