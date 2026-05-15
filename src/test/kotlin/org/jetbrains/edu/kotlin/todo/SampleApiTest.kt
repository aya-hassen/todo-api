package org.jetbrains.edu.kotlin.todo

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * A *sample* of the kind of checks the hidden grader runs. Passing all of these is
 * necessary but not sufficient — the grader randomizes inputs and tests more cases.
 *
 * These tests boot your `main()` on a random free port and hit it over HTTP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleApiTest {

    private lateinit var baseUrl: String
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeAll
    fun startServer() {
        val port = ServerSocket(0).use { it.localPort }
        System.setProperty("PORT", port.toString())   // honored by main() if it reads getProperty
        // The provided Main.kt reads System.getenv; if you change it to also read
        // System.getProperty("PORT") this test will Just Work without spawning a process.
        thread(isDaemon = true) {
            try { main() } catch (_: Throwable) { /* server lifecycle is fine */ }
        }
        baseUrl = "http://localhost:$port"
        // Wait up to 10s for /health to come up
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            try {
                val resp = kotlinx.coroutines.runBlocking { client.get("$baseUrl/health") }
                if (resp.status == HttpStatusCode.OK) return
            } catch (_: Throwable) { /* retrying */ }
            Thread.sleep(200)
        }
        throw IllegalStateException("Server did not start within 10s on $baseUrl")
    }

    @AfterAll
    fun stopClient() { client.close() }

    // ---------------------------------------------------------------- health

    @Test
    fun `health returns 200 with status ok`() = kotlinx.coroutines.runBlocking {
        val resp = client.get("$baseUrl/health")
        assertEquals(HttpStatusCode.OK, resp.status)
        val obj = json.parseToJsonElement(resp.bodyAsText()) as JsonObject
        assertEquals("ok", obj["status"]?.jsonPrimitive?.content)
    }

    // ----------------------------------------------------------------- auth

    @Test
    fun `register then me returns the same user`() = kotlinx.coroutines.runBlocking {
        val username = "u_${System.nanoTime()}".take(20)
        val registerResp = client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"hunter22"}""")
        }
        assertEquals(HttpStatusCode.Created, registerResp.status)
        val registerObj = json.parseToJsonElement(registerResp.bodyAsText()) as JsonObject
        val token = registerObj["token"]?.jsonPrimitive?.content
        assertNotNull(token, "register response must include a token")

        val meResp = client.get("$baseUrl/auth/me") {
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
        }
        assertEquals(HttpStatusCode.OK, meResp.status)
        val meObj = json.parseToJsonElement(meResp.bodyAsText()) as JsonObject
        assertEquals(username, meObj["username"]?.jsonPrimitive?.content)
    }

    @Test
    fun `me without token returns 401`() = kotlinx.coroutines.runBlocking {
        val resp = client.get("$baseUrl/auth/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        val obj = json.parseToJsonElement(resp.bodyAsText()) as JsonObject
        assertEquals("unauthorized", obj["error"]?.jsonPrimitive?.content)
    }

    // ----------------------------------------------------------------- todos

    @Test
    fun `create then list returns the created todo`() = kotlinx.coroutines.runBlocking {
        val token = registerFreshUser()
        val createResp = client.post("$baseUrl/todos") {
            contentType(ContentType.Application.Json)
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
            setBody("""{"title":"Buy milk","tags":["shopping"]}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)

        val listResp = client.get("$baseUrl/todos") {
            headers { append(HttpHeaders.Authorization, "Bearer $token") }
        }
        assertEquals(HttpStatusCode.OK, listResp.status)
        assertTrue(listResp.bodyAsText().contains("Buy milk"))
    }

    // ---------------------------------------------------------------- helper

    private suspend fun registerFreshUser(): String {
        val username = "u_${System.nanoTime()}".take(20)
        val resp = client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"hunter22"}""")
        }
        val obj = json.parseToJsonElement(resp.bodyAsText()) as JsonObject
        return obj["token"]!!.jsonPrimitive.content
    }
}
