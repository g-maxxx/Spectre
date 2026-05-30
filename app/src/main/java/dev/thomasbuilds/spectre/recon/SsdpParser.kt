package dev.thomasbuilds.spectre.recon

internal object SsdpParser {
  const val MULTICAST_HOST = "239.255.255.250"
  const val PORT = 1900

  fun mSearch(searchTarget: String): ByteArray =
    (
      "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: $MULTICAST_HOST:$PORT\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: $searchTarget\r\n" +
        "USER-AGENT: Spectre-Recon/1.0\r\n" +
        "\r\n"
    ).toByteArray(Charsets.US_ASCII)

  fun parse(
    raw: String,
    fromIp: String
  ): SsdpDevice? {
    val lines = raw.lineSequence().toList()
    if (lines.isEmpty()) return null
    val statusLine = lines[0].trim()
    val isResponse = statusLine.startsWith("HTTP/1.1 200", ignoreCase = true)
    val isNotify = statusLine.startsWith("NOTIFY ", ignoreCase = true)
    if (!isResponse && !isNotify) return null

    val headers =
      lines
        .drop(1)
        .mapNotNull { line ->
          val idx = line.indexOf(':')
          if (idx < 1) return@mapNotNull null
          line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
        }.toMap()

    val usn = headers["usn"] ?: return null
    return SsdpDevice(
      ip = fromIp,
      location = headers["location"],
      server = headers["server"],
      usn = usn
    )
  }
}
