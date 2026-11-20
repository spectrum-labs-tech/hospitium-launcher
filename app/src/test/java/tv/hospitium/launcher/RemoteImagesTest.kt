// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.hospitium.launcher.net.RemoteImages
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Hotlinked backgrounds load from a third-party CDN, so the request must carry nothing of the
 * launcher's: no device token, no cookies. A loopback server stands in for the CDN (the real host
 * check is Backgrounds.hotlinkAllowed, tested in BackgroundsTest).
 */
class RemoteImagesTest {
    private lateinit var server: HttpServer
    private val headers = ConcurrentLinkedQueue<Map<String, List<String>>>()
    private var status = 200
    private var body = "jpeg bytes".toByteArray()
    private var location: String? = null
    private val url get() = "http://127.0.0.1:${server.address.port}/photo-1?ixid=x"

    @Before
    fun up() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            headers += ex.requestHeaders.mapKeys { it.key.lowercase() }
            location?.let { ex.responseHeaders.add("Location", it) }
            ex.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) ex.responseBody.use { it.write(body) }
            ex.close()
        }
        server.start()
    }

    @After
    fun down() = server.stop(0)

    @Test
    fun `fetches the image with no token and no cookies`() = runBlocking {
        val bytes = RemoteImages("test-agent/1", allowed = { true }).fetch(url)
        assertArrayEquals(body, bytes)
        val h = headers.single()
        assertNull("no Authorization header may reach the CDN", h["authorization"])
        assertNull("no cookies may reach the CDN", h["cookie"])
        assertEquals(listOf("test-agent/1"), h["user-agent"])
    }

    @Test
    fun `a refused URL makes no request at all`() = runBlocking {
        assertNull(RemoteImages("a", allowed = { false }).fetch(url))
        assertTrue(headers.isEmpty())
    }

    @Test
    fun `redirects are not followed`() = runBlocking {
        status = 302
        body = ByteArray(0)
        location = "http://127.0.0.1:${server.address.port}/elsewhere"
        assertNull(RemoteImages("a", allowed = { true }).fetch(url))
        assertEquals("only the original request was made", 1, headers.size)
    }

    @Test
    fun `errors and oversized images give nothing`() = runBlocking {
        status = 404
        assertNull(RemoteImages("a", allowed = { true }).fetch(url))
        status = 200
        body = ByteArray(2048) { 1 }
        assertNull(RemoteImages("a", maxBytes = 1024, allowed = { true }).fetch(url))
        assertArrayEquals(body, RemoteImages("a", maxBytes = 4096, allowed = { true }).fetch(url))
    }
}
