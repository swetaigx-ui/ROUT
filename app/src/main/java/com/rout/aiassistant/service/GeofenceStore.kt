package com.rout.aiassistant.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** JSON persistence for active location reminders; survives reboots for re-registration. */
object GeofenceStore {

    data class Entry(
        val id: String, val task: String, val place: String,
        val lat: Double, val lon: Double, val radius: Float, val onExit: Boolean
    )

    private fun file(context: Context) = File(context.filesDir, "geofences.json")

    @Synchronized
    fun list(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    o.getString("id"), o.getString("task"), o.getString("place"),
                    o.getDouble("lat"), o.getDouble("lon"),
                    o.optDouble("radius", 150.0).toFloat(), o.optBoolean("onExit", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun put(context: Context, e: Entry) {
        val all = list(context).filter { it.id != e.id } + e
        val arr = JSONArray()
        all.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id); put("task", g.task); put("place", g.place)
                put("lat", g.lat); put("lon", g.lon); put("radius", g.radius.toDouble()); put("onExit", g.onExit)
            })
        }
        file(context).writeText(arr.toString())
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val all = list(context).filter { it.id != id }
        val arr = JSONArray()
        all.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id); put("task", g.task); put("place", g.place)
                put("lat", g.lat); put("lon", g.lon); put("radius", g.radius.toDouble()); put("onExit", g.onExit)
            })
        }
        file(context).writeText(arr.toString())
    }

    @Synchronized
    fun find(context: Context, id: String): Entry? = list(context).firstOrNull { it.id == id }
}
