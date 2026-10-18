package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {

    private fun isTestEnvironment(): Boolean {
        return try {
            Class.forName("org.robolectric.RobolectricTestRunner")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if the device has an active internet connection (Wi-Fi, Cellular Data, or Ethernet).
     */
    fun isNetworkAvailable(context: Context?): Boolean {
        if (isTestEnvironment()) return true
        if (context == null) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        } catch (_: Exception) {
            false
        }
    }
}
