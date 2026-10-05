package com.example.usagetracker.service

enum class ContentTag(val value: String) { SHORTS("shorts"), VIDEO("video"), OTHER("other") }

/** A scrollable node's on-screen bounds, copied out of AccessibilityNodeInfo so the classifier stays pure. */
data class Scroller(val left: Int, val top: Int, val right: Int, val bottom: Int, val childCount: Int)

/** Everything the classifier looks at, extracted from one pass over the view hierarchy. */
data class ScreenSnapshot(
    val width: Int,
    val height: Int,
    /** View resource-ids with the "pkg:id/" prefix stripped, e.g. "reel_recycler". */
    val resourceIds: Set<String>,
    /** Any node that looks like a seek bar / time bar (by class name). */
    val hasSeekBarClass: Boolean,
    val scrollers: List<Scroller>,
)

/** [signal] names the rule that fired, e.g. "id:reel_recycler" or "layout:seekbar+list-below". */
data class Classification(val tag: ContentTag, val signal: String)

/**
 * Heuristic Shorts / video / other classifier. YouTube has no stable public IDs, so this is a
 * best-effort stack: resource-id substrings first, layout shape as a fallback. Expect to retune
 * the token lists below whenever YouTube changes its UI; the logged [Classification.signal] says
 * which rule needs attention.
 */
object ContentClassifier {
    // Observed on an API 37 emulator. Deliberately NOT "reel_time_bar" (stays in the tree after the
    // first Shorts visit, on every screen), "reel_scrim_*" or "reel_generic_ghost_loader".
    private val SHORTS_TOKENS = listOf(
        "reel_recycler", "reel_player_page", "reel_player_footer", "reel_player_overlay",
        "reel_watch_player", "reel_video_interactions", "reel_watch_fragment",
    )
    // Ids containing these are navigation chrome or the miniplayer, not the content itself
    // (e.g. the bottom-bar "Shorts" tab is visible on every screen).
    private val CHROME_TOKENS = listOf("pivot", "tab", "nav", "mini", "floaty")
    // Not "watch_while_layout_coordinator_layout" (on every screen) or "player_view" (also present
    // on the home feed while an inline ad plays).
    private val VIDEO_TOKENS = listOf("watch_player", "watch_panel", "watch_while_time_bar", "watch_list")

    // Present on home / search / feed screens. The full-bleed-pager layout rule false-positived on
    // these (a feed also has a full-screen scroller), so it is skipped when any is visible.
    private val BROWSE_TOKENS = listOf("browse_fragment", "youtube_logo", "filter_bar", "results", "search_box")

    /** True for an id that alone decides "shorts"; lets the tree walk stop as soon as one is seen. */
    fun isShortsId(id: String): Boolean =
        SHORTS_TOKENS.any { it in id } && CHROME_TOKENS.none { it in id }

    fun classify(s: ScreenSnapshot): Classification {
        val shorts = s.resourceIds.filter(::isShortsId)
        if (shorts.isNotEmpty()) return Classification(ContentTag.SHORTS, "id:" + shorts.sorted().first())

        val video = s.resourceIds.filter { id ->
            VIDEO_TOKENS.any { it in id } && CHROME_TOKENS.none { it in id }
        }
        if (video.isNotEmpty()) return Classification(ContentTag.VIDEO, "id:" + video.sorted().first())

        // Layout fallback. Video page: a seek bar plus a full-width list in the lower part of the
        // screen (related videos / comments). Shorts: a full-bleed pager with very few children.
        val hasSeek = s.hasSeekBarClass
        if (hasSeek && s.scrollers.any { it.top > s.height * 0.25 && it.right - it.left >= s.width * 0.9 }) {
            return Classification(ContentTag.VIDEO, "layout:seekbar+list-below")
        }
        val browsing = s.resourceIds.any { id -> BROWSE_TOKENS.any { it in id } }
        if (!hasSeek && !browsing && s.scrollers.any {
                it.top <= s.height * 0.05 && it.bottom - it.top >= s.height * 0.9 &&
                    it.right - it.left >= s.width * 0.95 && it.childCount <= 3
            }
        ) {
            return Classification(ContentTag.SHORTS, "layout:fullbleed-pager")
        }
        return Classification(ContentTag.OTHER, "default")
    }
}
