package dev.thomasbuilds.spectre.recon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Immutable
data class MdnsService(
  val name: String,
  val type: String,
  val port: Int
)

class MdnsBrowser(
  private val context: Context
) {
  private val nsd: NsdManager? =
    context.getSystemService(Context.NSD_SERVICE) as? NsdManager

  fun discoverAll(): Flow<MdnsService> =
    callbackFlow {
      val nsd =
        nsd ?: run {
          close()
          return@callbackFlow
        }
      val listeners = mutableListOf<NsdManager.DiscoveryListener>()
      SERVICE_TYPES.forEach { type ->
        val listener = makeListener { trySend(it) }
        listeners += listener
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
      }
      awaitClose {
        listeners.forEach { l ->
          runCatching { nsd.stopServiceDiscovery(l) }
        }
      }
    }

  private fun makeListener(onFound: (MdnsService) -> Unit): NsdManager.DiscoveryListener =
    object : NsdManager.DiscoveryListener {
      override fun onStartDiscoveryFailed(
        serviceType: String?,
        errorCode: Int
      ) {
        Log.w(TAG, "start discovery failed for $serviceType: errorCode=$errorCode")
      }

      override fun onStopDiscoveryFailed(
        serviceType: String?,
        errorCode: Int
      ) {
        Log.w(TAG, "stop discovery failed for $serviceType: errorCode=$errorCode")
      }

      override fun onDiscoveryStarted(serviceType: String?) {}

      override fun onDiscoveryStopped(serviceType: String?) {}

      override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}

      override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        onFound(
          MdnsService(
            name = serviceInfo.serviceName.orEmpty(),
            type = serviceInfo.serviceType.orEmpty(),
            port = serviceInfo.port
          )
        )
      }
    }

  private companion object {
    const val TAG = "MdnsBrowser"

    val SERVICE_TYPES =
      listOf(
        "_http._tcp",
        "_https._tcp",
        "_ssh._tcp",
        "_printer._tcp",
        "_ipp._tcp",
        "_ipps._tcp",
        "_airplay._tcp",
        "_raop._tcp",
        "_companion-link._tcp",
        "_googlecast._tcp",
        "_spotify-connect._tcp",
        "_hap._tcp",
        "_apple-mobdev2._tcp",
        "_smb._tcp",
        "_afpovertcp._tcp",
        "_workstation._tcp",
        "_device-info._tcp",
        "_plex._tcp",
        "_googlerpc._tcp"
      )
  }
}
