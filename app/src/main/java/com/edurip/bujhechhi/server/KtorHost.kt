package com.edurip.bujhechhi.server

import android.content.Context
import com.edurip.bujhechhi.core.ClassCore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * শিক্ষকের ফোনে চলা ছোট ওয়েব সার্ভার: শিক্ষার্থীর পেজ (assets/student) পরিবেশন করে
 * এবং /ws-এ WebSocket নেয়। সব যুক্তি [ClassCore]-এ; এখানে শুধু যোগাযোগ।
 */
class KtorHost(private val context: Context, private val core: ClassCore) : ClassCore.Transport {

    private val conns = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val assetCache = ConcurrentHashMap<String, ByteArray>()
    private val seq = AtomicLong()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stopFn: (() -> Unit)? = null

    @Volatile
    var port: Int = 0
        private set

    /** ৮০৮০ থেকে শুরু করে খালি পোর্ট খুঁজে সার্ভার চালু করে এবং পোর্ট ফেরত দেয়। */
    fun start(): Int {
        stop()
        var last: Exception? = null
        for (p in 8080..8090) {
            try {
                val srv = embeddedServer(CIO, port = p, host = "0.0.0.0") { module() }
                srv.start(wait = false)
                if (!waitListening(p)) {
                    try {
                        srv.stop(100, 300)
                    } catch (e: Exception) {
                        // উপেক্ষা
                    }
                    continue
                }
                stopFn = {
                    try {
                        srv.stop(300, 1000)
                    } catch (e: Exception) {
                        // উপেক্ষা
                    }
                }
                port = p
                return p
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("খালি পোর্ট পাওয়া যায়নি")
    }

    fun stop() {
        stopFn?.invoke()
        stopFn = null
        conns.clear()
    }

    private fun waitListening(p: Int): Boolean {
        repeat(20) {
            try {
                Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", p), 150) }
                return true
            } catch (e: Exception) {
                Thread.sleep(100)
            }
        }
        return false
    }

    // ------------------------------------------------------------ Transport

    override fun send(connId: String, text: String) {
        conns[connId]?.outgoing?.trySend(Frame.Text(text))
    }

    override fun close(connId: String) {
        val s = conns.remove(connId) ?: return
        scope.launch {
            try {
                s.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
            } catch (e: Exception) {
                // উপেক্ষা
            }
        }
    }

    // ------------------------------------------------------------ রাউটিং

    private fun Application.module() {
        install(WebSockets) {
            pingPeriodMillis = 15_000
            timeoutMillis = 30_000
            maxFrameSize = 64L * 1024
            masking = false
        }
        routing {
            get("/") { call.respondAsset("student/index.html", ContentType.Text.Html, "no-store") }
            get("/f/{name}") {
                val name = call.parameters["name"].orEmpty()
                if (Regex("[a-z0-9\\-]+\\.woff2").matches(name)) {
                    call.respondAsset("student/f/$name", ContentType("font", "woff2"), "public, max-age=86400")
                } else {
                    call.respondText("not found", status = HttpStatusCode.NotFound)
                }
            }
            get("/health") { call.respondText("ok") }
            // শিক্ষকের শেয়ার করা প্রশ্নের ছবি। কোড মিললে, শেয়ার দেখানো অবস্থায় থাকলে তবেই দেয়।
            get("/img/{id}/{page}") {
                val id = call.parameters["id"]?.toLongOrNull()
                val page = call.parameters["page"]?.toIntOrNull()
                val c = call.request.queryParameters["c"].orEmpty()
                val dl = call.request.queryParameters["dl"] == "1"
                if (id == null || page == null || !core.shareAllowed(id, page, c, dl)) {
                    call.respondText("not found", status = HttpStatusCode.NotFound)
                    return@get
                }
                val f = java.io.File(context.filesDir, "share/$id/$page.jpg")
                if (!f.isFile) {
                    call.respondText("not found", status = HttpStatusCode.NotFound)
                    return@get
                }
                call.response.header(HttpHeaders.CacheControl, "private, max-age=3600")
                if (dl) call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"question-${page + 1}.jpg\"")
                call.respondBytes(f.readBytes(), ContentType.Image.JPEG)
            }
            webSocket("/ws") {
                val id = "c" + seq.incrementAndGet()
                conns[id] = this
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            if (text.length <= 8192) core.onMessage(id, text)
                        }
                    }
                } catch (e: Exception) {
                    // সংযোগ ভেঙে গেলে এখানে আসে
                } finally {
                    conns.remove(id)
                    core.onClose(id)
                }
            }
        }
    }

    private fun loadAsset(path: String): ByteArray? =
        assetCache[path] ?: try {
            context.assets.open(path).use { it.readBytes() }.also { assetCache[path] = it }
        } catch (e: Exception) {
            null
        }

    private suspend fun ApplicationCall.respondAsset(path: String, type: ContentType, cache: String) {
        val bytes = loadAsset(path)
        if (bytes == null) {
            respondText("not found", status = HttpStatusCode.NotFound)
        } else {
            response.header(HttpHeaders.CacheControl, cache)
            respondBytes(bytes, type)
        }
    }
}
