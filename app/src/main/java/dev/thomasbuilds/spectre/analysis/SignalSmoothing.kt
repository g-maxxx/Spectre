package dev.thomasbuilds.spectre.analysis

const val EMA_ALPHA = 0.3

fun smoothRssi(
  previousSmoothed: Double?,
  sample: Int,
  alpha: Double = EMA_ALPHA
): Double = if (previousSmoothed == null) sample.toDouble() else alpha * sample + (1 - alpha) * previousSmoothed
