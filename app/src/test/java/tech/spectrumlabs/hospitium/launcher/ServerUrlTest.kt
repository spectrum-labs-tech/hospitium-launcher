// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.spectrumlabs.hospitium.launcher.domain.ServerUrl

class ServerUrlTest {
    private fun ok(input: String, local: Boolean = false) = ServerUrl.normalize(input, local).getOrThrow()
    private fun refused(input: String, local: Boolean = false) = assertTrue(input, ServerUrl.normalize(input, local).isFailure)

    @Test
    fun `accepts and normalizes https addresses`() {
        assertEquals("https://hospitium.example", ok("hospitium.example"))
        assertEquals("https://hospitium.example", ok("  HTTPS://Hospitium.Example/ "))
        assertEquals("https://hospitium.example:8443", ok("https://hospitium.example:8443"))
    }

    @Test
    fun `refuses plain http, other schemes, paths and credentials`() {
        refused("http://hospitium.example")
        refused("ftp://hospitium.example")
        refused("https://hospitium.example/v1/launcher")
        refused("https://hospitium.example/?x=1")
        refused("https://user:pass@hospitium.example")
        refused("")
        refused("https://")
        refused("not a url at all")
    }

    @Test
    fun `plain http only to a development machine, only when allowed`() {
        refused("http://10.0.2.2:8080")
        assertEquals("http://10.0.2.2:8080", ok("http://10.0.2.2:8080", local = true))
        refused("http://hospitium.example", local = true)
    }
}
