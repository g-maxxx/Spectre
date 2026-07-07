package dev.thomasbuilds.spectre.scanner

internal class ReadinessTracker(
  private val warmupMs: Long
) {
  private var okSinceMs: Long = 0L
  private var hadData = false

  // When the source isn't OK (no permission, radio off, ...) report ready, so the card shows
  // that status instead of a perpetual warming-up skeleton, and reset the timers for next time.
  // Once a source has delivered data, an empty result means "nothing there", not "still loading",
  // so the skeleton only ever shows during the initial warmup.
  @Synchronized
  fun compute(
    statusOk: Boolean,
    hasData: Boolean,
    now: Long
  ): Boolean {
    if (!statusOk) {
      okSinceMs = 0L
      hadData = false
      return true
    }
    if (okSinceMs == 0L) okSinceMs = now
    if (hasData) hadData = true
    return hadData || (now - okSinceMs) > warmupMs
  }
}
