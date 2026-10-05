package com.example.usagetracker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.CategoryRepository
import com.example.usagetracker.data.MIN_SESSION_MILLIS
import com.example.usagetracker.data.UsageSession
import com.example.usagetracker.tracking.TrackerPrefs
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Classifies what the user is doing in YouTube (Shorts / video / other) and writes one
 * UsageSession row per stretch of constant classification, with contentTag set. Category comes from the
 * user's CategoryRules at write time.
 *
 * Filter `adb logcat -s ContentDetect` to see which signal fired.
 */
class ContentDetectionService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val debouncer = ClassificationDebouncer(MIN_CHECKS, MIN_HOLD_MS)
    private var scanScheduled = false
    private var scheduledDue = 0L // elapsedRealtime the pending scan is due at
    private val scanRunnable = Runnable {
        scanScheduled = false
        scan()
    }
    private var scanCount = 0L
    private var lastDiagSignature: Int? = null // executor thread only
    private val eventCounts = HashMap<Int, Int>() // main thread only; diagnostics only
    private var lastLogged: Classification? = null
    private var lastHeartbeat = 0L
    private var lastScanTime = 0L // SystemClock.elapsedRealtime()

    // All DB work goes through one thread so open/close of the current row stay ordered.
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val dao by lazy { AppDatabase.getInstance(applicationContext).usageSessionDao() }
    private val categories by lazy { CategoryRepository(applicationContext) }
    private var openRowId: Long? = null // only touched on the executor thread
    private var openRowStart = 0L // likewise
    private val trackerPrefs by lazy { TrackerPrefs(applicationContext) }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "screen off")
            debouncer.forceLeave(System.currentTimeMillis())?.let(::persist)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        trackingEnabled = trackerPrefs.enabled
        Diagnostics.enabled = DiagPrefs(applicationContext).enabled
        instance = this
        refreshDiagnostics()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    /**
     * While diagnostics is on, also *count* a few extra event types so the records show which events a
     * playing Short really produces. They never trigger a scan. Back to the base set when it is off.
     */
    fun refreshDiagnostics() {
        val info = serviceInfo ?: return
        info.eventTypes = if (Diagnostics.enabled) {
            SCAN_EVENT_TYPES or AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or AccessibilityEvent.TYPE_VIEW_CLICKED
        } else SCAN_EVENT_TYPES
        serviceInfo = info
        if (!Diagnostics.enabled) {
            eventCounts.clear()
            lastDiagSignature = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != YOUTUBE) return
        if (!trackingEnabled) {
            closeForTrackingOff()
            return
        }
        if (Diagnostics.enabled) eventCounts.merge(event.eventType, 1, Int::plus)
        if (event.eventType and SCAN_EVENT_TYPES == 0) return
        scheduleScan(SCAN_THROTTLE_MS)
    }

    /**
     * Keeps a single pending scan, but always the *earliest* one requested. Previously a pending slow
     * re-check (5 s) swallowed every event arriving meanwhile, so a screen that finished loading after
     * the first scan was not looked at again until that timer fired.
     */
    private fun scheduleScan(delayMs: Long) {
        val due = SystemClock.elapsedRealtime() + delayMs
        if (scanScheduled && due >= scheduledDue) return
        handler.removeCallbacks(scanRunnable)
        scanScheduled = true
        scheduledDue = due
        handler.postDelayed(scanRunnable, delayMs)
    }

    /**
     * The master "Tracking" switch. While it is off nothing is processed or written; a row that was
     * open when it went off is closed once so it doesn't keep counting.
     */
    private fun trackingOn(): Boolean {
        if (trackingEnabled) return true
        closeForTrackingOff()
        return false
    }

    private fun closeForTrackingOff() {
        handler.removeCallbacksAndMessages(null)
        scanScheduled = false
        debouncer.forceLeave(System.currentTimeMillis())?.let(::persist)
    }

    private fun scan() {
        if (!trackingOn()) return
        // Cap full scans at ~1/s. Deferred rather than dropped so a pending change still gets its recheck.
        val elapsed = SystemClock.elapsedRealtime()
        val sinceLast = elapsed - lastScanTime
        if (sinceLast < MIN_SCAN_GAP_MS) {
            scheduleScan(MIN_SCAN_GAP_MS - sinceLast)
            return
        }
        lastScanTime = elapsed
        val now = System.currentTimeMillis()
        // A failed scan (no window, exception) says nothing about the screen, so it must not be
        // counted as "other": keep the previous classification and just look again later.
        val result = try {
            scanTree()
        } catch (e: Exception) {
            Log.w(TAG, "scan failed: ${e.javaClass.simpleName}")
            null
        }
        if (result == null) {
            scheduleFollowUp()
            return
        }
        scanCount++
        publish(result)

        debouncer.observe(result.tag, now)?.let { t ->
            Log.i(TAG, "TRANSITION ${t.from?.value ?: "(left)"} -> ${t.to?.value ?: "(left)"} " +
                "(boundary ${now - t.at}ms before commit)")
            persist(t)
            lastHeartbeat = now
        } ?: run {
            if (debouncer.committed != null && !debouncer.hasPending && now - lastHeartbeat >= HEARTBEAT_MS) {
                lastHeartbeat = now
                scope.launch { openRowId?.let { dao.updateEndTime(it, now) } }
            }
        }
        scheduleFollowUp()
    }

    /**
     * Always leaves a next check queued while YouTube is (or may be) in front: soon if a change awaits
     * confirmation, otherwise a light periodic re-check. Nothing is queued once the session is closed,
     * so the loop stops by itself when YouTube leaves the foreground.
     */
    private fun scheduleFollowUp() {
        if (debouncer.hasPending) scheduleScan(RECHECK_MS)
        else if (debouncer.committed != null) scheduleScan(PERIODIC_CHECK_MS)
    }

    private class ScanResult(
        val tag: ContentTag?, // null = YouTube not in front
        val signal: String,
        val snapshot: ScreenSnapshot?,
        val diag: DiagTreeWalker.Result?,
        val truncation: String,
        val visited: Int,
    )

    /** Null when there is no window to read right now (transient during window transitions). */
    private fun scanTree(): ScanResult? {
        val root = rootInActiveWindow ?: return null
        try {
            if (root.packageName != YOUTUBE) return ScanResult(null, "not-youtube", null, null, "-", 0)
            val walk = snapshot(root)
            val c = ContentClassifier.classify(walk.snapshot)
            if (c != lastLogged) {
                lastLogged = c
                Log.d(TAG, "scan: ${c.tag.value} via ${c.signal}")
                // Layout-fallback hits are the shaky ones; dump what the screen looked like.
                if (c.signal.startsWith("layout:")) {
                    Log.d(TAG, "  ids=${walk.snapshot.resourceIds.sorted().take(60)} seekBar=${walk.snapshot.hasSeekBarClass} " +
                        "scrollers=${walk.snapshot.scrollers}")
                }
            }
            val diag = if (Diagnostics.enabled) DiagTreeWalker.walk(root) else null
            return ScanResult(c.tag, c.signal, walk.snapshot, diag, walk.truncation, walk.visited)
        } finally {
            @Suppress("DEPRECATION") root.recycle()
        }
    }

    /** Feeds the Detection status screen (only while it is open) and the diagnostics file (only when on). */
    private fun publish(r: ScanResult) {
        if (DetectionStatus.watching) {
            val signals = r.diag?.let { ContentClassifier.matchedSignals(it.snapshot) }
                ?: r.snapshot?.let { ContentClassifier.matchedSignals(it) }
                ?: emptyList()
            DetectionStatus.state.value = DetectionSnapshot(
                r.tag, debouncer.committed, signals, SystemClock.elapsedRealtime(), scanCount,
            )
        }
        val diag = r.diag ?: return
        val record = DiagRecord(
            timeMillis = System.currentTimeMillis(),
            scanNumber = scanCount,
            production = r.tag,
            fullTree = ContentClassifier.classify(diag.snapshot).tag,
            signals = ContentClassifier.matchedSignals(diag.snapshot).map { sig ->
                val id = sig.substringAfterLast(':')
                diag.idDepths[id]?.let { "$sig@d$it" } ?: sig
            },
            truncation = listOfNotNull(
                r.truncation.takeIf { it != "-" }?.let { "prod-$it" },
                if (diag.truncated) "diag-cap" else null,
            ).joinToString("+").ifEmpty { "-" },
            visitedNodes = diag.visited,
            maxDepth = diag.maxDepth,
            eventCounts = eventCounts.entries.sortedBy { it.key }
                .joinToString(",") { "${AccessibilityEvent.eventTypeToString(it.key).removePrefix("TYPE_")}:${it.value}" },
            nodes = diag.nodes,
        )
        eventCounts.clear()
        val writer = DiagFileWriter.get(applicationContext)
        scope.launch {
            val sig = DiagFormatter.signature(record.nodes)
            val text = DiagFormatter.record(record, includeTree = sig != lastDiagSignature)
            lastDiagSignature = sig
            writer.append(text)
            text.lineSequence().chunked(40).forEach { chunk ->
                Log.d(Diagnostics.TAG, chunk.joinToString("\n").trimEnd())
            }
        }
    }

    private class Walk(val snapshot: ScreenSnapshot, val truncation: String, val visited: Int)

    private fun snapshot(root: AccessibilityNodeInfo): Walk {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val ids = HashSet<String>()
        val scrollers = ArrayList<Scroller>()
        var hasSeek = false
        val rect = Rect()

        // Iterative DFS with caps: YouTube's tree can be deep and we run this on the main thread.
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        var depthCut = false
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            visited++
            val id = node.viewIdResourceName?.substringAfter(":id/")
            if (id != null) ids.add(id)
            if (node.className?.contains("SeekBar") == true) hasSeek = true
            if (id != null && ContentClassifier.isShortsId(id)) {
                // Shorts outranks every other signal, so nothing deeper can change the result.
                if (node !== root) @Suppress("DEPRECATION") node.recycle()
                break
            }
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(rect)
                scrollers += Scroller(rect.left, rect.top, rect.right, rect.bottom, node.childCount)
            }
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { stack.addLast(it to depth + 1) }
                }
            } else if (node.childCount > 0) {
                depthCut = true
            }
            if (node !== root) @Suppress("DEPRECATION") node.recycle()
        }
        val truncation = when {
            depthCut && visited >= MAX_NODES -> "depth+nodes"
            depthCut -> "depth"
            visited >= MAX_NODES && stack.isNotEmpty() -> "nodes"
            else -> "-"
        }
        // Anything left on the stack (caps hit) still needs releasing on API < 33.
        stack.forEach { @Suppress("DEPRECATION") it.first.recycle() }
        return Walk(ScreenSnapshot(rootBounds.width(), rootBounds.height(), ids, hasSeek, scrollers), truncation, visited)
    }

    /** Close the open row at [Transition.at] and open a new one if [Transition.to] is non-null. */
    private fun persist(t: Transition) {
        scope.launch {
            openRowId?.let {
                if (t.at - openRowStart < MIN_SESSION_MILLIS) dao.deleteById(it) else dao.updateEndTime(it, t.at)
            }
            openRowId = null
            t.to?.let {
                val category = categories.resolver().resolve(YOUTUBE, it.value)
                openRowStart = t.at
                openRowId = dao.insert(
                    UsageSession(
                        packageName = YOUTUBE,
                        appName = "YouTube",
                        category = category,
                        contentTag = it.value,
                        startTime = t.at,
                        endTime = t.at,
                    ),
                )
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private var shutDown = false
    private fun shutdown() {
        if (shutDown) return
        shutDown = true
        handler.removeCallbacksAndMessages(null)
        scanScheduled = false
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOff) }
        debouncer.forceLeave(System.currentTimeMillis())?.let(::persist)
        // Queued after the close above, so the final write still runs.
        scope.launch {
            executor.shutdown()
            scope.cancel()
        }
    }

    companion object {
        /** Master tracking switch, mirrored from [TrackerPrefs] so events never touch SharedPreferences. */
        @Volatile
        var trackingEnabled = false

        /** The connected service, so Settings can apply the diagnostics switch immediately. */
        @Volatile
        var instance: ContentDetectionService? = null

        private const val TAG = "ContentDetect"
        private const val YOUTUBE = "com.google.android.youtube"
        private const val MIN_CHECKS = 2
        private const val MIN_HOLD_MS = 1_000L
        private const val MIN_SCAN_GAP_MS = 1_000L
        private const val SCAN_THROTTLE_MS = 400L
        private const val RECHECK_MS = 600L
        private const val PERIODIC_CHECK_MS = 3_000L
        private const val HEARTBEAT_MS = 15_000L
        private const val MAX_NODES = 2_000
        private const val MAX_DEPTH = 24 // reel_recycler was measured at depth 14; 12 cut it off and Shorts read as "other"
        private const val SCAN_EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }
}
