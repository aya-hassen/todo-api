# Lab 3: Todo API Backend (37 points)

## Overview

You are building a small HTTP backend in Kotlin: a multi-user Todo manager with
token-based authentication. The server uses **in-memory storage only** (no DB,
no files), runs in a Docker container, and is deployed to **Render.com's free
tier**. A private grading client probes your live deployment with randomized
inputs and reports per-category results back to GitHub Classroom.

Unlike previous labs, **you don't see the grader's tests**. You see a strict
API specification and a small set of local sanity tests. If your service
matches the spec exactly, you pass.

## Grading

All 37 points are auto-graded. Each category is **all-or-nothing**: every check
in the category must pass to receive the points.

| # | Category    | Points | Summary                                                              |
|---|-------------|-------:|----------------------------------------------------------------------|
| 1 | Health      |      2 | `GET /health` returns 200 `{"status":"ok"}`                          |
| 2 | Auth        |      6 | `/auth/register`, `/auth/login`, bearer-token protection             |
| 3 | Todo CRUD   |     10 | Create, list, get, update, delete todos owned by the caller         |
| 4 | Filtering   |      8 | `completed`, `tag`, `q`, `sort`, `limit`, `offset` query parameters  |
| 5 | Isolation   |      5 | One user cannot read or modify another user's todos                  |
| 6 | Edge cases  |      6 | Validation errors, 404/409 cases, malformed bodies                   |
|   | **Total**   | **37** |                                                                      |

See [docs/GRADING.md](docs/GRADING.md) for the full breakdown and grading flow.

## What you need to do

1. **Read [docs/API.md](docs/API.md)** — this is the contract you must implement.
   Request/response shapes, status codes, and error formats are non-negotiable.
2. **Implement the server** in `src/main/kotlin/...`. A Ktor scaffold is provided
   but you may use Javalin, http4k, or even raw `HttpServer` — the grader only
   cares about HTTP behavior.
3. **Run it locally** with `./gradlew run` and verify against the open tests
   (`./gradlew test`) and curl examples in `docs/API.md`.
4. **Deploy your service.** The default path is Render.com's free tier — see
   [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the step-by-step, and Render's
   own Docker quickstart at <https://render.com/docs/docker> for context on
   how it builds the image.

   If you'd rather host elsewhere — a VPS you control, your own machine
   exposed via a tunnel, Fly.io, Koyeb, AWS, GCP, anything — that's fine. The
   grader is provider-agnostic. The only requirement is that **the URL you
   commit to `grading.yml` is reachable over HTTPS at any time the autograder
   might run**. Cold-start delays of up to 60 s are tolerated; outages are
   not.
5. **Commit your deployed URL** into [`grading.yml`](grading.yml).
6. **Push to `main`** — GitHub Classroom will trigger the grader.

## Quickstart

```bash
# Build and run locally on port 8080
./gradlew run

# In another shell, sanity check
curl -s http://localhost:8080/health
# → {"status":"ok"}

# Run open tests
./gradlew test

# Build the production fat JAR (the same one Docker uses)
./gradlew shadowJar

# Build and run the Docker image exactly as Render will
docker build -t todo-api .
docker run --rm -p 8080:8080 -e PORT=8080 todo-api
```

## Rules

- **In-memory storage only.** Restarting the container wipes everything. The
  grader does not expect persistence; do not add it.
- **The server must bind to `0.0.0.0`** and respect the `PORT` environment
  variable. Render injects `PORT`; binding to `localhost` will fail there
  silently.
- **Do not commit secrets** of any kind into the repo.
- **No persistent disks**, no databases (Postgres/SQLite/etc.), no external APIs.

## Academic integrity

**Cheating or sabotaging the test system will result in 0 points for this lab.**
That includes, non-exhaustively:

- Modifying `.github/workflows/test.yml`, `src/test/`, `build.gradle.kts`,
  `Dockerfile`, `render.yaml`, `settings.gradle.kts`, or the structure of
  `grading.yml`.
- Submitting another student's `deployedUrl`, or sharing your URL with a
  classmate so they can submit it as theirs. The grader keeps an audit log
  of every (repo, URL) pair it has ever seen; collisions are flagged for
  manual review.
- Hardcoding fake responses to pass the open tests while failing the spec.
  Closed tests use randomized inputs — stubs will not survive.
- Probing, reverse-engineering, or exfiltrating the grading service to game
  its checks.

AI tools are permitted, but you must understand every line of code you submit
and be able to explain it on demand. A random subset of students will be
asked to defend their implementation.

## Local tests vs. grader

The tests in `src/test/kotlin/` are **open** and only sample a handful of cases
per category — they exist to help you catch obvious mistakes before pushing.
The real grader (hidden, runs against your Render deployment) uses **randomized
inputs per category**, so hardcoded responses or stubs will fail. Implement the
spec, not the open tests.

## Submission

1. Implement the API.
2. Deploy to Render and verify with `curl https://<your-app>.onrender.com/health`.
3. Edit `grading.yml`:
   ```yaml
   studentId: <your-github-username-or-classroom-id>
   deployedUrl: https://<your-app>.onrender.com
   ```
4. Push to `main`. The autograder will appear under the **Actions** tab within
   a minute or two and run for 60–120 seconds.

## Tips

- Render free tier **sleeps after ~15 minutes of inactivity**. The grader sends
  a warmup request, but a cold start can take 30–60s. Don't panic if the first
  request in a session is slow.
- **Test your error responses** as carefully as your happy paths — half of
  category 6 is making sure `400`/`401`/`404`/`409`/`422` are returned with the
  right body shape.
- If you're stuck on a category, run the open tests with `--info`:
  `./gradlew test --tests "*Auth*" --info`.

Good luck.
