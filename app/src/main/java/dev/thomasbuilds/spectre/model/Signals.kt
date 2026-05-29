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

enum class CellNetworkType(
  val displayName: String
) {
  NR_5G("5G"),
  LTE_4G("4G"),
  WCDMA_3G("3G"),
  GSM_2G("2G")
}

@Immutable
data class CellSignal(
  val type: CellNetworkType,
  val dbm: Int,
  val exposureDbm: Int,
  val channelKey: String? = null,
  val distanceMeters: Double?,
  val distanceConfidence: DistanceConfidence,
  val identifier: String,
  val operator: String? = null,
  val isConnected: Boolean = false,
  val details: List<DetailEntry> = emptyList()
)

enum class WifiBand(
  val label: String
) {
  GHZ_2_4("2.4G"),
  GHZ_5("5.0G"),
  GHZ_6("6.0G")
}

enum class WifiSecurity(
  val label: String
) {
  WPA3("WPA3"),
  WPA2("WPA2"),
  WPA("WPA"),
  PSK("PSK"),
  EAP("EAP"),
  OWE("OWE"),
  WEP("WEP"),
  OPEN("Open")
}

@Immutable
data class WifiSignal(
  val ssid: String,
  val bssid: String,
  val rssi: Int,
  val band: WifiBand,
  val distanceMeters: Double?,
  val distanceConfidence: DistanceConfidence,
  val isConnected: Boolean = false,
  val details: List<DetailEntry> = emptyList(),
  val firstSeenMs: Long = 0L,
  val securityTypes: Set<WifiSecurity> = emptySet(),
  val hasWps: Boolean = false,
  val isStale: Boolean = false
)

@Immutable
data class BluetoothSignal(
  val name: String,
  val mac: String,
  val rssi: Int,
  val distanceMeters: Double?,
  val distanceConfidence: DistanceConfidence,
  val isBonded: Boolean = false,
  val details: List<DetailEntry> = emptyList(),
  val advertisementHex: String? = null,
  val firstSeenMs: Long = 0L,
  val isConnectable: Boolean = false,
  val isStale: Boolean = false
)

enum class Constellation(
  val label: String,
  val shortLabel: String
) {
  GPS("GPS", "GPS"),
  GLONASS("GLONASS", "GLO"),
  GALILEO("Galileo", "GAL"),
  BEIDOU("BeiDou", "BDS"),
  QZSS("QZSS", "QZS"),
  IRNSS("NavIC", "IRN"),
  SBAS("SBAS", "SBS"),
  UNKNOWN("?", "?")
}

@Immutable
data class GnssSignal(
  val constellation: Constellation,
  val svid: Int,
  val cn0DbHz: Float,
  val elevationDeg: Float,
  val details: List<DetailEntry> = emptyList()
)
