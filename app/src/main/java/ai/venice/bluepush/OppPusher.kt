package ai.venice.bluepush

import android.bluetooth.BluetoothDevice
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object OppPusher {

    private val OPP: Uri = Uri.parse("content://com.android.bluetooth.opp/btopp")

    fun push(ctx: Context, dev: BluetoothDevice, file: File): Boolean {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.grantUriPermission("com.android.bluetooth", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val cv = ContentValues().apply {
            put("uri", uri.toString())
            put("destination", dev.address)
            put("direction", 0)
            put("total_bytes", file.length())
            put("timestamp", System.currentTimeMillis())
        }
        return ctx.contentResolver.insert(OPP, cv) != null
    }
}
