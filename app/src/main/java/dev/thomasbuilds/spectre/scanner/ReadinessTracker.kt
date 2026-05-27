package dev.thomasbuilds.spectre.scanner

internal class ReadinessTracker(
  private val warmupMs: Long,
  private val stalenessMs: Long
) {
  private var okSinceMs: Long = 0L
  private var lastDataMs: Long = 0L

  fun compute(
    statusOk: Boolean,
    hasData: Boolean,
    now: Long
  ): Boolean {
    // When the source isn't OK (no permission, radio off, ...) report ready, so the card shows
    // that status instead of a perpetual warming-up skeleton, and reset the timers for next time.
    if (!statusOk) {
      okSinceMs = 0L
      lastDataMs = 0L
      return true
    }
    if (okSinceMs == 0L) okSinceMs = now
    if (hasData) lastDataMs = now
    if (lastDataMs != 0L) return (now - lastDataMs) < stalenessMs
    return (now - okSinceMs) > warmupMs
  }
}
