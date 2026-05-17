package org.jetbrains.edu.kotlin.todo.auth

import org.jetbrains.edu.kotlin.todo.domain.User
import org.jetbrains.edu.kotlin.todo.storage.InMemoryStore
import org.jetbrains.edu.kotlin.todo.storage.UsernameTakenException
import org.jetbrains.edu.kotlin.todo.util.PasswordHasher
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TokenAuthService(private val store: InMemoryStore) {

    private val tokens = ConcurrentHashMap<String, User>()

    fun register(username: String, password: String): Pair<User, String> {
        val hash = PasswordHasher.hash(password)
        val user = store.createUser(username, hash)
        val token = issueToken(user)
        return Pair(user, token)
    }

    fun login(username: String, password: String): Pair<User, String>? {
        val user = store.findUserByUsername(username) ?: return null
        if (!PasswordHasher.verify(password, user.passwordHash)) return null
        val token = issueToken(user)
        return Pair(user, token)
    }

    fun validate(token: String): User? = tokens[token]

    private fun issueToken(user: User): String {
        val token = "tok_${UUID.randomUUID().toString().replace("-", "")}"
        tokens[token] = user
        return token
    }
}
