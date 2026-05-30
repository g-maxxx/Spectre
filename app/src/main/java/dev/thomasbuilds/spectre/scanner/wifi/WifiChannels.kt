package dev.thomasbuilds.spectre.scanner.wifi

import android.net.wifi.ScanResult
import dev.thomasbuilds.spectre.model.WifiBand

internal object WifiChannels {
  fun bandFor(freqMhz: Int): WifiBand =
    when {
      freqMhz < 3000 -> WifiBand.GHZ_2_4
      freqMhz < 5950 -> WifiBand.GHZ_5
      else -> WifiBand.GHZ_6
    }

  fun channelNumber(freqMhz: Int): Int? =
    when {
      freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
      freqMhz == 2484 -> 14
      freqMhz in 5160..5885 -> (freqMhz - 5000) / 5
      freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
      else -> null
    }

  fun widthMhz(channelWidthConst: Int): Int =
    when (channelWidthConst) {
      ScanResult.CHANNEL_WIDTH_20MHZ -> 20
      ScanResult.CHANNEL_WIDTH_40MHZ -> 40
      ScanResult.CHANNEL_WIDTH_80MHZ -> 80
      ScanResult.CHANNEL_WIDTH_160MHZ -> 160
      ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
      else -> 20
    }
}
