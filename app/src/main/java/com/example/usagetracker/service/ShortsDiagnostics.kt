package com.example.usagetracker.service

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Optional diagnostics for tuning Shorts detection against real YouTube screens. Off by default and
 * switched on from the hidden section in Settings. While off, none of this code runs.
 *
 * Privacy: records contain structure only (depth, class, view id, bounds, state flags). Node text is
 * never read. A contentDescription is kept only when it is [MAX_LABEL] characters or shorter (UI labels
 * such as tab names); anything longer becomes "<long>".
 */
object Diagnostics {
    /** Mirror of [DiagPrefs.enabled] so the service never touches SharedPreferences per event. */
    @Volatile
    var enabled = false

    const val TAG = "ShortsDiag"
    const val MAX_LABEL = 20
    const val MAX_DEPTH = 25
    const val MAX_NODES = 5_000
}

class DiagPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    /** Set by tapping the Settings heading a few times; keeps the section out of normal users' way. */
    var unlocked: Boolean
        get() = prefs.getBoolean(KEY_UNLOCKED, false)
        set(value) = prefs.edit { putBoolean(KEY_UNLOCKED, value) }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
            Diagnostics.enabled = value
            ContentDetectionService.instance?.refreshDiagnostics()
        }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_ENABLED = "enabled"
    }
}

/** What the last scan saw. Signal names only. */
data class DetectionSnapshot(
    val tag: ContentTag?, // null = YouTube not in the foreground
    val committed: ContentTag?,
    val signals: List<String>,
    val scannedAtElapsed: Long,
    val scanCount: Long,
)

/** Live state for the Detection status screen. The service only publishes while [watching]. */
object DetectionStatus {
    val state = MutableStateFlow<DetectionSnapshot?>(null)

    @Volatile
    var watching = false
}

/** One dumped node. [label] has already been sanitised; the raw description is never stored. */
data class DiagNode(
    val depth: Int,
    val className: String,
    val id: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val selected: Boolean,
    val checked: Boolean,
    val scrollable: Boolean,
    val visible: Boolean,
    val childCount: Int,
    val label: String?,
)

data class DiagRecord(
    val timeMillis: Long,
    val scanNumber: Long,
    val production: ContentTag?,
    val fullTree: ContentTag?,
    val signals: List<String>,
    val truncation: String,
    val visitedNodes: Int,
    val maxDepth: Int,
    val eventCounts: String,
    val nodes: List<DiagNode>,
)

object DiagFormatter {
    const val LEGEND = "# legend: depth class viewId l,t,r,b flags(S=selected C=checked R=scrollable V=visible) nChildren \"short label\""

    /** Null for no/blank description, `<long>` above [Diagnostics.MAX_LABEL], otherwise the cleaned label. */
    fun sanitizeLabel(raw: CharSequence?): String? {
        if (raw == null || raw.isBlank()) return null
        if (raw.length > Diagnostics.MAX_LABEL) return "<long>"
        return raw.filter { it >= ' ' && it != '"' }.toString().ifBlank { null }
    }

    fun nodeLine(n: DiagNode): String = buildString {
        append(n.depth).append(' ').append(n.className).append(' ').append(n.id ?: "-")
        append(' ').append(n.left).append(',').append(n.top).append(',').append(n.right).append(',').append(n.bottom)
        append(' ')
        append(if (n.selected) 'S' else '-').append(if (n.checked) 'C' else '-')
        append(if (n.scrollable) 'R' else '-').append(if (n.visible) 'V' else '-')
        append(" n").append(n.childCount)
        if (n.label != null) append(" \"").append(n.label).append('"')
    }

    /** Ignores bounds so a Short sliding into place doesn't make an otherwise identical tree look new. */
    fun signature(nodes: List<DiagNode>): Int =
        nodes.fold(7) { h, n ->
            31 * (31 * (31 * (31 * h + n.depth) + n.className.hashCode()) + (n.id?.hashCode() ?: 0)) +
                (n.label?.hashCode() ?: 0) + 3 * n.selected.hashCode() + 5 * n.checked.hashCode() +
                7 * n.scrollable.hashCode() + 11 * n.visible.hashCode()
        }

