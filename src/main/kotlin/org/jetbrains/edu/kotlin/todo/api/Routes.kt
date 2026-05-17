package org.jetbrains.edu.kotlin.todo.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.edu.kotlin.todo.auth.TokenAuthService
import org.jetbrains.edu.kotlin.todo.domain.AuthResponse
import org.jetbrains.edu.kotlin.todo.domain.ErrorResponse
import org.jetbrains.edu.kotlin.todo.domain.MeResponse
import org.jetbrains.edu.kotlin.todo.domain.Todo
import org.jetbrains.edu.kotlin.todo.domain.TodoResponse
import org.jetbrains.edu.kotlin.todo.domain.User
import org.jetbrains.edu.kotlin.todo.storage.InMemoryStore
import org.jetbrains.edu.kotlin.todo.storage.UsernameTakenException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

private fun epochToIso(millis: Long): String = isoFormatter.format(Instant.ofEpochMilli(millis))

private fun Todo.toResponse() = TodoResponse(
    id = id,
    title = title,
    description = description,
    tags = tags,
    completed = completed,
    createdAt = epochToIso(createdAt),
    updatedAt = epochToIso(updatedAt),
)

private val usernameRegex = Regex("^[A-Za-z0-9_]{3,32}$")
private val tagRegex = Regex("^[a-z0-9-]{1,32}$")

private fun isValidUsername(u: String) = usernameRegex.matches(u)
private fun isValidPassword(p: String) = p.length in 6..128
private fun isValidTitle(t: String) = t.isNotEmpty() && t.length <= 200
private fun isValidDescription(d: String) = d.length <= 2000
private fun isValidTag(t: String) = tagRegex.matches(t)

private suspend fun io.ktor.server.application.ApplicationCall.requireAuth(auth: TokenAuthService): User? {
    val header = request.headers["Authorization"]
    val token = if (header != null && header.startsWith("Bearer ")) header.removePrefix("Bearer ") else null
    val user = token?.let { auth.validate(it) }
    if (user == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "missing or invalid token"))
        return null
    }
    return user
}

private suspend fun io.ktor.server.application.ApplicationCall.receiveJsonOrNull(): JsonObject? {
    val ct = request.contentType().toString()
    if (!ct.contains("application/json")) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Content-Type must be application/json"))
        return null
    }
    val text = receiveText()
    if (text.length > 64 * 1024) {
        respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body exceeds 64 KB"))
        return null
    }
    return try {
        json.parseToJsonElement(text) as? JsonObject
            ?: run {
                respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Expected a JSON object"))
                null
            }
    } catch (e: Exception) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Malformed JSON"))
        null
    }
}

