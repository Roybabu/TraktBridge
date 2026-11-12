package com.traktbridge.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object DeviceNetwork {
    fun local(context:Context):Network? {
        val cm=context.getSystemService(ConnectivityManager::class.java)
        val network=cm.activeNetwork ?: return null
        val caps=cm.getNetworkCapabilities(network) ?: return null
        return network.takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
    }
    fun sameNetwork(previous:String?,current:String?)=previous!=null && previous==current
}
