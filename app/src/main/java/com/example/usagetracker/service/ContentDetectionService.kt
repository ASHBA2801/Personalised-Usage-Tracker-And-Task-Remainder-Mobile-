package com.example.usagetracker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.CategoryRepository
import com.example.usagetracker.data.UsageSession
import com.example.usagetracker.tracking.TrackerPrefs
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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
    private var lastLogged: Classification? = null
    private var lastHeartbeat = 0L

    // All DB work goes through one thread so open/close of the current row stay ordered.
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val dao by lazy { AppDatabase.getInstance(applicationContext).usageSessionDao() }
    private val categories by lazy { CategoryRepository(applicationContext) }
    private var openRowId: Long? = null // only touched on the executor thread
    private val trackerPrefs by lazy { TrackerPrefs(applicationContext) }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "screen off")
            debouncer.forceLeave(System.currentTimeMillis())?.let(::persist)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!trackingOn()) return
        val inYouTube = event.packageName == YOUTUBE
        if (inYouTube) {
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) return
        } else {
            // Another app's window opening is how we notice YouTube was left. Ignore everything
            // else, and ignore all of it while we aren't tracking YouTube.
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || debouncer.isIdle) return
        }
        scheduleScan(SCAN_THROTTLE_MS)
    }

    private fun scheduleScan(delayMs: Long) {
        if (scanScheduled) return
        scanScheduled = true
        handler.postDelayed({
            scanScheduled = false
            scan()
        }, delayMs)
    }

    /**
     * The master "Tracking" switch. While it is off nothing is processed or written; a row that was
     * open when it went off is closed once so it doesn't keep counting.
     */
    private fun trackingOn(): Boolean {
        if (trackerPrefs.enabled) return true
        handler.removeCallbacksAndMessages(null)
        scanScheduled = false
        debouncer.forceLeave(System.currentTimeMillis())?.let(::persist)
        return false
    }

    private fun scan() {
        if (!trackingOn()) return
        val now = System.currentTimeMillis()
        val root = rootInActiveWindow
        if (root == null) {
            // Transient during window transitions; don't count it as an observation.
            if (debouncer.hasPending) scheduleScan(RECHECK_MS)
            return
        }
        val tag: ContentTag? = try {
            if (root.packageName == YOUTUBE) classify(root) else null
        } finally {
            @Suppress("DEPRECATION") root.recycle()
        }

        debouncer.observe(tag, now)?.let { t ->
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
        // A change is pending confirmation: no further event may arrive on a static screen.
        if (debouncer.hasPending) scheduleScan(RECHECK_MS)
    }

    private fun classify(root: AccessibilityNodeInfo): ContentTag {
        val snap = snapshot(root)
        val c = ContentClassifier.classify(snap)
        if (c != lastLogged) {
            lastLogged = c
            Log.d(TAG, "scan: ${c.tag.value} via ${c.signal}")
            // Layout-fallback hits are the shaky ones; dump what the screen looked like.
            if (c.signal.startsWith("layout:")) {
                Log.d(TAG, "  ids=${snap.resourceIds.sorted().take(60)} seekBar=${snap.hasSeekBarClass} " +
                    "scrollers=${snap.scrollers}")
            }
        }
        return c.tag
    }

    private fun snapshot(root: AccessibilityNodeInfo): ScreenSnapshot {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val ids = HashSet<String>()
        val scrollers = ArrayList<Scroller>()
        var hasSeek = false
        val rect = Rect()

        // Iterative DFS with caps: YouTube's tree can be deep and we run this on the main thread.
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            visited++
            node.viewIdResourceName?.substringAfter(":id/")?.let(ids::add)
            if (node.className?.contains("SeekBar") == true) hasSeek = true
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(rect)
                scrollers += Scroller(rect.left, rect.top, rect.right, rect.bottom, node.childCount)
            }
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { stack.addLast(it to depth + 1) }
                }
            }
            if (node !== root) @Suppress("DEPRECATION") node.recycle()
        }
        // Anything left on the stack (caps hit) still needs releasing on API < 33.
        stack.forEach { @Suppress("DEPRECATION") it.first.recycle() }
        return ScreenSnapshot(rootBounds.width(), rootBounds.height(), ids, hasSeek, scrollers)
    }

    /** Close the open row at [Transition.at] and open a new one if [Transition.to] is non-null. */
    private fun persist(t: Transition) {
        scope.launch {
            openRowId?.let { dao.updateEndTime(it, t.at) }
            openRowId = null
            t.to?.let {
                val category = categories.resolver().resolve(YOUTUBE, it.value)
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
        runCatching { unregisterReceiver(screenOff) }
        debouncer.forceLeave(System.currentTimeMillis())?.let(::persist)
        // Queued after the close above, so the final write still runs.
        scope.launch { executor.shutdown() }
    }

    private companion object {
        const val TAG = "ContentDetect"
        const val YOUTUBE = "com.google.android.youtube"
        const val MIN_CHECKS = 2
        const val MIN_HOLD_MS = 1_000L
        const val SCAN_THROTTLE_MS = 400L
        const val RECHECK_MS = 600L
        const val HEARTBEAT_MS = 15_000L
        const val MAX_NODES = 1_500
        const val MAX_DEPTH = 40
    }
}