fun Route.registerRoutes(store: InMemoryStore, auth: TokenAuthService) {

    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    post("/auth/register") {
        val body = call.receiveJsonOrNull() ?: return@post
        val username = body["username"]?.jsonPrimitive?.content
        val password = body["password"]?.jsonPrimitive?.content

        if (username == null || password == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "username and password are required"))
            return@post
        }
        if (!isValidUsername(username)) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "username must be 3–32 chars, [A-Za-z0-9_]"))
            return@post
        }
        if (!isValidPassword(password)) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "password must be 6–128 printable ASCII chars"))
            return@post
        }
        try {
            val (user, token) = auth.register(username, password)
            call.respond(HttpStatusCode.Created, AuthResponse(userId = user.id, username = user.username, token = token))
        } catch (e: UsernameTakenException) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("conflict", "username already taken"))
        }
    }

    post("/auth/login") {
        val body = call.receiveJsonOrNull() ?: return@post
        val username = body["username"]?.jsonPrimitive?.content
        val password = body["password"]?.jsonPrimitive?.content

        if (username == null || password == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "username and password are required"))
            return@post
        }
        val result = auth.login(username, password)
        if (result == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "invalid credentials"))
            return@post
        }
        val (user, token) = result
        call.respond(HttpStatusCode.OK, AuthResponse(userId = user.id, username = user.username, token = token))
    }

    get("/auth/me") {
        val user = call.requireAuth(auth) ?: return@get
        call.respond(HttpStatusCode.OK, MeResponse(userId = user.id, username = user.username))
    }

    post("/todos") {
        val user = call.requireAuth(auth) ?: return@post
        val body = call.receiveJsonOrNull() ?: return@post

        val title = body["title"]?.jsonPrimitive?.content
        if (title == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "title is required"))
            return@post
        }
        val trimmedTitle = title.trim()
        if (!isValidTitle(trimmedTitle)) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "title must be 1–200 chars"))
            return@post
        }
        val description = body["description"]?.jsonPrimitive?.content ?: ""
        if (!isValidDescription(description)) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "description must be 0–2000 chars"))
            return@post
        }
        val tags = if (body.containsKey("tags")) {
            try {
                body["tags"]!!.jsonArray.map { it.jsonPrimitive.content }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "tags must be an array of strings"))
                return@post
            }
        } else emptyList()

        if (tags.size > 16) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "at most 16 tags allowed"))
            return@post
        }
        for (tag in tags) {
            if (!isValidTag(tag)) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "tag '$tag' is invalid (1–32 chars, [a-z0-9-])"))
                return@post
            }
        }

        val todo = store.createTodo(
            ownerId = user.id,
            title = trimmedTitle,
            description = description,
            tags = tags,
        )
        call.respond(HttpStatusCode.Created, todo.toResponse())
    }

    get("/todos") {
        val user = call.requireAuth(auth) ?: return@get
        val params = call.request.queryParameters

        val completedFilter: Boolean? = params["completed"]?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> {
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "completed must be true or false"))
                    return@get
                }
            }
        }

        val tagFilter: String? = params["tag"]
        val qFilter: String? = params["q"]

        val sort: String = params["sort"] ?: "-created"
        if (sort !in setOf("created", "-created", "title", "-title")) {
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "sort must be one of: created, -created, title, -title"))
            return@get
        }

        val limitParam = params["limit"]
        val limit: Int = if (limitParam != null) {
            val v = limitParam.toIntOrNull()
            if (v == null || v !in 1..100) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "limit must be 1–100"))
                return@get
            }
            v
        } else 20

        val offsetParam = params["offset"]
        val offset: Int = if (offsetParam != null) {
            val v = offsetParam.toIntOrNull()
            if (v == null || v < 0) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "offset must be an integer >= 0"))
                return@get
            }
            v
        } else 0

        var todos = store.listTodosForUser(user.id)

        if (completedFilter != null) todos = todos.filter { it.completed == completedFilter }
        if (tagFilter != null) todos = todos.filter { it.tags.contains(tagFilter) }
        if (qFilter != null) todos = todos.filter { it.title.contains(qFilter, ignoreCase = true) }

        todos = when (sort) {
            "created"  -> todos.sortedBy { it.createdAt }
            "-created" -> todos.sortedByDescending { it.createdAt }
            "title"    -> todos.sortedBy { it.title.lowercase() }
            "-title"   -> todos.sortedByDescending { it.title.lowercase() }
            else       -> todos
        }

        val page = todos.drop(offset).take(limit)
        call.respond(HttpStatusCode.OK, page.map { it.toResponse() })
    }

    get("/todos/{id}") {
        val user = call.requireAuth(auth) ?: return@get
        val id = call.parameters["id"]!!
        val todo = store.findTodoById(id)
        when {
            todo == null          -> call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "todo not found"))
            todo.ownerId != user.id -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "you do not own this todo"))
            else                  -> call.respond(HttpStatusCode.OK, todo.toResponse())
        }
    }

    patch("/todos/{id}") {
        val user = call.requireAuth(auth) ?: return@patch
        val id = call.parameters["id"]!!
        val body = call.receiveJsonOrNull() ?: return@patch

        val todo = store.findTodoById(id)
        when {
            todo == null -> {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "todo not found"))
                return@patch
            }
            todo.ownerId != user.id -> {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "you do not own this todo"))
                return@patch
            }
        }

        val newTitle: String? = if (body.containsKey("title")) {
            val t = body["title"]?.jsonPrimitive?.content
            if (t == null) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "title cannot be null"))
                return@patch
            }
            val trimmed = t.trim()
            if (!isValidTitle(trimmed)) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "title must be 1–200 chars"))
                return@patch
            }
            trimmed
        } else null

        val newDescription: String? = if (body.containsKey("description")) {
            val d = body["description"]?.jsonPrimitive?.content ?: ""
            if (!isValidDescription(d)) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "description must be 0–2000 chars"))
                return@patch
            }
            d
        } else null

        val newTags: List<String>? = if (body.containsKey("tags")) {
            val t = try {
                body["tags"]!!.jsonArray.map { it.jsonPrimitive.content }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "tags must be an array of strings"))
                return@patch
            }
            if (t.size > 16) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "at most 16 tags"))
                return@patch
            }
            for (tag in t) {
                if (!isValidTag(tag)) {
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "tag '$tag' is invalid"))
                    return@patch
                }
            }
            t
        } else null

        val newCompleted: Boolean? = if (body.containsKey("completed")) {
            try { body["completed"]!!.jsonPrimitive.boolean }
            catch (e: Exception) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("unprocessable", "completed must be a boolean"))
                return@patch
            }
        } else null

        val updated = store.updateTodo(id, newTitle, newDescription, newTags, newCompleted)!!
        call.respond(HttpStatusCode.OK, updated.toResponse())
    }

    delete("/todos/{id}") {
        val user = call.requireAuth(auth) ?: return@delete
        val id = call.parameters["id"]!!
        val todo = store.findTodoById(id)
        when {
            todo == null -> call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "todo not found"))
            todo.ownerId != user.id -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "you do not own this todo"))
            else -> {
                store.deleteTodo(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
