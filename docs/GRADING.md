# Grading

Total: **37 points**. All categories are auto-graded, all-or-nothing.

The grader runs against your **live deployed service** (committed in
`grading.yml`). It does not run any of your code locally — only HTTP calls.

## Per-category breakdown

| #   | Category    | Pts | Rough idea of what's tested                                          |
|-----|-------------|----:|----------------------------------------------------------------------|
| 1   | Health      |   2 | `GET /health` returns exactly `{"status":"ok"}` with status 200      |
| 2   | Auth        |   6 | Register + login + bearer-token flow; correct error codes            |
| 3   | Todo CRUD   |  10 | Create/list/get/update/delete; correct shapes and timestamps         |
| 4   | Filtering   |   8 | `completed`/`tag`/`q`/`sort`/`limit`/`offset` work as specified      |
| 5   | Isolation   |   5 | One user cannot read or mutate another user's todos                  |
| 6   | Edge cases  |   6 | Validation, 404/409, malformed JSON, oversized payload               |
|     | **Total**   |  **37** |                                                                  |

Inside a category the grader runs multiple checks; **every check must pass** to
award the category's points. There is no partial credit per check.

## Randomization

Each category seeds its inputs from a per-run random source. Concretely:

- **Auth** uses freshly generated usernames each run, so a hardcoded user list
  won't pass.
- **CRUD** invents random titles, descriptions, and tag sets.
- **Filtering** generates a mixed batch of todos with controlled tag/completed
  distributions, then asserts the returned counts and ordering.
- **Isolation** creates two fresh users per check and asserts each cannot see
  the other's resources.
- **Edge cases** sends random malformed bodies and random invalid query params.

Stubbing won't work. Returning a hardcoded `[{"id":"1",...}]` from `GET /todos`
will pass exactly the open tests in `src/test/` and fail every closed check.

## Grading flow (technical)

1. You push to `main`.
2. GitHub Actions reads `grading.yml`:
   ```yaml
   studentId: alice-2026
   deployedUrl: https://todo-api-alice.onrender.com
   ```
3. Workflow calls `POST https://teacher-grader.example.com/grade` (the URL is
   stored as the `TEACHER_GRADER_URL` workflow variable) with a bearer token
   that the course staff issued to GitHub Classroom:
   ```json
   {
     "studentId": "alice-2026",
     "deployedUrl": "https://todo-api-alice.onrender.com",
     "runId": "12345678",
     "githubRepo": "Development-in-JVM-Languages/jvm-2026-lab-03-alice"
   }
   ```
4. The teacher-side client:
   - sends 1–3 warmup `GET /health` requests (Render free tier cold-start);
   - runs each category sequentially with random fixtures;
   - aggregates results into a JSON report:
     ```json
     {
       "studentId": "alice-2026",
       "total": 31,
       "max": 37,
       "durationMs": 18432,
       "categories": [
         {"name": "health",    "max": 2,  "awarded": 2,  "passed": 1, "failed": 0},
         {"name": "auth",      "max": 6,  "awarded": 6,  "passed": 5, "failed": 0},
         {"name": "crud",      "max": 10, "awarded": 10, "passed": 8, "failed": 0},
         {"name": "filtering", "max": 8,  "awarded": 0,  "passed": 3, "failed": 2},
         {"name": "isolation", "max": 5,  "awarded": 5,  "passed": 4, "failed": 0},
         {"name": "edge",      "max": 6,  "awarded": 6,  "passed": 6, "failed": 0}
       ]
     }
     ```
5. The workflow parses the JSON, then runs one
   `classroom-resources/autograding-command-grader@v1` step per category. The
   step's command is a tiny `jq` check that exits 0 iff that category got full
   points.

The autograder reporter combines the steps and posts the total on the PR/run.

## Why is everything all-or-nothing?

Two reasons:

1. **Pedagogical:** if a category has 10 sub-checks and you implement 9 of them
   plus return wrong status codes for the 10th, you've still missed the point.
   Backend APIs are contracts — they work in full or not at all.
2. **Operational:** GitHub Classroom's autograder doesn't support fractional
   points per test. Splitting categories into 1-point sub-steps would mean 30+
   workflow steps and a much noisier UI.

## Defense

Per course policy, a random subset of students will be asked to defend their
implementation. Suspected plagiarism or irresponsible AI usage also triggers a
mandatory defense.

$$
L = A \times D
$$

- $L$ — final lab grade
- $A$ — autograder grade (0–37)
- $D$ — defense coefficient: 1.0 if not selected / passed defense, 0 if failed

## Academic integrity

- AI tools are permitted, but you must understand and be able to explain every
  line of code you submit.
- **Do not modify** `.github/workflows/test.yml`, `src/test/`, `build.gradle.kts`,
  `Dockerfile`, `render.yaml`, `settings.gradle.kts`, or `grading.yml`'s
  structure. Tampering with the autograder = 0 for the whole lab.
- **Do not share your `deployedUrl` with classmates.** The grader keeps an
  audit log of every (repo, URL) pair it has ever seen. If two students submit
  the *same* `deployedUrl`, both are flagged in the staff's collision report
  for manual investigation. The matching is not visible to students — you
  won't see a warning in your own grading output, but the staff will.
- **Do not exfiltrate the grader.** The teacher-grader URL is in a workflow
  variable; the bearer token is a Classroom secret. Probing the grader from
  your service to capture its requests is detectable and treated as cheating.
- Changing your `deployedUrl` between submissions is fine and expected (you may
  redeploy, switch hosts, etc.) — the grader simply notes the change in its
  staff log.
