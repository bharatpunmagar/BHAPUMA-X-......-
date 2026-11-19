package ai.venice.bluepush

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: BluetoothAdapter
    private lateinit var picked: TextView
    private lateinit var logView: TextView
    private lateinit var btnStart: Button
    private var payload: File? = null
    private var audio = false

    private val perms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res.values.all { it }) startService() else toast("अनुमति चाहिन्छ")
        }

    private val picker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            val f = JobStore.copy(this, uri, audio)
            payload = f
            picked.text = "${f.name}  (${f.length() / 1024} KB)"
            btnStart.isEnabled = true
            if (f.length() > 5_000_000) picked.append("\nठूलो छ — सानो फाइल राम्रो")
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        adapter = getSystemService(BluetoothManager::class.java).adapter
        picked = findViewById(R.id.picked)
        logView = findViewById(R.id.log)
        btnStart = findViewById(R.id.btnStart)
        Log.sink = { line -> runOnUiThread { logView.append("$line\n") } }

        findViewById<View>(R.id.btnEnableBt).setOnClickListener {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        findViewById<View>(R.id.btnPhoto).setOnClickListener {
            audio = false
            picker.launch("image/*")
        }
        findViewById<View>(R.id.btnVoice).setOnClickListener {
            audio = true
            picker.launch("audio/*")
        }
        btnStart.setOnClickListener { ask() }
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(R.id.btBanner).visibility =
            if (adapter.isEnabled) View.GONE else View.VISIBLE
    }

    private fun ask() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) need += android.Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) {
            need += android.Manifest.permission.BLUETOOTH_SCAN
            need += android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            need += android.Manifest.permission.ACCESS_FINE_LOCATION
        }
        perms.launch(need.toTypedArray())
    }

    private fun startService() {
        val f = payload ?: return toast("पहिले फाइल छान्नुहोस्")
        if (!adapter.isEnabled) return toast("Bluetooth खोल्नुहोस्")
        ContextCompat.startForegroundService(this,
            Intent(this, BroadcastService::class.java)
                .putExtra(BroadcastService.EXTRA_FILE, f.absolutePath))
        toast("पठाउन सुरु भयो")
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
