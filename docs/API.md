# API Specification

This document is the **authoritative contract** for the Todo API. The grader
checks for exact conformance to status codes, header names, JSON field names,
and error shapes. Deviations — even cosmetic ones — will fail tests.

All responses are JSON unless explicitly stated otherwise. The server must send
`Content-Type: application/json; charset=utf-8` on every JSON response.

---

## Conventions

### Identifiers

- **User ID** (`userId`): server-generated, returned by `/auth/register`. Format:
  string, ≤ 64 chars. UUIDs are fine. Stable for the lifetime of the process.
- **Todo ID** (`id`): server-generated, returned by `POST /todos`. Same constraints
  as userId. Globally unique across all users in a single process.
- **Token**: opaque bearer token returned by `/auth/login` or `/auth/register`.
  Treat as a black box — the grader never inspects token contents.

### Authentication

Protected endpoints expect an `Authorization: Bearer <token>` header. Behaviour
when the header is missing or the token is unknown/expired:

```
HTTP/1.1 401 Unauthorized
Content-Type: application/json; charset=utf-8

{"error": "unauthorized", "message": "missing or invalid token"}
```

The `message` text is informational only — the grader checks `error` and the
status code, not the message string.

### Standard error shape

Every non-2xx response has this body:

```json
{"error": "<machine-readable-code>", "message": "<human-readable detail>"}
```

The `error` codes used in this spec are:

| Code             | Status | When                                                       |
|------------------|-------:|------------------------------------------------------------|
| `bad_request`    |    400 | Malformed JSON, wrong Content-Type, missing required body  |
| `unauthorized`   |    401 | Missing or invalid bearer token                            |
| `forbidden`      |    403 | Authenticated but acting on another user's resource        |
| `not_found`      |    404 | Resource (user, todo) does not exist                       |
| `conflict`       |    409 | Username already taken on register                         |
| `unprocessable`  |    422 | Body is valid JSON but fails validation                    |
| `payload_too_large` | 413 | Request body > 64 KB                                       |

The grader checks both the status code **and** the `error` string. Do not
return `400` where the spec says `422`.

### Validation rules (used throughout)

- `username`: 3–32 chars, `[A-Za-z0-9_]+`, case-sensitive.
- `password`: 6–128 chars, any printable ASCII.
- `title`: 1–200 chars, trimmed of leading/trailing whitespace before storing.
- `description`: optional, 0–2000 chars.
- `tags`: list of strings, each 1–32 chars `[a-z0-9-]+`, at most 16 tags per todo.
- Any extra unknown fields in a request body **must be ignored**, not rejected.

---

## Endpoints

### `GET /health`

Liveness check. No auth.

**Response 200:**
```json
{"status": "ok"}
```

No other fields, no other status codes.

---

### `POST /auth/register`

Create a new account. No auth required.

**Request:**
```json
{"username": "alice", "password": "hunter2"}
```

**Response 201:**
```json
{"userId": "u_8f3a...", "username": "alice", "token": "tok_91ce..."}
```

**Errors:**

| Status | `error` code   | Cause                                                  |
|-------:|----------------|--------------------------------------------------------|
|    400 | `bad_request`  | Malformed JSON, missing `username` or `password` field |
|    422 | `unprocessable`| Validation rules above failed                          |
|    409 | `conflict`     | `username` already exists                              |

The returned `token` is immediately usable; the user does not need to also
call `/auth/login`.

---

### `POST /auth/login`

Exchange credentials for a fresh token. No auth required.

**Request:**
```json
{"username": "alice", "password": "hunter2"}
```

**Response 200:**
```json
{"userId": "u_8f3a...", "username": "alice", "token": "tok_2bb7..."}
```

**Errors:**

| Status | `error` code   | Cause                                                  |
|-------:|----------------|--------------------------------------------------------|
|    400 | `bad_request`  | Malformed JSON, missing fields                         |
|    401 | `unauthorized` | Unknown username **or** wrong password                 |

> The same `unauthorized` is returned for both cases on purpose — do not leak
> whether a username exists.

Issuing a new login token does **not** invalidate previous tokens. The grader
may keep using the registration token.

---

### `GET /auth/me`

Returns the authenticated user's identity. Auth required.

