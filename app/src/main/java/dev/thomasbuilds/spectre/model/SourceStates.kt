package dev.thomasbuilds.spectre.model

import androidx.compose.runtime.Immutable

@Immutable
data class CellularSourceState(
  val signals: List<CellSignal> = emptyList(),
  val status: ScannerStatus = ScannerStatus.OK,
  val ready: Boolean = false,
  val connectionLabel: String? = null
)

@Immutable
data class WifiSourceState(
  val signals: List<WifiSignal> = emptyList(),
  val status: ScannerStatus = ScannerStatus.OK,
  val ready: Boolean = false,
  val scanThrottlingOn: Boolean = true
)

@Immutable
data class BluetoothSourceState(
  val signals: List<BluetoothSignal> = emptyList(),
  val status: ScannerStatus = ScannerStatus.OK,
  val ready: Boolean = false
)

@Immutable
data class GnssSourceState(
  val signals: List<GnssSignal> = emptyList(),
  val status: ScannerStatus = ScannerStatus.OK,
  val ready: Boolean = false
)
