package dev.thomasbuilds.spectre.recon

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

@Immutable
data class SsdpDevice(
  val ip: String,
  val location: String?,
  val server: String?,
  val usn: String
)

class SsdpDiscovery(
  private val context: Context,
  private val io: CoroutineDispatcher = Dispatchers.IO
) {
  fun scan(timeoutMs: Int = 4_000): Flow<SsdpDevice> =
    channelFlow {
      withContext(io) {
        val socket =
          DatagramSocket().apply {
            soTimeout = 250
            reuseAddress = true
          }
        LocalNetwork.pick(context)?.let { runCatching { it.bindSocket(socket) } }

        try {
          listOf("upnp:rootdevice", "ssdp:all").forEach { st ->
            val msg = SsdpParser.mSearch(st)
            runCatching {
              socket.send(
                DatagramPacket(
                  msg,
                  msg.size,
                  InetAddress.getByName(SsdpParser.MULTICAST_HOST),
                  SsdpParser.PORT
                )
              )
            }.onFailure { Log.w(TAG, "M-SEARCH send failed for $st: ${it.message}") }
          }

          val seen = HashSet<String>()
          val deadline = SystemClock.elapsedRealtime() + timeoutMs
          val buf = ByteArray(2048)
          while (SystemClock.elapsedRealtime() < deadline) {
            val packet = DatagramPacket(buf, buf.size)
            try {
              socket.receive(packet)
            } catch (_: SocketTimeoutException) {
              continue
            } catch (_: Throwable) {
              break
            }
            val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val device = SsdpParser.parse(raw, packet.address.hostAddress ?: continue) ?: continue
            if (seen.add(device.usn)) {
              trySend(device)
            }
          }
        } finally {
          runCatching { socket.close() }
        }
      }
    }

  private companion object {
    const val TAG = "SsdpDiscovery"
  }
}
