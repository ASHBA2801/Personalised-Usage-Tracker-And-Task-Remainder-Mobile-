package com.example.usagetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.usagetracker.data.Category
import com.example.usagetracker.data.DefaultCategoryRules
import com.example.usagetracker.service.ContentTag

private val CHOICES = listOf(
    Category.USEFUL to "Useful",
    Category.LOW_VALUE to "Low-value",
    Category.UNCATEGORIZED to "Uncategorized",
)

private val YOUTUBE_ROWS = listOf(
    ContentTag.SHORTS to "Shorts",
    ContentTag.VIDEO to "Video",
    ContentTag.OTHER to "Other",
)

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = state.resolver

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Categories", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(
                "Choose how each app counts on the Home screen. Changes also apply to past usage.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (resolver != null && state.apps.isEmpty()) {
            item { Text("No apps tracked yet. They will appear here once usage is recorded.") }
        }
        if (resolver != null) items(state.apps, key = { it.packageName }) { app ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium)
                    if (app.packageName == DefaultCategoryRules.YOUTUBE) {
                        YOUTUBE_ROWS.forEach { (tag, label) ->
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            CategoryChoice(resolver.resolve(app.packageName, tag.value)) {
                                viewModel.setCategory(app.packageName, tag.value, it)
                            }
                        }
                    } else {
                        CategoryChoice(resolver.resolve(app.packageName, null)) {
                            viewModel.setCategory(app.packageName, null, it)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChoice(current: String, onChange: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        CHOICES.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == current,
                onClick = { if (value != current) onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(i, CHOICES.size),
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
    }
}
