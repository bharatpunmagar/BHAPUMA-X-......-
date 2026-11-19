package ai.venice.bluepush

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object JobStore {

    private fun dir(ctx: Context) = File(ctx.filesDir, "out").apply { mkdirs() }

    fun copy(ctx: Context, uri: Uri, audio: Boolean): File {
        val name = displayName(ctx, uri)
            ?: if (audio) "venice-bata-swar.m4a" else "venice-bata-tasbir.jpg"
        val target = File(dir(ctx), sanitize(name))
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    private fun displayName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun sanitize(name: String) =
        name.replace(Regex("[^A-Za-z0-9._\\-\\u0900-\\u097F ]"), "_").take(80)
}
