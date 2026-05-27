package dev.thomasbuilds.spectre.scanner.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat

@Immutable
data class GattCharacteristicInfo(
  val uuid: String,
  val label: String?,
  val properties: List<String>,
  val readValue: String? = null
)

@Immutable
data class GattServiceInfo(
  val uuid: String,
  val label: String?,
  val characteristics: List<GattCharacteristicInfo>
)

@Immutable
sealed class GattInspection {
  data object Idle : GattInspection()

  data class Connecting(
    val mac: String
  ) : GattInspection()

  data class DiscoveringServices(
    val mac: String
  ) : GattInspection()

  data class ReadingValues(
    val mac: String,
    val done: Int,
    val total: Int
  ) : GattInspection()

  data class Done(
    val mac: String,
    val services: List<GattServiceInfo>
  ) : GattInspection()

  data class Failed(
    val mac: String,
    val reason: String
  ) : GattInspection()
}

class GattInspector(
  private val context: Context
) {
  private val manager: BluetoothManager? =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val adapter: BluetoothAdapter? get() = manager?.adapter

  // GATT callbacks arrive on binder threads and only one operation may be in flight at once, so
  // the connect/read/write state machine is serialized onto the main thread through this handler.
  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile private var gatt: BluetoothGatt? = null

  @Volatile private var session: Session? = null

  @Volatile private var pendingWrite: ((Boolean, String) -> Unit)? = null

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

  @SuppressLint("MissingPermission")
  fun inspect(
    mac: String,
    callback: (GattInspection) -> Unit
  ) {
    if (!hasPermission()) {
      callback(GattInspection.Failed(mac, "BLUETOOTH_CONNECT not granted"))
      return
    }
    val a = adapter
    if (a == null || !a.isEnabled) {
      callback(GattInspection.Failed(mac, "Bluetooth is off"))
      return
    }
    val device = runCatching { a.getRemoteDevice(mac) }.getOrNull()
    if (device == null) {
      callback(GattInspection.Failed(mac, "Invalid MAC"))
      return
    }

    session?.cancelled = true
    mainHandler.removeCallbacksAndMessages(null)
    runCatching {
      gatt?.disconnect()
      gatt?.close()
    }
    gatt = null

    callback(GattInspection.Connecting(mac))
    val s = Session(mac, callback)
    session = s

    val cb =
      object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
          g: BluetoothGatt,
          status: Int,
          newState: Int
        ) {
          when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
              s.deliver(GattInspection.DiscoveringServices(mac))
              runCatching { g.discoverServices() }
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
              mainHandler.post {
                when {
                  s.completed -> {
                    runCatching { g.close() }
                    if (gatt == g) gatt = null
                  }

                  status != BluetoothGatt.GATT_SUCCESS -> {
                    s.finishWithError(g, "Disconnected (status $status)")
                  }

                  else -> {
                    s.deliverDone(g, close = true)
                  }
                }
              }
            }
          }
        }

        override fun onServicesDiscovered(
          g: BluetoothGatt,
          status: Int
        ) {
          if (status != BluetoothGatt.GATT_SUCCESS) {
            mainHandler.post { s.finishWithError(g, "Service discovery failed (status $status)") }
            return
          }
          val services = g.services
          mainHandler.post {
            s.bind(g, services)
            s.startReading()
          }
        }

        override fun onCharacteristicRead(
          g: BluetoothGatt,
          characteristic: BluetoothGattCharacteristic,
          value: ByteArray,
          status: Int
        ) {
          val uuid = characteristic.uuid.toString()
          mainHandler.post { s.onRead(uuid, value, status) }
        }

        @Deprecated("API < 33 fallback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
          g: BluetoothGatt,
          characteristic: BluetoothGattCharacteristic,
          status: Int
        ) {
          val uuid = characteristic.uuid.toString()
          val value = characteristic.value ?: ByteArray(0)
          mainHandler.post { s.onRead(uuid, value, status) }
        }

        override fun onCharacteristicWrite(
          g: BluetoothGatt,
          characteristic: BluetoothGattCharacteristic,
          status: Int
        ) {
          val uuid = characteristic.uuid
          mainHandler.post {
            val cb = pendingWrite
            pendingWrite = null
            val ok = status == BluetoothGatt.GATT_SUCCESS
            val msg = if (ok) "Wrote $uuid" else "Write failed (status $status)"
            cb?.invoke(ok, msg)
          }
        }
      }

    @Suppress("DEPRECATION")
    val newGatt =
      device.connectGatt(
        context,
        false,
        cb,
        BluetoothDevice.TRANSPORT_LE,
        BluetoothDevice.PHY_LE_1M_MASK
      )
    gatt = newGatt
  }

  @SuppressLint("MissingPermission")
  fun cancel() {
    session?.cancelled = true
    pendingWrite = null
    mainHandler.removeCallbacksAndMessages(null)
    runCatching {
      gatt?.disconnect()
      gatt?.close()
    }
    gatt = null
    session = null
  }

  @SuppressLint("MissingPermission")
  fun write(
    serviceUuid: String,
    charUuid: String,
    value: ByteArray,
    withResponse: Boolean,
    onResult: (Boolean, String) -> Unit
  ) {
    if (!hasPermission()) {
      onResult(false, "BLUETOOTH_CONNECT not granted")
      return
    }
    val g = gatt
    if (g == null) {
      onResult(false, "Not connected. Inspect GATT first.")
      return
    }
    val service = g.services.firstOrNull { it.uuid.toString().equals(serviceUuid, ignoreCase = true) }
    val characteristic =
      service?.characteristics?.firstOrNull { it.uuid.toString().equals(charUuid, ignoreCase = true) }
    if (characteristic == null) {
      onResult(false, "Characteristic not found")
      return
    }
    val writeType =
      if (withResponse) {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
      } else {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
      }
    pendingWrite = onResult
    val code =
      runCatching { g.writeCharacteristic(characteristic, value, writeType) }
        .getOrElse {
          pendingWrite = null
          onResult(false, "Write threw: ${it.message}")
          return
        }
    if (code != BluetoothStatusCodes.SUCCESS) {
      pendingWrite = null
      onResult(false, "Write rejected (code $code)")
      return
    }
    mainHandler.postDelayed({
      if (pendingWrite === onResult) {
        pendingWrite = null
        onResult(false, "Write timed out (no confirmation)")
      }
    }, WRITE_TIMEOUT_MS)
  }

  private inner class Session(
    val mac: String,
    val callback: (GattInspection) -> Unit
  ) {
    @Volatile var completed: Boolean = false

    @Volatile var cancelled: Boolean = false
    private var gattRef: BluetoothGatt? = null
    private var services: List<BluetoothGattService> = emptyList()
    private val queue = ArrayDeque<BluetoothGattCharacteristic>()
    private val values = mutableMapOf<String, String>()
    private var totalReads = 0
    private var doneReads = 0
    private var timeoutToken: Runnable? = null

    fun bind(
      g: BluetoothGatt,
      svcs: List<BluetoothGattService>
    ) {
      gattRef = g
      services = svcs
      svcs.forEach { svc ->
        svc.characteristics.forEach { ch ->
          if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            queue.addLast(ch)
          }
        }
      }
      totalReads = queue.size
    }

    fun startReading() {
      deliver(GattInspection.ReadingValues(mac, 0, totalReads))
      readNext()
    }

    @SuppressLint("MissingPermission")
    private fun readNext() {
      if (cancelled || completed) return
      val next = queue.firstOrNull()
      val g = gattRef
      if (next == null || g == null) {
        deliverDone(g, close = false)
        return
      }
      val ok = runCatching { g.readCharacteristic(next) }.getOrDefault(false)
      if (!ok) {
        queue.removeFirst()
        doneReads++
        mainHandler.post { readNext() }
        return
      }
      armTimeout(next.uuid.toString())
    }

    fun onRead(
      uuid: String,
      value: ByteArray,
      status: Int
    ) {
      if (cancelled || completed) return
      disarmTimeout()
      if (queue.isEmpty()) return
      val expected = queue.first().uuid.toString()
      if (expected != uuid) return
      queue.removeFirst()
      doneReads++
      if (status == BluetoothGatt.GATT_SUCCESS) {
        val decoded =
          runCatching { GattValueDecoder.decode(uuid, value) }
            .getOrElse { value.joinToString(" ") { "%02x".format(it.toInt() and 0xff) } }
        values[uuid] = decoded
      }
      deliver(GattInspection.ReadingValues(mac, doneReads, totalReads))
      mainHandler.post { readNext() }
    }

    private fun armTimeout(uuid: String) {
      disarmTimeout()
      val token =
        Runnable {
          if (completed || cancelled) return@Runnable
          if (queue.isNotEmpty() && queue.first().uuid.toString() == uuid) {
            queue.removeFirst()
            doneReads++
            deliver(GattInspection.ReadingValues(mac, doneReads, totalReads))
            readNext()
          }
        }
      timeoutToken = token
      mainHandler.postDelayed(token, READ_TIMEOUT_MS)
    }

    private fun disarmTimeout() {
      timeoutToken?.let { mainHandler.removeCallbacks(it) }
      timeoutToken = null
    }

    @SuppressLint("MissingPermission")
    fun deliverDone(
      g: BluetoothGatt?,
      close: Boolean
    ) {
      if (completed) return
      completed = true
      disarmTimeout()
      val infos = services.map { it.toInfo(values) }
      deliver(GattInspection.Done(mac, infos))
      if (close) {
        runCatching {
          g?.disconnect()
          g?.close()
        }
        if (gatt == g) gatt = null
        if (session === this) session = null
      }
    }

    @SuppressLint("MissingPermission")
    fun finishWithError(
      g: BluetoothGatt?,
      reason: String
    ) {
      if (completed) return
      completed = true
      disarmTimeout()
      deliver(GattInspection.Failed(mac, reason))
      runCatching {
        g?.disconnect()
        g?.close()
      }
      if (gatt == g) gatt = null
      if (session === this) session = null
    }

    fun deliver(state: GattInspection) {
      if (Looper.myLooper() == Looper.getMainLooper()) {
        callback(state)
      } else {
        mainHandler.post { callback(state) }
      }
    }
  }

  private fun BluetoothGattService.toInfo(values: Map<String, String>): GattServiceInfo =
    GattServiceInfo(
      uuid = uuid.toString(),
      label = KnownGattUuids.serviceName(uuid.toString()),
      characteristics = characteristics.map { it.toInfo(values) }
    )

  private fun BluetoothGattCharacteristic.toInfo(values: Map<String, String>): GattCharacteristicInfo {
    val props =
      buildList {
        val p = properties
        if (p and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("broadcast")
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("read")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("write-no-resp")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("write")
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("notify")
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indicate")
        if (p and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("signed-write")
        if (p and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("extended")
      }
    return GattCharacteristicInfo(
      uuid = uuid.toString(),
      label = KnownGattUuids.characteristicName(uuid.toString()),
      properties = props,
      readValue = values[uuid.toString()]
    )
  }

  private companion object {
    const val READ_TIMEOUT_MS = 3_000L
    const val WRITE_TIMEOUT_MS = 4_000L
  }
}
