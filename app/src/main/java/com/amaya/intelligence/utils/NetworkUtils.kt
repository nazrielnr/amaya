package com.amaya.intelligence.utils

import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Utility object for network-related operations
 */
object NetworkUtils {
    
    /**
     * Get the local IP address of the device on the network.
     * Returns the first non-loopback IPv4 address found.
     * 
     * @return IP address as String (e.g., "192.168.1.100") or "127.0.0.1" if not found
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress
                        // Check if it's IPv4 (no colon in address)
                        if (hostAddress != null && !hostAddress.contains(":")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
