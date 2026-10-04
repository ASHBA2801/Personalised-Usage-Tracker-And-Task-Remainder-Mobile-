package com.example.usagetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.usagetracker.data.AppDatabase
import java.time.LocalDate

/** Lists today's incomplete tasks, re-queried on open. */
@Composable
fun EndOfDayScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var titles by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val today = LocalDate.now().toString()
        titles = AppDatabase.getInstance(context).focusTaskDao().getIncompleteForDate(today).map { it.title }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Today's incomplete tasks:", style = MaterialTheme.typography.headlineMedium)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(titles) { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
        Button(onClick = onDone) { Text("OK") }
    }
}
