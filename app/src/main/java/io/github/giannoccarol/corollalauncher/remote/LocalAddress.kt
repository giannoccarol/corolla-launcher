package io.github.giannoccarol.corollalauncher.remote

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * The address the phone can reach the unit at: its IPv4 address on Wi-Fi (as a client of the
 * phone's hotspot or of a home network), or on its own hotspot.
 */
object LocalAddress {

    data class Candidate(val interfaceName: String, val address: String)

    /** The best of [candidates]: Wi-Fi first, then a hotspot, then wired, then the rest. Pure. */
    fun choose(candidates: List<Candidate>): String? = candidates
        .filter { usable(it.address) }
        .minByOrNull { rank(it.interfaceName.lowercase(Locale.ROOT)) }
        ?.address

    private fun rank(name: String): Int = when {
        name.startsWith("wlan") -> 0
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 1
        name.startsWith("eth") -> 2
        else -> 3
    }

    /** A private IPv4 address a phone on the same network could use. */
    fun usable(address: String): Boolean {
        val parts = address.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            else -> false
        }
    }

    /** The device's interfaces, read from the system. */
    fun find(): String? = try {
        val candidates = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                iface.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .mapNotNull { address -> address.hostAddress?.let { Candidate(iface.name, it) } }
            }
        choose(candidates)
    } catch (e: java.net.SocketException) {
        null
    }
}
