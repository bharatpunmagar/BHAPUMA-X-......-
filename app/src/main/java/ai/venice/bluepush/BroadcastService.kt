package ai.venice.bluepush

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File

class BroadcastService : Service() {

    private lateinit var adapter: BluetoothAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val attempts = mutableMapOf<String, Int>()
    private lateinit var file: File

    companion object {
        const val EXTRA_FILE = "file"
        private const val NID = 42
        private const val CH = "bcast"
        private const val MAX_ATTEMPTS = 3
        private const val SWEEPS = 5
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        adapter = getSystemService(BluetoothManager::class.java).adapter
        channel()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, notif("तयार"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NID, notif("तयार"))
        }
        runCatching { adapter.name = "Venice" }
        observer()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val path = i?.getStringExtra(EXTRA_FILE) ?: return START_NOT_STICKY
        file = File(path)
        Log.add("सुरु: ${file.name} (${file.length() / 1024} KB)")
        scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        // १. cache — discoverable चाहिँदैन
        val cached = SeenStore.fresh(this)
        Log.add("cache मा ${cached.size} फोन")
        cached.forEach { push(it) }

        // २. नयाँ खोज
        repeat(SWEEPS) { sweep ->
            if (!adapter.isEnabled) { Log.add("BT बन्द भयो"); return@repeat }
            val found = discoverOnce(12_000)
            Log.add("sweep ${sweep + 1}: ${found.size} भेटिए")
            found.filter { it !in cached }.forEach {
                SeenStore.add(this, it)
                push(it)
            }
            delay(if (sweep < 3) 8_000L else 25_000L)
        }
        Log.add("sweep सकियो, retry क्रममा")
        delay(20_000)
        if (attempts.values.none { it < MAX_ATTEMPTS }) {
            Log.add("काम सकियो")
            stopSelf()
        }
    }

    private fun push(mac: String) {
        if ((attempts[mac] ?: 0) >= MAX_ATTEMPTS) return
        val dev = runCatching { adapter.getRemoteDevice(mac) }.getOrNull() ?: return
        adapter.cancelDiscovery()
        val ok = runCatching { OppPusher.push(this, dev, file) }.getOrDefault(false)
        Log.add(if (ok) "→ $mac" else "× $mac (fail)")
        if (!ok) retry(mac)
    }

    private fun retry(mac: String) {
        val n = attempts[mac] ?: 0
        attempts[mac] = n + 1
        if (n + 1 >= MAX_ATTEMPTS) { Log.add("$mac छोडियो"); return }
        scope.launch {
            delay(15_000L * (n + 1))
            if (adapter.isEnabled) push(mac)
        }
    }

    private suspend fun discoverOnce(timeoutMs: Long): List<String> =
        withTimeoutOrNull(timeoutMs) {
            val found = mutableListOf<String>()
            val rx = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?.address?.let { if (it !in found) found += it }
                }
            }
            ContextCompat.registerReceiver(this@BroadcastService, rx,
                IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
            try {
                adapter.startDiscovery()
                awaitCancellation()
            } finally {
                runCatching { adapter.cancelDiscovery() }
                runCatching { unregisterReceiver(rx) }
            }
        } ?: emptyList()

    /** accept / decline थाहा पाउन OPP तालिका पछ्याउने */
    private fun observer() {
        contentResolver.registerContentObserver(
            Uri.parse("content://com.android.bluetooth.opp/btopp"), true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(self: Boolean, uri: Uri?) {
                    uri ?: return
                    runCatching {
                        contentResolver.query(uri,
                            arrayOf("destination", "status"), null, null, null)?.use { c ->
                            if (!c.moveToFirst()) return
                            val mac = c.getString(0) ?: return
                            when (c.getInt(1)) {
                                2 -> { attempts.remove(mac); Log.add("✓ $mac स्वीकार") }
                                3, 4, 5 -> retry(mac)
                            }
                        }
                    }
                }
            })
    }

    private fun channel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Broadcast", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notif(text: String) =
        NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BluePush चलिरहेको छ")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE))
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
