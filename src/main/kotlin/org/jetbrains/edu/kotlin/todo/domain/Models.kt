package org.jetbrains.edu.kotlin.todo.domain

import kotlinx.serialization.Serializable

/**
 * A user account. Stored only in memory.
 *
 * @property id Opaque server-generated identifier returned to clients.
 * @property username Unique, validated per the API spec.
 * @property passwordHash The hashed password. Do **not** store the plaintext password.
 *   For this lab a salted SHA-256 is sufficient — see [org.jetbrains.edu.kotlin.todo.util.PasswordHasher].
 */
data class User(
    val id: String,
    val username: String,
    val passwordHash: String,
)

/**
 * A todo item. See `docs/API.md` for the wire format.
 *
 * Timestamps are stored as epoch millis and rendered as ISO-8601 UTC strings on the wire.
 */
data class Todo(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

// --- Wire DTOs --------------------------------------------------------------
//
// Keep wire shapes separate from internal domain types so changing one doesn't
// silently break the other. The grader's expected JSON shape is in docs/API.md.

@Serializable
data class RegisterRequest(val username: String? = null, val password: String? = null)

@Serializable
data class LoginRequest(val username: String? = null, val password: String? = null)

@Serializable
data class AuthResponse(val userId: String, val username: String, val token: String)

@Serializable
data class MeResponse(val userId: String, val username: String)

@Serializable
data class CreateTodoRequest(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class UpdateTodoRequest(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val completed: Boolean? = null,
)

@Serializable
data class TodoResponse(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val completed: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ErrorResponse(val error: String, val message: String)
