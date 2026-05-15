package org.jetbrains.edu.kotlin.todo.storage

import org.jetbrains.edu.kotlin.todo.domain.Todo
import org.jetbrains.edu.kotlin.todo.domain.User

/**
 * In-memory store for users and todos.
 *
 * No databases, no files — restarting the process must wipe everything.
 * The contract these methods back is in `docs/API.md`.
 */
class InMemoryStore {

    fun createUser(username: String, passwordHash: String): User = TODO()

    fun findUserByUsername(username: String): User? = TODO()

    fun findUserById(id: String): User? = TODO()

    fun createTodo(
        ownerId: String,
        title: String,
        description: String,
        tags: List<String>,
    ): Todo = TODO()

    fun findTodoById(id: String): Todo? = TODO()

    fun listTodosForUser(ownerId: String): List<Todo> = TODO()

    fun updateTodo(
        id: String,
        title: String? = null,
        description: String? = null,
        tags: List<String>? = null,
        completed: Boolean? = null,
    ): Todo? = TODO()

    fun deleteTodo(id: String): Boolean = TODO()
}

class UsernameTakenException(username: String) : RuntimeException("username '$username' already taken")
