package org.jetbrains.edu.kotlin.todo.storage

import org.jetbrains.edu.kotlin.todo.domain.Todo
import org.jetbrains.edu.kotlin.todo.domain.User
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryStore {

    private val users = ConcurrentHashMap<String, User>()
    private val usersByName = ConcurrentHashMap<String, User>()
    private val todos = ConcurrentHashMap<String, Todo>()

    fun createUser(username: String, passwordHash: String): User {
        if (usersByName.containsKey(username)) throw UsernameTakenException(username)
        val user = User(
            id = "u_${UUID.randomUUID().toString().replace("-", "")}",
            username = username,
            passwordHash = passwordHash,
        )
        users[user.id] = user
        usersByName[username] = user
        return user
    }

    fun findUserByUsername(username: String): User? = usersByName[username]

    fun findUserById(id: String): User? = users[id]

    fun createTodo(
        ownerId: String,
        title: String,
        description: String,
        tags: List<String>,
    ): Todo {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = "t_${UUID.randomUUID().toString().replace("-", "")}",
            ownerId = ownerId,
            title = title,
            description = description,
            tags = tags,
            completed = false,
            createdAt = now,
            updatedAt = now,
        )
        todos[todo.id] = todo
        return todo
    }

    fun findTodoById(id: String): Todo? = todos[id]

    fun listTodosForUser(ownerId: String): List<Todo> =
        todos.values.filter { it.ownerId == ownerId }

    fun updateTodo(
        id: String,
        title: String? = null,
        description: String? = null,
        tags: List<String>? = null,
        completed: Boolean? = null,
    ): Todo? {
        val existing = todos[id] ?: return null
        val updated = existing.copy(
            title = title ?: existing.title,
            description = description ?: existing.description,
            tags = tags ?: existing.tags,
            completed = completed ?: existing.completed,
            updatedAt = System.currentTimeMillis(),
        )
        todos[id] = updated
        return updated
    }

    fun deleteTodo(id: String): Boolean = todos.remove(id) != null
}

class UsernameTakenException(username: String) : RuntimeException("username '$username' already taken")
