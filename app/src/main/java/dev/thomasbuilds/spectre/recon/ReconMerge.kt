package dev.thomasbuilds.spectre.recon

internal object ReconMerge {
  fun mergeHost(
    list: List<HostInfo>,
    fresh: HostInfo
  ): HostInfo {
    val existing = list.firstOrNull { it.ip == fresh.ip } ?: return fresh
    return fresh.copy(
      ssdpServer = fresh.ssdpServer ?: existing.ssdpServer,
      ssdpLocation = fresh.ssdpLocation ?: existing.ssdpLocation
    )
  }

  fun mergeSsdp(
    list: List<HostInfo>,
    device: SsdpDevice
  ): HostInfo {
    val existing = list.firstOrNull { it.ip == device.ip }
    return existing?.copy(
      ssdpServer = device.server ?: existing.ssdpServer,
      ssdpLocation = device.location ?: existing.ssdpLocation
    ) ?: HostInfo(
      ip = device.ip,
      hostname = null,
      openPorts = emptyList(),
      banners = emptyMap(),
      ssdpServer = device.server,
      ssdpLocation = device.location
    )
  }

  fun ipToLong(ip: String): Long {
    val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return 0
    return parts.fold(0L) { acc, b -> (acc shl 8) or b.toLong() }
  }

  fun shortType(type: String): String = type.removeSuffix(".local.").removeSuffix(".")
}