    fun header(r: DiagRecord, treeMarker: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(r.timeMillis))
        return "REC $time scan#${r.scanNumber} prod=${r.production?.value ?: "none"} " +
            "full=${r.fullTree?.value ?: "none"} trunc=${r.truncation} visited=${r.visitedNodes} " +
            "dumped=${r.nodes.size} maxDepth=${r.maxDepth} events={${r.eventCounts}} " +
            "signals=${r.signals} tree=$treeMarker"
    }

    /** Full text of one record. The tree is skipped (`unchanged`) when identical to the previous one. */
    fun record(r: DiagRecord, includeTree: Boolean): String = buildString {
        val sig = signature(r.nodes)
        appendLine(header(r, if (includeTree) "sig:%08x".format(sig) else "unchanged sig:%08x".format(sig)))
        if (includeTree) r.nodes.forEach { appendLine(nodeLine(it)) }
    }

    fun marker(timeMillis: Long, name: String): String =
        "MARK ${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(timeMillis))} $name\n"
}

/**
 * Two files of at most [maxFileBytes] each, written alternately: when the current one is full the other
 * is truncated and takes over. Total size therefore never exceeds 2 x [maxFileBytes] (1 MB by default)
 * and the newest records always survive.
 */
class DiagFileWriter(private val dir: File, private val maxFileBytes: Long = 512 * 1024L) {
    private val files = arrayOf(File(dir, "shortsdiag_a.log"), File(dir, "shortsdiag_b.log"))
    private var current = -1

    @Synchronized
    fun append(text: String) {
        dir.mkdirs()
        if (current < 0) current = pickCurrent()
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (files[current].length() + bytes.size > maxFileBytes) {
            current = 1 - current
            files[current].writeText(DiagFormatter.LEGEND + "\n")
        }
        if (!files[current].exists()) files[current].writeText(DiagFormatter.LEGEND + "\n")
        files[current].appendBytes(bytes.copyOf(minOf(bytes.size.toLong(), maxFileBytes).toInt()))
    }

    @Synchronized
    fun clear() {
        files.forEach { it.delete() }
        current = -1
    }

    private fun pickCurrent(): Int {
        val a = files[0]
        val b = files[1]
        return if (b.exists() && (!a.exists() || b.lastModified() >= a.lastModified())) 1 else 0
    }

    companion object {
        @Volatile
        private var instance: DiagFileWriter? = null

        fun get(context: Context): DiagFileWriter = instance ?: synchronized(this) {
            instance ?: DiagFileWriter(File(context.applicationContext.cacheDir, "shortsdiag")).also { instance = it }
        }
    }
}

/** Full-depth walk used only while diagnostics is on: no early exit, so we can see where signals really sit. */
object DiagTreeWalker {
    class Result(
        val nodes: List<DiagNode>,
        val snapshot: ScreenSnapshot,
        val idDepths: Map<String, Int>,
        val visited: Int,
        val maxDepth: Int,
        val truncated: Boolean,
    )

    fun walk(root: AccessibilityNodeInfo): Result {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val rect = Rect()
        val dump = ArrayList<DiagNode>()
        val ids = HashSet<String>()
        val idDepths = HashMap<String, Int>()
        val scrollers = ArrayList<Scroller>()
        var hasSeek = false
        var visited = 0
        var maxDepth = 0
        var truncated = false

        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (visited >= Diagnostics.MAX_NODES) {
                truncated = true
                if (node !== root) @Suppress("DEPRECATION") node.recycle()
                continue
            }
            visited++
            if (depth > maxDepth) maxDepth = depth
            val id = node.viewIdResourceName?.substringAfter(":id/")
            val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
            if (id != null) {
                ids.add(id)
                if ((idDepths[id] ?: Int.MAX_VALUE) > depth) idDepths[id] = depth
            }
            if (cls.contains("SeekBar")) hasSeek = true
            if (node.isScrollable && node.isVisibleToUser) {
                node.getBoundsInScreen(rect)
                scrollers += Scroller(rect.left, rect.top, rect.right, rect.bottom, node.childCount)
            }
            // contentDescription only; node.text is deliberately never read.
            val label = DiagFormatter.sanitizeLabel(node.contentDescription)
            if (id != null || label != null || node.isScrollable || node.isSelected || node.isChecked) {
                node.getBoundsInScreen(rect)
                dump += DiagNode(
                    depth, cls, id, rect.left, rect.top, rect.right, rect.bottom,
                    node.isSelected, node.isChecked, node.isScrollable, node.isVisibleToUser,
                    node.childCount, label,
                )
            }
            if (depth < Diagnostics.MAX_DEPTH) {
                // Reverse so the dump comes out in tree order.
                for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to depth + 1) }
            } else if (node.childCount > 0) {
                truncated = true
            }
            if (node !== root) @Suppress("DEPRECATION") node.recycle()
        }
        val snap = ScreenSnapshot(rootBounds.width(), rootBounds.height(), ids, hasSeek, scrollers)
        return Result(dump, snap, idDepths, visited, maxDepth, truncated)
    }
}
