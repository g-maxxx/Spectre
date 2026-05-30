package dev.thomasbuilds.spectre.recon

object ServiceFingerprinter {
  fun identify(response: String?): String? {
    if (response.isNullOrEmpty()) return null

    if (response.startsWith("HTTP/")) {
      val server =
        response
          .lineSequence()
          .firstOrNull { it.startsWith("Server:", ignoreCase = true) }
          ?.substringAfter(':')
          ?.trim()
          ?.takeIf { it.isNotBlank() }
      return if (server != null) "http · $server" else "http"
    }

    for (sig in SIGNATURES) {
      val match = sig.regex.find(response) ?: continue
      val version =
        sig.versionGroup
          .takeIf { it > 0 }
          ?.let {
            match.groupValues
              .getOrNull(it)
              ?.trim()
              .orEmpty()
          }.orEmpty()
      return if (version.isNotEmpty()) "${sig.service} · $version" else sig.service
    }
    return null
  }

  private data class Sig(
    val service: String,
    val regex: Regex,
    val versionGroup: Int = 0
  )

  private val SIGNATURES =
    listOf(
      Sig("ssh", Regex("^SSH-\\d[\\d.]*-(\\S+)"), 1),
      Sig("ftp", Regex("(?i)^220[ -][^\\r\\n]*?ftp[^\\r\\n]*")),
      Sig("smtp", Regex("(?i)^220[ -][^\\r\\n]*?\\b(?:smtp|esmtp)\\b")),
      Sig("pop3", Regex("^\\+OK[^\\r\\n]*")),
      Sig("imap", Regex("(?i)^\\* OK[^\\r\\n]*imap")),
      Sig("mariadb", Regex("\\x0a(\\d+\\.\\d+\\.\\d+[\\w.-]*-MariaDB[\\w.-]*)\\x00"), 1),
      Sig("mysql", Regex("\\x0a(\\d+\\.\\d+\\.\\d+[\\w.-]*)\\x00"), 1),
      Sig("redis", Regex("^(?:-NOAUTH|-ERR|\\+PONG)")),
      Sig("rtsp", Regex("^RTSP/1\\.0")),
      Sig("vnc", Regex("^RFB (\\d{3}\\.\\d{3})"), 1),
      Sig("telnet", Regex("^\\xFF[\\xFB-\\xFE]"))
    )
}
