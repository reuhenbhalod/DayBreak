package com.daybreak.ble

import android.annotation.SuppressLint
import android.util.Log
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import com.daybreak.protocol.BatteryInfo
import com.daybreak.protocol.BatteryParser
import com.daybreak.protocol.BigData
import com.daybreak.protocol.BigDataReassembler
import com.daybreak.protocol.DailyActivitySummary
import com.daybreak.protocol.HeartRateLog
import com.daybreak.protocol.HeartRateLogAssembler
import com.daybreak.protocol.RealTimeKind
import com.daybreak.protocol.RealTimeParser
import com.daybreak.protocol.RealTimeReading
import com.daybreak.protocol.Requests
import com.daybreak.protocol.SleepDay
import com.daybreak.protocol.SleepParser
import com.daybreak.protocol.Spo2Day
import com.daybreak.protocol.Spo2Parser
import com.daybreak.protocol.StepsAssembler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Real COLMI R09 client over native [BluetoothGatt] (no third-party BLE dependency).
 *
 * Two channels are used: the 16-byte UART commands (battery/HR/steps) over the V1
 * service, and the variable-length "big data" sleep message over the V2 service. The
 * packet codec it drives (`:protocol`) is fully unit-tested; this transport class is
 * thin and can only be verified against the physical ring. Requests are serialized by
 * [ioMutex] so one exchange completes before the next begins.
 */
@SuppressLint("MissingPermission")
class RealRingClient(private val context: Context) : RingClient {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var uartWrite: BluetoothGattCharacteristic? = null
    private var bigDataWrite: BluetoothGattCharacteristic? = null

    private val ioMutex = Mutex()

    private val _events = MutableStateFlow<List<String>>(emptyList())
    override val events: StateFlow<List<String>> = _events.asStateFlow()

    /** Log an event both to logcat and the on-screen diagnostics log. */
    private fun ev(line: String) {
        Log.i(TAG, line)
        _events.update { (it + line).takeLast(300) }
    }

    /** Active only during a request; receives (sourceCharacteristicUuid, payload). */
    @Volatile
    private var sink: ((UUID, ByteArray) -> Unit)? = null

    private var connectResult: CompletableDeferred<Boolean>? = null

    // region connection ---------------------------------------------------------------

    override suspend fun connect(): Boolean {
        if (uartWrite != null) return true
        if (adapter?.isEnabled != true) return false
        if (!BlePermissions.allGranted(context)) return false
        // Prefer an already-bonded ring: once paired it stops advertising, so a scan
        // won't find it — but we can connect to the bonded device by address directly.
        val device = bondedRing() ?: scanForRing() ?: return false
        return openGatt(device)
    }

    private fun bondedRing(): BluetoothDevice? =
        adapter?.bondedDevices?.firstOrNull { it.name?.let(::looksLikeRing) == true }

