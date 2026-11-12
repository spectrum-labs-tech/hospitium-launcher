// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.domain

import java.net.URI
import java.net.URISyntaxException

object ServerUrl {
    private val LOCAL_DEV_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    /**
     * Normalizes an operator-entered control-plane address to `scheme://host[:port]`, or explains
     * why it's refused. HTTPS only; plain HTTP is accepted solely for a development machine
     * ([allowLocalHttp], debug builds), matching the debug network security config. A path, query,
     * fragment or user info is refused rather than silently dropped.
     */
    fun normalize(input: String, allowLocalHttp: Boolean): Result<String> {
        val raw = input.trim().let { if ("://" in it) it else "https://$it" }
        val uri = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            return Result.failure(IllegalArgumentException("That isn't a valid address."))
        }
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) return Result.failure(IllegalArgumentException("Enter a host name, e.g. hospitium.example."))
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.rawPath ?: "").trimEnd('/').isNotEmpty()
        ) {
            return Result.failure(IllegalArgumentException("Enter only the server address, without a path."))
        }
        when (uri.scheme?.lowercase()) {
            "https" -> {}
            "http" -> if (!(allowLocalHttp && host in LOCAL_DEV_HOSTS)) {
                return Result.failure(IllegalArgumentException("The address must start with https://."))
            }
            else -> return Result.failure(IllegalArgumentException("The address must start with https://."))
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return Result.success("${uri.scheme.lowercase()}://$host$port")
    }
}
