package dev.thomasbuilds.spectre.recon

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

internal object LocalNetwork {
  fun pick(context: Context): Network? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null

    @Suppress("DEPRECATION")
    val nets = cm.allNetworks

    fun firstWith(transport: Int) =
      nets.firstOrNull { n ->
        val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
        c.hasTransport(transport) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
      }
    return firstWith(NetworkCapabilities.TRANSPORT_WIFI) ?: firstWith(NetworkCapabilities.TRANSPORT_ETHERNET)
  }
}
