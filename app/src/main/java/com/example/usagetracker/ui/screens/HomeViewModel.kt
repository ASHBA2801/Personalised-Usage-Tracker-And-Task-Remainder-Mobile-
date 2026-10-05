package com.example.usagetracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.usagetracker.data.AppDatabase
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class Period(val label: String) { TODAY("Today"), WEEK("Week") }

/** [report] is null until the first query returns, so the UI can tell "loading" from "empty". */
data class HomeState(val period: Period, val report: UsageReport?)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).usageSessionDao()
    private val taskDao = AppDatabase.getInstance(app).focusTaskDao()
    private val period = MutableStateFlow(Period.TODAY)
    // Bumped on resume so the window is recomputed (e.g. after midnight while the app sat open).
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<HomeState> = combine(period, refresh) { p, _ -> p }
        .flatMapLatest { p ->
            val (start, end) = windowFor(p)
            combine(
                dao.observeAppTotals(start, end),
                dao.observeCategoryTotals(start, end),
                dao.observeYouTubeContentTotals(start, end),
            ) { apps, cats, tags -> HomeState(p, UsageAggregator.build(apps, cats, tags)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(period.value, null))

    /** Today's task totals; null until the first emission. Re-keyed on resume so it follows midnight. */
    val todayProgress: StateFlow<Progress?> = refresh
        .flatMapLatest { taskDao.observeDayCounts(LocalDate.now().toString()) }
        .map { dayProgress(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(p: Period) { period.value = p }

    fun refresh() { refresh.value++ }

    /** Today = local midnight to next midnight. Week = the last 7 days, today included. */
    private fun windowFor(p: Period): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = cal.timeInMillis
        if (p == Period.TODAY) return today to tomorrow
        cal.add(Calendar.DAY_OF_YEAR, -7)
        return cal.timeInMillis to tomorrow
    }
}
