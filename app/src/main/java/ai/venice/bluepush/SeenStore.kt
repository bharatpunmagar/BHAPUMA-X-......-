package ai.venice.bluepush

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * भेटिएका MAC ठेगाना ३० दिनसम्म बचत गर्छ।
 * BT on मात्र भएको फोनलाई discoverable नभए पनि page गर्न यही आधार हो।
 */
object SeenStore {

    private const val TTL = 30L * 24 * 3600 * 1000
    private fun file(ctx: Context) = File(ctx.filesDir, "seen.json")

    private fun read(ctx: Context): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        runCatching {
            val arr = JSONArray(file(ctx).readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("mac")] = o.getLong("ts")
            }
        }
        return map
    }

    fun add(ctx: Context, mac: String) {
        val map = read(ctx)
        map[mac] = System.currentTimeMillis()
        write(ctx, map)
    }

    fun fresh(ctx: Context): List<String> {
        val now = System.currentTimeMillis()
        val map = read(ctx).filterValues { now - it < TTL }
        write(ctx, map.toMutableMap())
        return map.keys.toList()
    }

    fun forget(ctx: Context, mac: String) {
        val map = read(ctx)
        map.remove(mac)
        write(ctx, map)
    }

    private fun write(ctx: Context, map: Map<String, Long>) {
        val arr = JSONArray()
        map.forEach { (mac, ts) ->
            arr.put(JSONObject().put("mac", mac).put("ts", ts))
        }
        runCatching { file(ctx).writeText(arr.toString()) }
    }
}
