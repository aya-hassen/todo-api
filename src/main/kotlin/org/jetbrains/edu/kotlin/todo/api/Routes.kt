package org.jetbrains.edu.kotlin.todo.api

import io.ktor.server.routing.Route
import org.jetbrains.edu.kotlin.todo.auth.TokenAuthService
import org.jetbrains.edu.kotlin.todo.storage.InMemoryStore

/** Wire up the HTTP routes defined in `docs/API.md`. */
fun Route.registerRoutes(store: InMemoryStore, auth: TokenAuthService) {
    TODO()
}
