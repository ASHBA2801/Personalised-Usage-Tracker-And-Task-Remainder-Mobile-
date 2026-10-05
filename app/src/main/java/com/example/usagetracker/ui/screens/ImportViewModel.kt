package com.example.usagetracker.ui.screens

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.importer.ImportException
import com.example.usagetracker.importer.ImportFileReader
import com.example.usagetracker.importer.ImportIssue
import com.example.usagetracker.importer.ImportResult
import com.example.usagetracker.importer.ImportValidator
import com.example.usagetracker.importer.Severity
import com.example.usagetracker.importer.TaskImportRepository
import com.example.usagetracker.importer.ValidatedImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

sealed interface ImportUiState {
    /** Nothing picked (or the preview was lost, e.g. after the process was killed). */
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Failed(val issue: ImportIssue) : ImportUiState
    /** [duplicates] are indexes into the validated tasks that match a task already stored. */
    data class Ready(val result: ValidatedImport, val duplicates: Set<Int>) : ImportUiState
    data object Saving : ImportUiState
    /** Saved; the preview closes itself and the Focus Tasks screen shows the Undo snackbar. */
    data object Done : ImportUiState
}

/**
 * Drives pick → preview → save → undo. Scoped to the activity so the Focus Tasks screen and the preview
 * share it. The picked Uri is read once and never stored; only the parsed tasks are kept, in memory.
 */
class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TaskImportRepository(AppDatabase.getInstance(app))
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _skipDuplicates = MutableStateFlow(true)
    val skipDuplicates: StateFlow<Boolean> = _skipDuplicates.asStateFlow()

    /** Set after a successful import, until the Focus Tasks screen has shown its Undo snackbar. */
    private val _finished = MutableStateFlow<ImportResult?>(null)
    val finished: StateFlow<ImportResult?> = _finished.asStateFlow()

    private var job: Job? = null

    fun load(uri: Uri) {
        job?.cancel()
        _skipDuplicates.value = true
        _state.value = ImportUiState.Loading
        job = viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val parsed = resolver.openInputStream(uri)?.use { ImportFileReader.read(it, displayName(uri)) }
                        ?: throw ImportException(fileIssue("Couldn't open this file."))
                    val validated = ImportValidator(LocalDate.now(), ZoneId.systemDefault()).validate(parsed)
                    ImportUiState.Ready(validated, repository.findDuplicates(validated.tasks))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                ImportUiState.Failed(e.issue)
            } catch (e: Exception) {
                // Messages from lower layers may echo file content, so they are never shown or logged.
                ImportUiState.Failed(fileIssue("Couldn't read this file. Check that it is a .json or .docx file."))
            }
        }
    }

    fun setSkipDuplicates(skip: Boolean) {
        _skipDuplicates.value = skip
    }

    /** Saves the previewed tasks in one transaction, then moves to [ImportUiState.Done]. */
    fun confirm() {
        val ready = _state.value as? ImportUiState.Ready ?: return
        _state.value = ImportUiState.Saving
        job = viewModelScope.launch {
            try {
                val result = repository.import(ready.result.tasks, _skipDuplicates.value)
                _finished.value = result
                _state.value = ImportUiState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ImportUiState.Failed(fileIssue("Saving failed, so nothing was imported. Please try again."))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportUiState.Idle
    }

    fun consumeFinished() {
        _finished.value = null
    }

    fun undo(batchId: String) {
        viewModelScope.launch { repository.undo(batchId) }
    }

    private fun displayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    private fun fileIssue(message: String) = ImportIssue(Severity.ERROR, "File", message)

    companion object {
        /** Real format is detected from content; octet-stream and msword let more pickers offer the file. */
        val MIME_TYPES = arrayOf(
            "application/json",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/octet-stream",
        )
    }
}
