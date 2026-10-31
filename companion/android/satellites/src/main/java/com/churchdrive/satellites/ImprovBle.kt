package com.churchdrive.satellites

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** How sending the Wi-Fi ended: [error] is a sentence for the person, or null when the satellite joined the network. */
data class ImprovOutcome(val error: String?)

/**
 * Sends a satellite the home Wi-Fi over Bluetooth with the Improv protocol (the satellite's `improv_ble`). Needs the Bluetooth
 * permissions already granted. Untested on a real satellite so far: every failure is turned into a sentence that says what to try.
 */
@SuppressLint("MissingPermission")
class ImprovBle(private val context: Context) {
    private sealed interface Event {
        data class Connection(val state: Int, val status: Int) : Event
        data class Services(val status: Int) : Event
        data class Descriptor(val status: Int) : Event
        data class Changed(val uuid: UUID, val value: ByteArray) : Event
        data class Written(val status: Int) : Event
        data class Mtu(val mtu: Int) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) { events.trySend(Event.Connection(newState, status)) }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { events.trySend(Event.Services(status)) }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { events.trySend(Event.Descriptor(status)) }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { events.trySend(Event.Written(status)) }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { events.trySend(Event.Mtu(mtu)) }

        // Android 13 and later call this one; earlier versions call the deprecated one below, with the value on the characteristic.
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            events.trySend(Event.Changed(c.uuid, value))
        }

        @Deprecated("Used before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            events.trySend(Event.Changed(c.uuid, c.value ?: ByteArray(0)))
        }
    }

    private suspend inline fun <reified T : Event> next(ms: Long, crossinline matches: (T) -> Boolean = { true }): T {
        var hit: T? = null
        withTimeout(ms) {
            while (hit == null) {
                val e = events.receive()
                if (e is T && matches(e)) hit = e
            }
        }
        @Suppress("UNCHECKED_CAST")
        return hit as T
    }

    /** Finds the satellite called [device] (its Bluetooth name is its device name), or the only one in range if none has that name. */
    private suspend fun find(device: String): BluetoothDevice? {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return null
        if (!adapter.isEnabled) return null
        val scanner = adapter.bluetoothLeScanner ?: return null
        val found = Channel<ScanResult>(Channel.UNLIMITED)
        val scan = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { found.trySend(result) }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Improv.SERVICE))).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scan,
        )
        val seen = mutableMapOf<String, ScanResult>()
        try {
            withTimeout(20_000) {
                while (true) {
                    val r = found.receive()
                    seen[r.device.address] = r
                    val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull()
                    if (name.equals(device, ignoreCase = true)) return@withTimeout
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { scanner.stopScan(scan) }
        }
        val exact = seen.values.firstOrNull { (it.scanRecord?.deviceName ?: runCatching { it.device.name }.getOrNull()).equals(device, ignoreCase = true) }
        return (exact ?: seen.values.singleOrNull())?.device
    }

    suspend fun provision(device: String, ssid: String, password: String, status: (String) -> Unit): ImprovOutcome {
        try {
            status("Looking for $device over Bluetooth…")
            val target = find(device)
                ?: return ImprovOutcome("Couldn't find $device over Bluetooth. Open Settings > Pair or reset on the satellite so its code is showing, check Bluetooth is on, and stand next to it.")
            status("Connecting to $device…")
            gatt = target.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            next<Event.Connection>(15_000) { it.state == BluetoothProfile.STATE_CONNECTED }
            gatt?.requestMtu(185)
            runCatching { next<Event.Mtu>(3_000) }
            gatt?.discoverServices()
            val services = next<Event.Services>(15_000)
            if (services.status != 0) return ImprovOutcome("Bluetooth connected but couldn't read the satellite's services. Try again.")
            val service = gatt?.getService(UUID.fromString(Improv.SERVICE))
                ?: return ImprovOutcome("That device doesn't offer Wi-Fi setup. It may already be on your Wi-Fi: reset its pairing and try again.")
            val state = service.getCharacteristic(UUID.fromString(Improv.CURRENT_STATE))
            val error = service.getCharacteristic(UUID.fromString(Improv.ERROR_STATE))
            val result = service.getCharacteristic(UUID.fromString(Improv.RPC_RESULT))
            val command = service.getCharacteristic(UUID.fromString(Improv.RPC_COMMAND))
            if (state == null || error == null || command == null) return ImprovOutcome("The satellite's Wi-Fi setup is incomplete. Its firmware may be too old.")

            for (c in listOfNotNull(state, error, result)) subscribe(c)

            // Authorised: the satellite says so once its pair screen is showing.
            status("Waiting for the satellite to allow it…")
            var current = Improv.STATE_AUTHORIZATION_REQUIRED
            val first = runCatching { next<Event.Changed>(2_000) { it.uuid == state.uuid } }.getOrNull()
            if (first != null && first.value.isNotEmpty()) current = first.value[0].toInt() and 0xFF
            else {
                @Suppress("DEPRECATION")
                gatt?.readCharacteristic(state)
                // A read comes back through the same callback on newer Android; if nothing arrives, assume it must still be authorised.
            }
            if (current < Improv.STATE_AUTHORIZED) {
                val got = runCatching { next<Event.Changed>(60_000) { it.uuid == state.uuid && it.value.isNotEmpty() && (it.value[0].toInt() and 0xFF) >= Improv.STATE_AUTHORIZED } }.getOrNull()
                    ?: return ImprovOutcome(Improv.errorText(0x04))
                current = got.value[0].toInt() and 0xFF
            }

            status("Sending the Wi-Fi…")
            write(command, Improv.wifiCommand(ssid, password))

            status("The satellite is joining the Wi-Fi…")
            while (true) {
                val e = next<Event.Changed>(60_000)
                val v = e.value.firstOrNull()?.toInt()?.and(0xFF) ?: continue
                when (e.uuid) {
                    error.uuid -> if (v != 0) return ImprovOutcome(Improv.errorText(v))
                    state.uuid -> if (v == Improv.STATE_PROVISIONED) return ImprovOutcome(null)
                    else -> if (result != null && e.uuid == result.uuid) return ImprovOutcome(null)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return ImprovOutcome("The satellite didn't answer in time. Stand next to it, keep its code showing and try again.")
        } catch (e: SecurityException) {
            return ImprovOutcome("Bluetooth permission is needed to set up a satellite.")
        } finally {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }
        @Suppress("UNREACHABLE_CODE")
        return ImprovOutcome("Something went wrong.")
    }

    private suspend fun subscribe(c: BluetoothGattCharacteristic) {
        val g = gatt ?: return
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) ?: return
        @Suppress("DEPRECATION")
        run {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(cccd)
        }
        next<Event.Descriptor>(5_000)
    }

    /** Writes in pieces that fit the Bluetooth packet size, one at a time. */
    private suspend fun write(c: BluetoothGattCharacteristic, bytes: ByteArray) {
        val g = gatt ?: return
        val piece = 18
        var i = 0
        while (i < bytes.size) {
            val part = bytes.copyOfRange(i, minOf(i + piece, bytes.size))
            @Suppress("DEPRECATION")
            run {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                c.value = part
                g.writeCharacteristic(c)
            }
            next<Event.Written>(5_000)
            i += piece
        }
    }
}
