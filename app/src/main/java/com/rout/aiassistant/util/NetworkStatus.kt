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
}