**Response 200:**
```json
{"userId": "u_8f3a...", "username": "alice"}
```

**Errors:** `401 unauthorized`.

---

### `POST /todos`

Create a todo owned by the caller. Auth required.

**Request:**
```json
{
  "title": "Buy milk",
  "description": "2% organic",
  "tags": ["shopping", "today"]
}
```

`description` and `tags` are optional; `tags` defaults to `[]`, `description`
to `""`.

**Response 201:**
```json
{
  "id": "t_a1b2...",
  "title": "Buy milk",
  "description": "2% organic",
  "tags": ["shopping", "today"],
  "completed": false,
  "createdAt": "2026-05-14T10:30:00Z",
  "updatedAt": "2026-05-14T10:30:00Z"
}
```

`createdAt` / `updatedAt` are ISO-8601 UTC strings ending in `Z`. They must
equal each other on creation.

**Errors:** `400`, `401`, `422`, `413`.

---

### `GET /todos`

List the caller's todos. Auth required. Returns an array (possibly empty).

**Query parameters** (all optional):

| Param       | Type     | Default | Effect                                                  |
|-------------|----------|---------|---------------------------------------------------------|
| `completed` | `true`/`false` | (any) | Filter by completion status                       |
| `tag`       | string   | —       | Include only todos that have this tag (exact match)     |
| `q`         | string   | —       | Case-insensitive substring match against `title`        |
| `sort`      | `created`/`-created`/`title`/`-title` | `-created` | Sort order (prefix `-` = descending) |
| `limit`     | int 1..100 | 20    | Page size                                               |
| `offset`    | int ≥ 0  | 0       | Pagination offset                                       |

**Response 200:**
```json
[
  {"id": "...", "title": "...", "description": "...", "tags": [...],
   "completed": false, "createdAt": "...", "updatedAt": "..."},
  ...
]
```

**Errors:** `401`. Invalid query values → `422`.

> A user with zero todos receives `200 []`, **not** `404`.

---

### `GET /todos/{id}`

Fetch a single todo. Auth required.

**Response 200:** same object as in `POST /todos`.

**Errors:**

| Status | `error`        | Cause                                              |
|-------:|----------------|----------------------------------------------------|
|    401 | `unauthorized` | No/invalid token                                   |
|    403 | `forbidden`    | Todo exists but belongs to another user            |
|    404 | `not_found`    | No todo with this id exists in the entire system   |

The distinction between `403` and `404` matters: returning `404` for another
user's todo leaks less information, but the spec explicitly requires `403` so
the grader can verify the isolation logic works.

---

### `PATCH /todos/{id}`

Partial update. Auth required. Any subset of the editable fields may be sent.

**Editable fields:** `title`, `description`, `tags`, `completed`.

**Request (example):**
```json
{"completed": true, "tags": ["done"]}
```

**Response 200:** the updated todo (same shape as creation), with `updatedAt`
refreshed to the current time. `createdAt` is unchanged.

**Errors:** `400`, `401`, `403`, `404`, `422`.

> Sending `id`, `createdAt`, or `updatedAt` in the body must be silently ignored
> (the "ignore unknown fields" rule applies to immutable fields too).

---

### `DELETE /todos/{id}`

Delete a todo. Auth required.

**Response 204:** empty body.

**Errors:** `401`, `403`, `404`.

Deleting an already-deleted todo returns `404`, not `204`.

---

## Behaviour examples (curl)

```bash
# Health
curl -i http://localhost:8080/health

# Register and capture the token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"hunter2"}' | jq -r .token)

# Create a todo
curl -s -X POST http://localhost:8080/todos \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Buy milk","tags":["shopping"]}'

# List, filtered
curl -s "http://localhost:8080/todos?tag=shopping&sort=-created&limit=5" \
  -H "Authorization: Bearer $TOKEN"

# Mark complete
curl -s -X PATCH http://localhost:8080/todos/$ID \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"completed":true}'
```

## What the grader does NOT test

- Performance (within reason — each request must complete in < 5s).
- Logging output.
- Concurrency edge cases under high load.
- CORS, HTTPS (Render terminates TLS at the edge).
- Any endpoint not listed in this spec — adding extra endpoints is allowed but
  not graded.
