package com.edurip.bujhechhi.server

import java.net.Inet4Address
import java.net.NetworkInterface

data class Addr(val ip: String, val iface: String)

object Net {
    /** এই ফোনের স্থানীয় (হটস্পট বা ওয়াইফাই) IPv4 ঠিকানা; হটস্পটেরটা আগে। */
    fun localAddresses(): List<Addr> {
        val out = ArrayList<Addr>()
        try {
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
            for (nif in ifs) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name ?: continue
                if (skip(name)) continue
                for (ia in nif.inetAddresses) {
                    if (ia is Inet4Address && !ia.isLoopbackAddress && ia.isSiteLocalAddress) {
                        val host = ia.hostAddress ?: continue
                        out.add(Addr(host, name))
                    }
                }
            }
        } catch (e: Exception) {
            // ঠিকানা না পেলে খালি তালিকা
        }
        return out.sortedBy { rank(it.iface) }
    }

    private fun skip(n: String): Boolean =
        n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("tun") ||
            n.startsWith("dummy") || n.startsWith("v4-") || n.startsWith("clat") || n.startsWith("p2p")

    private fun rank(n: String): Int = when {
        n.startsWith("ap") || n.startsWith("swlan") || n.contains("softap") -> 0
        n.startsWith("wlan") || n.startsWith("eth") -> 1
        else -> 2
    }
}