    override suspend fun disconnect() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        uartWrite = null
        bigDataWrite = null
    }

    private suspend fun scanForRing(): BluetoothDevice? {
        val scanner = adapter?.bluetoothLeScanner ?: return null
        val found = CompletableDeferred<BluetoothDevice?>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device?.name ?: result.scanRecord?.deviceName
                if (name != null && looksLikeRing(name) && !found.isCompleted) {
                    found.complete(result.device)
                }
            }
        }
        scanner.startScan(callback)
        val device = withTimeoutOrNull(SCAN_TIMEOUT_MS) { found.await() }
        runCatching { scanner.stopScan(callback) }
        return device
    }

    private fun looksLikeRing(name: String): Boolean =
        name.contains("R0", ignoreCase = true) || name.contains("COLMI", ignoreCase = true)

    private suspend fun openGatt(device: BluetoothDevice): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        connectResult = deferred
        gatt = device.connectGatt(context, false, gattCallback)
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { deferred.await() } ?: false
    }

    private var bigDataNotifyChar: BluetoothGattCharacteristic? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            ev("conn state: status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                uartWrite = null
                connectResult?.takeIf { !it.isCompleted }?.complete(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val uartTx = findCharacteristic(gatt, UART_TX_UUID)
            uartWrite = findCharacteristic(gatt, UART_RX_UUID)
            bigDataWrite = findCharacteristic(gatt, BIG_DATA_COMMAND_UUID)
            bigDataNotifyChar = findCharacteristic(gatt, BIG_DATA_NOTIFY_UUID)
            ev(
                "services: status=$status uartRx=${uartWrite != null} uartTx=${uartTx != null} " +
                    "bigCmd=${bigDataWrite != null} bigNotify=${bigDataNotifyChar != null}",
            )
            if (uartWrite == null || uartTx == null) {
                connectResult?.takeIf { !it.isCompleted }?.complete(false)
                return
            }
            // Subscribe to UART notifications first, then big-data, each waiting for its
            // CCCD write callback. Connect is only "ready" once BOTH are done — otherwise a
            // pending descriptor write keeps the GATT stack busy and rejects command writes.
            if (!enableNotifications(gatt, uartTx)) subscribeBigDataOrReady(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: android.bluetooth.BluetoothGattDescriptor, status: Int) {
            ev("descriptor write: char=${descriptor.characteristic.uuid} status=$status")
            when (descriptor.characteristic.uuid) {
                UART_TX_UUID -> subscribeBigDataOrReady(gatt)
                BIG_DATA_NOTIFY_UUID -> markReady()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            ev("write done: char=${characteristic.uuid} status=$status")
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { onPacket(characteristic.uuid, it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onPacket(characteristic.uuid, value)
        }
    }

    private fun onPacket(uuid: UUID, value: ByteArray) {
        ev("rx ${uuid.toString().substring(0, 8)}: ${value.toHex()}")
        sink?.invoke(uuid, value)
    }

    /** After UART is subscribed, subscribe big-data; if there's nothing to subscribe, ready now. */
    private fun subscribeBigDataOrReady(gatt: BluetoothGatt) {
        val notify = bigDataNotifyChar
        if (notify != null && enableNotifications(gatt, notify)) return // wait for its onDescriptorWrite
        markReady()
    }

    private fun markReady() {
        ev("ready")
        connectResult?.takeIf { !it.isCompleted }?.complete(true)
    }

    private fun findCharacteristic(gatt: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? {
        for (service in gatt.services) {
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    /** Returns true if a CCCD write was started (so the caller can await onDescriptorWrite). */
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
        return true
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

    // endregion

    // region requests -----------------------------------------------------------------

    /**
     * Write [payload] to [writeChar], then feed notifications from [listenUuid] to
     * [onPacket] until it returns non-null or the timeout elapses. Serialized.
     */
    private suspend fun <T> request(
        writeChar: BluetoothGattCharacteristic?,
        listenUuid: UUID,
        payload: ByteArray,
        timeoutMs: Long,
        writeType: Int,
        onPacket: (ByteArray) -> T?,
    ): T? = ioMutex.withLock {
        val target = writeChar ?: return null
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        sink = { uuid, value -> if (uuid == listenUuid) runCatching { channel.trySend(value) } }
        try {
            ev("tx ${payload.joinToString(" ") { "%02X".format(it) }} (listen=${listenUuid.toString().substring(0, 8)})")
            if (!writeRaw(target, payload, writeType)) {
                ev("write failed")
                return null
            }
            withTimeoutOrNull(timeoutMs) {
                var result: T? = null
                while (result == null) {
                    result = onPacket(channel.receive())
                }
                result
            }
        } finally {
            sink = null
            channel.close()
        }
    }

    private fun writeRaw(characteristic: BluetoothGattCharacteristic, payload: ByteArray, writeType: Int): Boolean {
        val g = gatt ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(characteristic, payload, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = writeType
                characteristic.value = payload
                g.writeCharacteristic(characteristic)
            }
        }
    }

    override suspend fun setTime() {
        val now = java.time.LocalDateTime.now()
        val packet = Requests.setTime(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second)
        fireAndForget(uartWrite, packet)
    }

    /** Write a command we don't expect a reply to, holding the lock long enough to flush. */
    private suspend fun fireAndForget(characteristic: BluetoothGattCharacteristic?, payload: ByteArray) =
        ioMutex.withLock {
            val target = characteristic ?: return@withLock
            ev("tx(set) ${payload.joinToString(" ") { "%02X".format(it) }}")
            if (writeRaw(target, payload, WRITE_NO_RESPONSE)) kotlinx.coroutines.delay(300)
        }

    override suspend fun measureHeartRate(onReading: (Int) -> Unit): Int? = ioMutex.withLock {
        val target = uartWrite ?: return@withLock null
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        sink = { uuid, value -> if (uuid == UART_TX_UUID) runCatching { channel.trySend(value) } }
        var last: Int? = null
        try {
            ev("tx(rt-start) ${Requests.startRealTime(RealTimeKind.HEART_RATE).toHex()}")
            if (!writeRaw(target, Requests.startRealTime(RealTimeKind.HEART_RATE), WRITE_NO_RESPONSE)) {
                return@withLock null
            }
            withTimeoutOrNull(REALTIME_TIMEOUT_MS) {
                var stable = 0
                while (stable < REQUIRED_STABLE) {
                    when (val r = RealTimeParser.parse(channel.receive())) {
                        is RealTimeReading.Value -> {
                            if (r.kind == RealTimeKind.HEART_RATE && r.value > 0) {
                                last = r.value
                                onReading(r.value)
                                stable++
                            }
                        }
                        is RealTimeReading.Err -> {
                            ev("rt error code=${r.code}")
                            return@withTimeoutOrNull
                        }
                        null -> {} // not a real-time packet; ignore
                    }
                }
            }
            last
        } finally {
            runCatching { writeRaw(target, Requests.stopRealTime(RealTimeKind.HEART_RATE), WRITE_NO_RESPONSE) }
            sink = null
            channel.close()
        }
    }

    override suspend fun fetchBattery(): BatteryInfo? =
        request(uartWrite, UART_TX_UUID, Requests.battery(), REQUEST_TIMEOUT_MS, WRITE_NO_RESPONSE) {
            BatteryParser.parse(it)
        }

    override suspend fun fetchHeartRateLog(epochSecondsOfDay: Long): HeartRateLog? {
        val assembler = HeartRateLogAssembler()
        val result = request(uartWrite, UART_TX_UUID, Requests.readHeartRate(epochSecondsOfDay), LOG_TIMEOUT_MS, WRITE_NO_RESPONSE) {
            val done = assembler.offer(it)
            if (done) assembler.result() else null
        }
        ev("hr parsed: ${result?.rates?.count { r -> r > 0 } ?: -1} readings, noData=${assembler.noData}")
        return result
    }

    override suspend fun fetchDailyActivity(dayOffset: Int): DailyActivitySummary? {
        val assembler = StepsAssembler()
        return request(uartWrite, UART_TX_UUID, Requests.getSteps(dayOffset), LOG_TIMEOUT_MS, WRITE_NO_RESPONSE) {
            if (assembler.offer(it)) assembler.summary() else null
        }
    }

    override suspend fun fetchSleep(): List<SleepDay>? {
        val reassembler = BigDataReassembler()
        val payload = request(
            bigDataWrite, BIG_DATA_NOTIFY_UUID, BigData.sleepRequest(), LOG_TIMEOUT_MS, WRITE_DEFAULT,
        ) {
            if (reassembler.offer(it)) reassembler.payload() else null
        } ?: return null
        return SleepParser.parse(payload)
    }

    override suspend fun fetchSpo2(): List<Spo2Day>? {
        val reassembler = BigDataReassembler()
        val payload = request(
            bigDataWrite, BIG_DATA_NOTIFY_UUID, BigData.spo2Request(), LOG_TIMEOUT_MS, WRITE_DEFAULT,
        ) {
            if (reassembler.offer(it)) reassembler.payload() else null
        } ?: return null
        return Spo2Parser.parse(payload)
    }

    // endregion

    private companion object {
        const val TAG = "RealRingClient"
        val UART_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val UART_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val BIG_DATA_NOTIFY_UUID: UUID = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
        val BIG_DATA_COMMAND_UUID: UUID = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        const val WRITE_NO_RESPONSE = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        const val WRITE_DEFAULT = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        const val SCAN_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val LOG_TIMEOUT_MS = 15_000L
        const val REALTIME_TIMEOUT_MS = 30_000L
        const val REQUIRED_STABLE = 3
    }
}
