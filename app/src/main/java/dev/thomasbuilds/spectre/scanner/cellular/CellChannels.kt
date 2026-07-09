package dev.thomasbuilds.spectre.scanner.cellular

// *ARFCN to downlink frequency (MHz) per 3GPP TS 38.104 (NR), 36.101 (LTE), 25.101 (WCDMA), 45.005 (GSM).
internal object CellChannels {
  fun label(mhz: Double): String = "%.1f MHz".format(mhz)

  fun nrArfcnToMhz(nrArfcn: Int): Double? =
    when {
      nrArfcn < 0 -> null
      nrArfcn < 600_000 -> nrArfcn * 0.005
      nrArfcn < 2_016_667 -> 3000.0 + (nrArfcn - 600_000) * 0.015
      nrArfcn <= 3_279_165 -> 24_250.08 + (nrArfcn - 2_016_667) * 0.060
      else -> null
    }

  fun earfcnToMhz(earfcn: Int): Double? = LTE_BANDS.toMhz(earfcn, stepMhz = 0.1)

  fun uarfcnToMhz(uarfcn: Int): Double? = UMTS_BANDS.toMhz(uarfcn, stepMhz = 0.2)

  fun arfcnToMhz(arfcn: Int): Double? = GSM_BANDS.toMhz(arfcn, stepMhz = 0.2)

  private class Band(
    val first: Int,
    val last: Int,
    val dlLowMhz: Double
  )

  private fun List<Band>.toMhz(
    channel: Int,
    stepMhz: Double
  ): Double? =
    firstOrNull { channel in it.first..it.last }
      ?.let { it.dlLowMhz + stepMhz * (channel - it.first) }

  private val LTE_BANDS =
    listOf(
      Band(0, 599, 2110.0),
      Band(600, 1199, 1930.0),
      Band(1200, 1949, 1805.0),
      Band(1950, 2399, 2110.0),
      Band(2400, 2649, 869.0),
      Band(2750, 3449, 2620.0),
      Band(3450, 3799, 925.0),
      Band(5010, 5179, 729.0),
      Band(5180, 5279, 746.0),
      Band(5280, 5379, 758.0),
      Band(5730, 5849, 734.0),
      Band(5850, 5999, 860.0),
      Band(6000, 6149, 875.0),
      Band(6150, 6449, 791.0),
      Band(6450, 6599, 1495.9),
      Band(8040, 8689, 1930.0),
      Band(8690, 9039, 859.0),
      Band(9210, 9659, 758.0),
      Band(9660, 9769, 717.0),
      Band(9770, 9869, 2350.0),
      Band(9920, 10359, 1452.0),
      Band(37750, 38249, 2570.0),
      Band(38250, 38649, 1880.0),
      Band(38650, 39649, 2300.0),
      Band(39650, 41589, 2496.0),
      Band(41590, 43589, 3400.0),
      Band(43590, 45589, 3600.0),
      Band(46790, 54539, 5150.0),
      Band(55240, 56739, 3550.0),
      Band(66436, 67335, 2110.0),
      Band(68586, 68935, 617.0)
    )

  // Each band's FDL_offset (TS 25.101 Table 5.1) is folded into dlLowMhz; the rare 100 kHz-raster
  // "additional channels" are intentionally unmapped.
  private val UMTS_BANDS =
    listOf(
      Band(712, 763, 877.4), // XIX
      Band(1162, 1513, 1807.4), // III
      Band(1537, 1738, 2112.4), // IV
      Band(2937, 3088, 927.4), // VIII
      Band(3712, 3787, 1478.4), // XI
      Band(4357, 4458, 871.4), // V (VI is a subrange)
      Band(9237, 9387, 1847.4), // IX
      Band(9662, 9938, 1932.4), // II
      Band(10562, 10838, 2112.4) // I
    )

  // 512..810 overlaps DCS-1800 and PCS-1900. Resolved as DCS-1800, the near-universal modern allocation.
  private val GSM_BANDS =
    listOf(
      Band(0, 124, 935.0), // E-GSM ARFCN 0 + P-GSM
      Band(128, 251, 869.2), // GSM-850
      Band(512, 885, 1805.2), // DCS-1800
      Band(975, 1023, 925.2) // E-GSM extension
    )
}
