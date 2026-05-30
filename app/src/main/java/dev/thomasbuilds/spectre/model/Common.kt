package dev.thomasbuilds.spectre.model

import androidx.compose.runtime.Immutable

enum class ScannerStatus(
  val message: String?
) {
  OK(null),
  NO_PERMISSION("Tap to grant permission"),
  RADIO_OFF("Tap to turn on"),
  LOCATION_OFF(null),
  NO_SIM("No SIM card")
}

@Immutable
data class DetailEntry(
  val label: String,
  val value: String
)

enum class DistanceConfidence { NONE, PENDING, APPROXIMATE, CALIBRATED }
