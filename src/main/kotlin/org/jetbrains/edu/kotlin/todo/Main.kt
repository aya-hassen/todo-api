package org.jetbrains.edu.kotlin.todo

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.jetbrains.edu.kotlin.todo.api.registerRoutes
import org.jetbrains.edu.kotlin.todo.auth.TokenAuthService
import org.jetbrains.edu.kotlin.todo.storage.InMemoryStore

fun main() {
    val port = (System.getenv("PORT") ?: System.getProperty("PORT") ?: "8080").toInt()
    val store = InMemoryStore()
    val auth = TokenAuthService(store)

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(DefaultHeaders)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        routing {
            registerRoutes(store, auth)
        }
    }.start(wait = true)
}
