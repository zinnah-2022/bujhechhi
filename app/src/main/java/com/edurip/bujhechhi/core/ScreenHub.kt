package com.edurip.bujhechhi.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * স্ক্রিন শেয়ারের সর্বশেষ ছবি (JPEG) ধরে রাখে। শিক্ষার্থীর ব্রাউজার নিজের গতিতে "এর পরের ছবি" চায়
 * (long-poll), তাই ধীর ফোনকে আটকে থাকতে হয় না, শুধু মাঝের ছবি বাদ পড়ে।
 */
class ScreenHub(private val clock: () -> Long = { System.currentTimeMillis() }) {
    class Frame(val seq: Long, val bytes: ByteArray)

    @Volatile private var cur: Frame? = null
    private val seq = AtomicLong()
    private val seen = ConcurrentHashMap<String, Long>()
    val framesPublished = AtomicLong()
    val bytesServed = AtomicLong()

    fun publish(bytes: ByteArray): Long {
        val s = seq.incrementAndGet()
        cur = Frame(s, bytes)
        framesPublished.incrementAndGet()
        return s
    }

    /** [after]-এর চেয়ে নতুন ছবি থাকলে সেটা, নইলে null। */
    fun peek(after: Long): Frame? = cur?.takeIf { it.seq > after }

    /** কে দেখছে তার হিসাব: অনুরোধ আসা মাত্র ডাকুন। */
    fun touch(cid: String) {
        if (cid.length in 6..64) seen[cid] = clock()
    }

    fun served(cid: String, n: Int) {
        touch(cid)
        bytesServed.addAndGet(n.toLong())
    }

    /** গত [windowMs]-এ যত ডিভাইস ছবি চেয়েছে। */
    fun viewers(windowMs: Long = 12_000): Int {
        val now = clock()
        seen.entries.removeIf { now - it.value > 120_000 }
        return seen.values.count { now - it <= windowMs }
    }

    fun reset() {
        cur = null
        seen.clear()
    }
}
