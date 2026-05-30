package dev.thomasbuilds.spectre.scanner.gnss

import android.location.GnssStatus
import dev.thomasbuilds.spectre.model.Constellation
import kotlin.math.abs

internal object GnssBands {
  fun constellationFor(type: Int): Constellation =
    when (type) {
      GnssStatus.CONSTELLATION_GPS -> Constellation.GPS
      GnssStatus.CONSTELLATION_GLONASS -> Constellation.GLONASS
      GnssStatus.CONSTELLATION_GALILEO -> Constellation.GALILEO
      GnssStatus.CONSTELLATION_BEIDOU -> Constellation.BEIDOU
      GnssStatus.CONSTELLATION_QZSS -> Constellation.QZSS
      GnssStatus.CONSTELLATION_IRNSS -> Constellation.IRNSS
      GnssStatus.CONSTELLATION_SBAS -> Constellation.SBAS
      else -> Constellation.UNKNOWN
    }

  fun bandName(
    constellation: Constellation,
    freqHz: Float
  ): String? {
    fun near(target: Double): Boolean = abs(freqHz - target) < 10_000_000.0
    return when (constellation) {
      Constellation.GPS -> {
        when {
          near(1_575_420_000.0) -> "L1"
          near(1_227_600_000.0) -> "L2"
          near(1_176_450_000.0) -> "L5"
          else -> null
        }
      }

      Constellation.GALILEO -> {
        when {
          near(1_575_420_000.0) -> "E1"
          near(1_176_450_000.0) -> "E5a"
          near(1_207_140_000.0) -> "E5b"
          else -> null
        }
      }

      Constellation.GLONASS -> {
        when {
          near(1_602_000_000.0) -> "L1"
          near(1_246_000_000.0) -> "L2"
          else -> null
        }
      }

      Constellation.BEIDOU -> {
        when {
          near(1_561_098_000.0) -> "B1I"
          near(1_575_420_000.0) -> "B1C"
          near(1_176_450_000.0) -> "B2a"
          near(1_268_520_000.0) -> "B3"
          else -> null
        }
      }

      Constellation.QZSS -> {
        when {
          near(1_575_420_000.0) -> "L1"
          near(1_176_450_000.0) -> "L5"
          else -> null
        }
      }

      else -> {
        null
      }
    }
  }
}
