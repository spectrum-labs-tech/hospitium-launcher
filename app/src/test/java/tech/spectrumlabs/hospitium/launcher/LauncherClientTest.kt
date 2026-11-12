// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tech.spectrumlabs.hospitium.launcher.net.ApiException
import tech.spectrumlabs.hospitium.launcher.net.LauncherClient
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/** Against a real loopback HTTP server (the JDK's), so the wire behavior is what's tested. */
class LauncherClientTest {
    private data class Seen(val method: String, val path: String, val auth: String?, val userAgent: String?)

    private lateinit var server: HttpServer
    private val seen = ConcurrentLinkedQueue<Seen>()
    private var respond: (HttpExchange) -> Pair<Int, String> = { 404 to "" }
    private val client get() = LauncherClient("http://127.0.0.1:${server.address.port}/", "test-agent/1", timeoutMillis = 5_000)

    @Before
    fun up() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seen += Seen(ex.requestMethod, ex.requestURI.rawPath, ex.requestHeaders.getFirst("Authorization"), ex.requestHeaders.getFirst("User-Agent"))
            val (status, body) = respond(ex)
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
    }

    @After
    fun down() = server.stop(0)

    @Test
    fun `pairing start is an unauthenticated POST`() = runBlocking {
        respond = { 201 to """{"code":"ABC234","poll_token":"hsp_x","expires_at":"2026-11-12T15:15:00Z"}""" }
        val r = client.startPairing()
        assertEquals("ABC234", r.code)
        assertEquals(Seen("POST", "/v1/launcher/pairings", null, "test-agent/1"), seen.single())
    }

    @Test
    fun `pairing status sends the poll token as a bearer`() = runBlocking {
        respond = { 200 to """{"status":"claimed","device_token":"hsd_y","device_id":7}""" }
        val r = client.pairingStatus("ABC234", "hsp_x")
        assertEquals("hsd_y", r.deviceToken)
        assertEquals(Seen("GET", "/v1/launcher/pairings/ABC234", "Bearer hsp_x", "test-agent/1"), seen.single())
    }

    @Test
    fun `a code can't escape its path segment`() = runBlocking {
        respond = { 200 to """{"status":"pending"}""" }
        client.pairingStatus("../config", "hsp_x")
        assertEquals("/v1/launcher/pairings/..%2Fconfig", seen.single().path)
    }

    @Test
    fun `config sends the device token`() = runBlocking {
        respond = { 200 to GO_CONFIG_JSON }
        val c = client.config("hsd_y")
        assertEquals("Example Lodge", c.property.name)
        assertEquals("Bearer hsd_y", seen.single().auth)
    }

    @Test
    fun `status codes map to typed failures`() = runBlocking {
        suspend fun failure(status: Int, body: String = """{"error":"x"}"""): ApiException {
            respond = { status to body }
            try {
                client.config("hsd_y")
            } catch (e: ApiException) {
                return e
            }
            fail("expected a failure for $status")
            error("unreachable")
        }
        assertTrue(failure(401) is ApiException.Unauthorized)
        assertEquals(429, (failure(429) as ApiException.Http).status)
        assertEquals(503, (failure(503) as ApiException.Http).status)
        assertTrue(failure(200, "not json") is ApiException.Malformed)
        assertTrue(failure(200, """{"property":{"name":"P"},"server_time":"yesterday"}""") is ApiException.Malformed)
    }

    @Test
    fun `redirects are not followed, so the token can't be forwarded`() = runBlocking {
        respond = { ex -> ex.responseHeaders.add("Location", "http://127.0.0.1:1/steal"); 302 to "" }
        try {
            client.config("hsd_y")
            fail("expected a failure")
        } catch (e: ApiException.Http) {
            assertEquals(302, e.status)
        }
        assertEquals(1, seen.size)
    }

    @Test
    fun `an unreachable server is a network failure that names no token`() = runBlocking {
        server.stop(0)
        try {
            client.config("hsd_secret")
            fail("expected a failure")
        } catch (e: ApiException.Network) {
            assertTrue("hsd_secret" !in e.message.orEmpty())
        }
        assertNull(seen.peek())
    }
}
