package dev.thomasbuilds.spectre.recon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.system.ErrnoException
import android.system.OsConstants
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

@Immutable
data class SubnetInfo(
  val localIp: String,
  val gatewayIp: String?,
  val prefixLength: Int,
  val interfaceName: String?
)

@Immutable
data class HostInfo(
  val ip: String,
  val hostname: String?,
  val openPorts: List<Int>,
  val banners: Map<Int, String> = emptyMap(),
  val rtMs: Long,
  val ssdpServer: String? = null,
  val ssdpLocation: String? = null
)

class LanScanner(
  private val context: Context,
  private val io: CoroutineDispatcher = Dispatchers.IO
) {
  @Volatile var lastDiagnostic: String = ""
    private set

  @Volatile var preferredNetwork: Network? = null
    private set

  @Volatile var networkPermissionLikelyDenied: Boolean = false
    private set

  @Volatile var localAccessBlocked: Boolean = false
    private set

  private fun isVpnLikeInterface(name: String): Boolean {
    val lower = name.lowercase()
    return VPN_INTERFACE_PREFIXES.any { lower.startsWith(it) }
  }

  private fun probeNetworkPermission(): Boolean {
    val ifaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
    return ifaces != null
  }

  fun currentSubnet(): SubnetInfo? {
    val diag = StringBuilder()

    fun log(msg: String) {
      diag.appendLine(msg)
    }

    log("─── currentSubnet() probe ───")

    val canEnumerate = probeNetworkPermission()
    log("NetworkInterface.getNetworkInterfaces accessible: $canEnumerate")
    val cspGranted =
      context.checkSelfPermission(Manifest.permission.INTERNET) ==
        PackageManager.PERMISSION_GRANTED
    log("checkSelfPermission(INTERNET): ${if (cspGranted) "GRANTED" else "DENIED"} (informational)")
    networkPermissionLikelyDenied = !canEnumerate

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    log("ConnectivityManager: ${cm != null}")
    @Suppress("DEPRECATION")
    val allNets: List<Network> = cm?.allNetworks?.toList() ?: emptyList()
    log("allNetworks: ${allNets.size}")
    allNets.forEach { n ->
      val caps = cm?.getNetworkCapabilities(n)
      val transports =
        listOf(
          NetworkCapabilities.TRANSPORT_WIFI to "WIFI",
          NetworkCapabilities.TRANSPORT_CELLULAR to "CELLULAR",
          NetworkCapabilities.TRANSPORT_ETHERNET to "ETHERNET",
          NetworkCapabilities.TRANSPORT_VPN to "VPN"
        ).filter { (t, _) -> caps?.hasTransport(t) == true }
          .joinToString(",") { it.second }
      log("  net $n  transports=[$transports]")
    }

    val wifiNet =
      allNets.firstOrNull { n ->
        val caps = cm?.getNetworkCapabilities(n) ?: return@firstOrNull false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
          !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
      }
    val ethernetNet =
      allNets.firstOrNull { n ->
        val caps = cm?.getNetworkCapabilities(n) ?: return@firstOrNull false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
          !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
      }
    val activeNet = cm?.activeNetwork
    val targetNet = wifiNet ?: ethernetNet ?: activeNet
    val pickReason =
      when {
        wifiNet != null -> "WiFi (preferred over VPN)"
        ethernetNet != null -> "Ethernet (preferred over VPN)"
        activeNet != null -> "activeNetwork fallback"
        else -> "(no network)"
      }
    log("picked network: $targetNet ($pickReason)")
    preferredNetwork = targetNet

    if (cm != null && targetNet != null) {
      val lp = cm.getLinkProperties(targetNet)
      log("linkProperties: $lp")
      if (lp != null) {
        log("  interfaceName: ${lp.interfaceName}")
        log("  linkAddresses: ${lp.linkAddresses}")
        val v4 = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
        log("  v4: $v4")
        if (v4 != null) {
          val ip = v4.address.hostAddress
          log("  v4.ip: $ip")
          if (ip != null) {
            val gateway =
              lp.routes
                .firstOrNull { it.isDefaultRoute }
                ?.gateway
                ?.hostAddress
            log("  gateway: $gateway")
            log("✓ resolved via ConnectivityManager ($pickReason)")
            lastDiagnostic = diag.toString()
            return SubnetInfo(
              localIp = ip,
              gatewayIp = gateway,
              prefixLength = v4.prefixLength,
              interfaceName = lp.interfaceName
            )
          }
        }
      }
    }
    log("✗ ConnectivityManager path didn't resolve, trying NetworkInterface fallback")

    val ifaces =
      runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
        .onFailure { log("NetworkInterface.getNetworkInterfaces threw: ${it.message}") }
        .getOrNull()
    log("NetworkInterface count: ${ifaces?.size}")
    if (ifaces.isNullOrEmpty()) {
      lastDiagnostic = diag.toString()
      return null
    }
    ifaces.forEach { iface ->
      val flags =
        buildList {
          if (runCatching { iface.isUp }.getOrDefault(false)) add("up")
          if (runCatching { iface.isLoopback }.getOrDefault(false)) add("loopback")
          if (runCatching { iface.isVirtual }.getOrDefault(false)) add("virtual")
          if (runCatching { iface.isPointToPoint }.getOrDefault(false)) add("p2p")
        }.joinToString(",").ifEmpty { "no-flags" }
      log("  iface ${iface.name}  [$flags]")
      iface.interfaceAddresses.forEach { ia ->
        val a = ia.address
        val kind =
          when {
            a is Inet4Address -> "v4"
            else -> "v6"
          }
        val tags =
          buildList {
            if (a.isLoopbackAddress) add("loopback")
            if (a.isLinkLocalAddress) add("link-local")
            if (a.isAnyLocalAddress) add("any")
          }.joinToString(",").ifEmpty { "global" }
        log("      $kind ${a.hostAddress}/${ia.networkPrefixLength}  [$tags]")
      }
    }

    val pick =
      ifaces
        .asSequence()
        .filter { iface ->
          val up = runCatching { iface.isUp }.getOrDefault(false)
          val lo = runCatching { iface.isLoopback }.getOrDefault(false)
          up && !lo && !isVpnLikeInterface(iface.name)
        }.sortedByDescending { it.name.startsWith("wlan", ignoreCase = true) }
        .flatMap { iface ->
          iface.interfaceAddresses.asSequence().map { iface to it }
        }.firstOrNull { (_, ia) ->
          val a = ia.address
          a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress
        }

    if (pick == null) {
      log("✗ NetworkInterface fallback: no usable IPv4 found")
      lastDiagnostic = diag.toString()
      return null
    }
    val (iface, ia) = pick
    val ip = ia.address.hostAddress
    if (ip == null) {
      log("✗ NetworkInterface fallback: picked addr has null hostAddress")
      lastDiagnostic = diag.toString()
      return null
    }
    log("✓ resolved via NetworkInterface fallback (${iface.name})")
    lastDiagnostic = diag.toString()
    return SubnetInfo(
      localIp = ip,
      gatewayIp = null,
      prefixLength = ia.networkPrefixLength.toInt(),
      interfaceName = iface.name
    )
  }

  fun scanHosts(
    subnet: SubnetInfo,
    probePorts: List<Int> = DISCOVERY_PORTS,
    timeoutMs: Int = 600,
    concurrency: Int = 32
  ): Flow<HostInfo> =
    channelFlow {
      localAccessBlocked = false
      val ips = enumerateSubnet(subnet) ?: return@channelFlow
      ips.chunked(concurrency).forEach { chunk ->
        coroutineScope {
          chunk
            .map { ip ->
              async(io) { probeHost(ip, probePorts, timeoutMs) }
            }.awaitAll()
            .filterNotNull()
            .forEach { send(it) }
        }
      }
    }

  private suspend fun probeHost(
    ip: String,
    ports: List<Int>,
    timeoutMs: Int
  ): HostInfo? {
    val start = System.currentTimeMillis()
    val open =
      coroutineScope {
        ports
          .map { port ->
            async(io) { if (tryConnect(ip, port, timeoutMs)) port else null }
          }.awaitAll()
          .filterNotNull()
      }
    if (open.isEmpty()) return null

    val banners =
      coroutineScope {
        open
          .map { port ->
            async(io) { port to grabBanner(ip, port, BANNER_TIMEOUT_MS) }
          }.awaitAll()
          .mapNotNull { (p, b) -> b?.let { p to it } }
          .toMap()
      }

    val rt = System.currentTimeMillis() - start
    val hostname =
      withTimeoutOrNull(RDNS_TIMEOUT_MS) {
        withContext(io) {
          runCatching {
            InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
          }.getOrNull()
        }
      }
    return HostInfo(
      ip = ip,
      hostname = hostname,
      openPorts = open.sorted(),
      banners = banners,
      rtMs = rt
    )
  }

  private fun grabBanner(
    host: String,
    port: Int,
    timeoutMs: Int
  ): String? {
    if (port in TLS_PORTS) return null
    val httpFirst = port in HTTP_PROBE_PORTS
    val first = readProbe(host, port, timeoutMs, if (httpFirst) httpGet(host) else null)
    ServiceFingerprinter.identify(first, port)?.let { return it }
    val second = readProbe(host, port, timeoutMs, if (httpFirst) null else httpGet(host))
    ServiceFingerprinter.identify(second, port)?.let { return it }
    return (first ?: second)
      ?.lineSequence()
      ?.firstOrNull()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.take(120)
  }

  private fun readProbe(
    host: String,
    port: Int,
    timeoutMs: Int,
    payload: ByteArray?
  ): String? =
    runCatching {
      bindToWifi(Socket()).use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = timeoutMs
        if (payload != null) {
          socket.getOutputStream().apply {
            write(payload)
            flush()
          }
        }
        val buf = ByteArray(4096)
        val read = socket.getInputStream().read(buf)
        if (read <= 0) null else String(buf, 0, read, Charsets.ISO_8859_1)
      }
    }.onFailure { if (isPolicyBlock(it)) localAccessBlocked = true }.getOrNull()

  private fun httpGet(host: String): ByteArray =
    "GET / HTTP/1.0\r\nHost: $host\r\nUser-Agent: Spectre-Recon\r\nConnection: close\r\n\r\n"
      .toByteArray(Charsets.US_ASCII)

  private fun tryConnect(
    host: String,
    port: Int,
    timeoutMs: Int
  ): Boolean =
    try {
      bindToWifi(Socket()).use {
        it.connect(InetSocketAddress(host, port), timeoutMs)
        true
      }
    } catch (e: Throwable) {
      if (isPolicyBlock(e)) localAccessBlocked = true
      false
    }

  private fun isPolicyBlock(error: Throwable): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
      if (cause is ErrnoException) {
        return cause.errno == OsConstants.EPERM || cause.errno == OsConstants.EACCES
      }
      cause = cause.cause
    }
    return false
  }

  // Bind probe sockets to the chosen Wi-Fi/Ethernet network so recon stays on that LAN and
  // never escapes over cellular or a VPN.
  private fun bindToWifi(socket: Socket): Socket {
    val net = preferredNetwork ?: return socket
    runCatching { net.bindSocket(socket) }
    return socket
  }

  private fun enumerateSubnet(subnet: SubnetInfo): List<String>? {
    if (subnet.prefixLength !in 22..30) return null
    val parts = subnet.localIp.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return null
    val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    val maskInt = if (subnet.prefixLength == 0) 0 else (-1 shl (32 - subnet.prefixLength))
    val network = ipInt and maskInt
    val broadcast = network or maskInt.inv()
    val firstHost = (network + 1).toLong() and 0xFFFFFFFFL
    val lastHost = (broadcast - 1).toLong() and 0xFFFFFFFFL
    return (firstHost..lastHost).map { addr ->
      val a = (addr shr 24) and 0xFF
      val b = (addr shr 16) and 0xFF
      val c = (addr shr 8) and 0xFF
      val d = addr and 0xFF
      "$a.$b.$c.$d"
    }
  }

  companion object {
    private val VPN_INTERFACE_PREFIXES =
      listOf("tun", "tap", "ppp", "wireguard", "wg", "ipsec", "nordlynx")

    private const val BANNER_TIMEOUT_MS = 600
    private const val RDNS_TIMEOUT_MS = 500L

    private val HTTP_PROBE_PORTS = setOf(80, 631, 5000, 8000, 8008, 8080, 8443, 9100, 32400)

    private val TLS_PORTS = setOf(443, 636, 853, 989, 990, 993, 995, 8883)

    val DISCOVERY_PORTS =
      listOf(22, 53, 80, 139, 443, 445, 515, 548, 554, 631, 1900, 2049, 3389, 5000, 5353, 5900, 8000, 8008, 8080, 8443, 9100, 32400)

    fun labelForPort(port: Int): String =
      when (port) {
        22 -> "SSH"
        23 -> "Telnet"
        25 -> "SMTP"
        53 -> "DNS"
        67, 68 -> "DHCP"
        80 -> "HTTP"
        110 -> "POP3"
        123 -> "NTP"
        135 -> "MS-RPC"
        137, 138 -> "NetBIOS"
        139, 445 -> "SMB"
        143 -> "IMAP"
        161 -> "SNMP"
        389 -> "LDAP"
        443 -> "HTTPS"
        500 -> "IPsec"
        515 -> "LPD"
        548 -> "AFP"
        554 -> "RTSP"
        631 -> "IPP"
        636 -> "LDAPS"
        853 -> "DoT"
        993 -> "IMAPS"
        995 -> "POP3S"
        1080 -> "SOCKS"
        1194 -> "OpenVPN"
        1433 -> "MSSQL"
        1521 -> "Oracle"
        1883 -> "MQTT"
        1900 -> "SSDP"
        2049 -> "NFS"
        2375, 2376 -> "Docker"
        3306 -> "MySQL"
        3389 -> "RDP"
        5000 -> "UPnP"
        5060 -> "SIP"
        5353 -> "mDNS"
        5432 -> "PostgreSQL"
        5900 -> "VNC"
        6379 -> "Redis"
        8000 -> "HTTP-alt"
        8008, 8009 -> "Chromecast"
        8080 -> "HTTP-proxy"
        8443 -> "HTTPS-alt"
        8883 -> "MQTTS"
        9100 -> "JetDirect"
        27017 -> "MongoDB"
        32400 -> "Plex"
        else -> ""
      }
  }
}
