package com.example.usagetracker.service

/** A committed change of state. [from]/[to] are null when YouTube is not in the foreground. */
data class Transition(val from: ContentTag?, val to: ContentTag?, val at: Long)

/**
 * Only commits a new state after it has been observed on [minChecks] consecutive checks spanning at
 * least [minHoldMs]. The transition is stamped with when the new state was *first* seen, so session
 * boundaries aren't shifted late by the debounce delay.
 */
class ClassificationDebouncer(
    private val minChecks: Int = 2,
    private val minHoldMs: Long = 1_000L,
) {
    private class Pending(val tag: ContentTag?, val since: Long, var count: Int)

    var committed: ContentTag? = null
        private set
    private var pending: Pending? = null

    val isIdle: Boolean get() = committed == null && pending == null
    val hasPending: Boolean get() = pending != null

    /** [tag] is null when YouTube is not in the foreground. */
    fun observe(tag: ContentTag?, now: Long): Transition? {
        if (tag == committed) {
            pending = null
            return null
        }
        val p = pending
        if (p == null || p.tag != tag) {
            pending = Pending(tag, now, 1)
            return null
        }
        p.count++
        if (p.count < minChecks || now - p.since < minHoldMs) return null
        return Transition(committed, tag, p.since).also {
            committed = tag
            pending = null
        }
    }

    /** Immediately leave YouTube (screen off, service stopping). Returns null if already out. */
    fun forceLeave(now: Long): Transition? {
        pending = null
        val from = committed ?: return null
        committed = null
        return Transition(from, null, now)
    }
}
