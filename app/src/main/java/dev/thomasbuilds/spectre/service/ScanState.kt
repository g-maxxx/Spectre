package dev.thomasbuilds.spectre.service

import androidx.compose.runtime.Immutable
import dev.thomasbuilds.spectre.model.BluetoothSignal
import dev.thomasbuilds.spectre.model.CellSignal
import dev.thomasbuilds.spectre.model.GnssSignal
import dev.thomasbuilds.spectre.model.ScannerStatus
import dev.thomasbuilds.spectre.model.WifiSignal

@Immutable
data class ScanState(
  val cellular: List<CellSignal> = emptyList(),
  val wifi: List<WifiSignal> = emptyList(),
  val bluetooth: List<BluetoothSignal> = emptyList(),
  val gnss: List<GnssSignal> = emptyList(),
  val totalDbm: Double = -120.0,
  val cellularStatus: ScannerStatus = ScannerStatus.OK,
  val wifiStatus: ScannerStatus = ScannerStatus.OK,
  val bluetoothStatus: ScannerStatus = ScannerStatus.OK,
  val gnssStatus: ScannerStatus = ScannerStatus.OK,
  val cellularConnection: String? = null,
  val wifiScanThrottlingOn: Boolean = true,
  val cellularReady: Boolean = true,
  val wifiReady: Boolean = true,
  val bluetoothReady: Boolean = true,
  val gnssReady: Boolean = true
) {
  val totalScoreUsable: Boolean
    get() =
      cellularStatus == ScannerStatus.OK &&
        wifiStatus == ScannerStatus.OK &&
        bluetoothStatus == ScannerStatus.OK &&
        cellularReady &&
        wifiReady &&
        bluetoothReady

  companion object {
    val Loading =
      ScanState(
        cellularReady = false,
        wifiReady = false,
        bluetoothReady = false,
        gnssReady = false
      )
  }
}
