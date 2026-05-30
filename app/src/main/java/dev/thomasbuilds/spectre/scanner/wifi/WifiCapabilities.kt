package dev.thomasbuilds.spectre.scanner.wifi

import dev.thomasbuilds.spectre.model.WifiSecurity

internal object WifiCapabilities {
  fun parseSecurityTypes(capabilities: String?): Set<WifiSecurity> {
    val caps = capabilities.orEmpty()
    val types = linkedSetOf<WifiSecurity>()
    if ("WPA3" in caps || "SAE" in caps) types += WifiSecurity.WPA3
    if ("WPA2" in caps || "RSN" in caps) types += WifiSecurity.WPA2
    if ("WPA-" in caps || "WPA/" in caps || "WPA]" in caps) types += WifiSecurity.WPA
    if ("PSK" in caps) types += WifiSecurity.PSK
    if ("EAP" in caps) types += WifiSecurity.EAP
    if ("OWE" in caps) types += WifiSecurity.OWE
    if ("WEP" in caps) types += WifiSecurity.WEP
    if (types.isEmpty() && caps.contains("[ESS]")) types += WifiSecurity.OPEN
    return types
  }

  fun securityLabel(capabilities: String?): String {
    val labels = parseSecurityTypes(capabilities).map { it.label }
    return if (labels.isEmpty()) "—" else labels.joinToString(" / ")
  }

  fun securityRisk(capabilities: String?): String? {
    val caps = capabilities.orEmpty()
    return when {
      "WEP" in caps -> "WEP (broken, trivially crackable)"
      !caps.contains("WPA") && !caps.contains("RSN") && !caps.contains("SAE") && !caps.contains("OWE") -> "Open (unencrypted)"
      "TKIP" in caps -> "TKIP cipher (deprecated)"
      "WPA-" in caps && "WPA2" !in caps && "WPA3" !in caps -> "WPA-original (superseded by WPA2/3)"
      else -> null
    }
  }

  fun hasWps(capabilities: String?): Boolean = capabilities.orEmpty().let { "[WPS]" in it || "WPS]" in it }

  fun mfpStatus(capabilities: String?): String {
    val caps = capabilities.orEmpty()
    return when {
      "MFPR" in caps -> "Required"
      "MFPC" in caps -> "Capable"
      else -> "Not advertised"
    }
  }
}
