package com.rout.aiassistant.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.rout.aiassistant.commands.DeviceActions
import java.util.UUID

/**
 * Geofencing via Google Play services (free, no API key).
 * Needs precise location; for triggers while the app is closed Android also
 * requires background location ("Allow all the time") - the caller tells the
 * user honestly when that grant is missing.
 */
object GeofenceManager {

    sealed class AddResult {
        data class Added(val entry: GeofenceStore.Entry, val backgroundGranted: Boolean) : AddResult()
        object NoFinePermission : AddResult()
        data class PlaceNotFound(val place: String) : AddResult()
        data class Failed(val why: String) : AddResult()
    }

    fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun client(context: Context): GeofencingClient = LocationServices.getGeofencingClient(context)

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    /** Geocode a place name with the on-device Geocoder (needs Google Play services on the phone). */
    @Suppress("DEPRECATION")
    private fun geocode(context: Context, place: String): Pair<Double, Double>? = try {
        if (!Geocoder.isPresent()) null
        else Geocoder(context).getFromLocationName(place, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
    } catch (_: Exception) { null }

    fun add(context: Context, req: GeoReminders.Request): AddResult {
        if (!hasFine(context)) return AddResult.NoFinePermission
        val coords = if (req.useCurrentLocation) {
            DeviceActions.lastLocation(context) ?: return AddResult.PlaceNotFound("current location")
        } else {
            geocode(context, req.place) ?: return AddResult.PlaceNotFound(req.place)
        }
        val entry = GeofenceStore.Entry(
            id = UUID.randomUUID().toString().take(8),
            task = req.task, place = req.place,
            lat = coords.first, lon = coords.second,
            radius = 150f, onExit = req.onExit
        )
        return try {
            val transition = if (req.onExit) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER
            val geofence = Geofence.Builder()
                .setRequestId(entry.id)
                .setCircularRegion(entry.lat, entry.lon, entry.radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transition)
                .build()
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0) // never fire on the state at registration time
                .addGeofence(geofence)
                .build()
            client(context).addGeofences(request, pendingIntent(context))
                .addOnSuccessListener { GeofenceStore.put(context, entry) }
                .addOnFailureListener { }
            GeofenceStore.put(context, entry) // optimistic; receiver re-checks the store
            AddResult.Added(entry, hasBackground(context))
        } catch (ex: SecurityException) {
            AddResult.NoFinePermission
        } catch (ex: Exception) {
            AddResult.Failed(ex.javaClass.simpleName)
        }
    }

    fun cancel(context: Context, id: String) {
        try { client(context).removeGeofences(listOf(id)) } catch (_: Exception) {}
        GeofenceStore.remove(context, id)
    }

    /** Re-register everything after a reboot (geofences do not survive it). */
    fun reRegisterAll(context: Context) {
        if (!hasFine(context)) return
        val all = GeofenceStore.list(context)
        if (all.isEmpty()) return
        try {
            val geofences = all.map { e ->
                Geofence.Builder()
                    .setRequestId(e.id)
                    .setCircularRegion(e.lat, e.lon, e.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(if (e.onExit) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()
            }
            val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(geofences).build()
            client(context).addGeofences(request, pendingIntent(context))
        } catch (_: Exception) {}
    }
}
