package org.jetbrains.edu.kotlin.todo.auth

import org.jetbrains.edu.kotlin.todo.domain.User
import org.jetbrains.edu.kotlin.todo.storage.InMemoryStore

/** Token-based authentication. Behaviour is specified in `docs/API.md`. */
class TokenAuthService(private val store: InMemoryStore) {

    fun register(username: String, password: String): Pair<User, String> = TODO()

    fun login(username: String, password: String): Pair<User, String>? = TODO()

    fun validate(token: String): User? = TODO()
}
