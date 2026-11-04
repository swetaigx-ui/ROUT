package com.rout.aiassistant.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live connectivity with hysteresis. A callback-driven feed replaces polling:
 * transient network switches (wifi <-> cellular handover) no longer flicker the
 * UI to "Offline". Offline is declared only if no capable network exists 3s
 * after a loss; online recovers immediately.
 */
object NetworkStatus {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online
    private var registered = false
    private val handler = Handler(Looper.getMainLooper())

    @Synchronized
    fun start(context: Context) {
        if (registered) return
        registered = true
        _online.value = isOnline(context)
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = true }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) _online.value = true
                }
                override fun onLost(network: Network) {
                    handler.postDelayed({ _online.value = isOnline(context) }, 3000)
                }
                override fun onUnavailable() {
                    handler.postDelayed({ _online.value = isOnline(context) }, 3000)
                }
            })
        } catch (_: Exception) {
            _online.value = isOnline(context)
        }
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Last known location as a short text (for SMS anti-theft). Coarse/fine, whichever the OS grants. */
    fun lastKnownLocationText(ctx: Context): String {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val providers = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            var best: android.location.Location? = null
            for (p in providers) {
                try {
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (best == null || l.time > best!!.time)) best = l
                } catch (_: SecurityException) {}
            }
            if (best == null) "location nahi mili (GPS/permission off)"
            else "https://maps.google.com/?q=${best.latitude},${best.longitude} (accuracy ${best.accuracy.toInt()}m)"
        } catch (e: Exception) { "location error: ${e.message}" }
    }
}
