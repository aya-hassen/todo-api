# Deployment to Render.com

The grader runs against your **live, public** deployment, not your local machine.
You must have a working URL committed to `grading.yml` before the autograder can
score you.

Render's free "Web Service" tier is sufficient: 512 MB RAM, sleeps after ~15 min
of inactivity, ~30–60 s cold start. No credit card required.

## Option A — One-click via `render.yaml` (recommended)

This repo ships a `render.yaml` blueprint. Render reads it and configures the
service for you.

1. **Sign up** at https://render.com (use your GitHub account — it makes the
   next step easier).
2. From the dashboard, click **New → Blueprint**.
3. **Connect** your GitHub account if you haven't, and pick your fork of this
   repository.
4. Render parses `render.yaml`, shows you a single service called
   `todo-api-<your-username>`. Click **Apply**.
5. Wait for the first build (3–5 min the first time). When it's green, copy the
   service URL from the top of the dashboard — it looks like
   `https://todo-api-<something>.onrender.com`.
6. Verify:
   ```bash
   curl -i https://todo-api-<something>.onrender.com/health
   # HTTP/2 200
   # {"status":"ok"}
   ```

## Option B — Manual setup

If the Blueprint flow misbehaves:

1. From the dashboard, **New → Web Service** → pick your repo.
2. Settings:
   - **Environment**: Docker
   - **Region**: any (pick closest)
   - **Branch**: `main`
   - **Dockerfile Path**: `./Dockerfile` (the default)
   - **Plan**: **Free**
3. Add an environment variable: `PORT = 8080`. (Render also injects `$PORT`
   automatically at runtime — the Dockerfile uses it.)
4. Click **Create Web Service**. Wait for the build.

## Updating `grading.yml`

Once your service URL is live, edit the repo root file:

```yaml
# grading.yml
studentId: alice-2026          # your GitHub Classroom username / handle
deployedUrl: https://todo-api-alice.onrender.com
```

**No trailing slash.** **No path component** (just the host). The grader appends
paths itself.

Commit and push:

```bash
git add grading.yml
git commit -m "chore: add render deployment URL"
git push
```

## Verifying the autograder picked it up

1. Open the **Actions** tab in your GitHub repo.
2. The latest workflow run should be named `Autograding`.
3. Click into it. The first step (`Read grading.yml`) prints the URL and student
   ID it parsed. If they're wrong, fix `grading.yml` and push again.
4. The subsequent steps each correspond to one category — green check = points
   awarded, red X = 0 for that category.

## Common deployment problems

| Symptom                                              | Cause                                                 | Fix                                                                 |
|------------------------------------------------------|-------------------------------------------------------|---------------------------------------------------------------------|
| `502 Bad Gateway` from Render                        | App didn't bind to `0.0.0.0:$PORT`                    | Use `embeddedServer(Netty, port = port, host = "0.0.0.0") {...}` |
| Build succeeds, `/health` hangs                      | Server didn't start (look at "Logs" tab on Render)   | Read the stack trace, fix it locally first                          |
| First grader request times out, retries pass         | Render cold start exceeded 30 s                       | Normal — the grader retries with backoff. Don't change anything.    |
| `OutOfMemoryError` on startup                        | Default JVM heap too big for 512 MB plan              | Add `-Xmx400m -Xms128m` to the JVM args in `Dockerfile`            |
| Every request returns 401                            | Token storage keyed by request ID, not user          | Re-read `docs/API.md` § Authentication                              |
| Local works, deployed returns wrong status codes     | Different framework defaults                          | Test against the Docker image: `docker build … && docker run …`     |

## Free-tier caveats

- The service **sleeps after ~15 min idle**. Cold starts take 30–60 s. The
  grader sends a warmup ping before testing, so this rarely matters in practice
  — but it does mean a quick browser check from your phone might be slow.
- Render free tier has a **monthly hours limit** (~750 h, enough for one
  always-on service). If you have multiple services on the free plan, they
  share the quota.
- Outbound network egress is fine. No inbound static IP — but the grader
  doesn't need one.

## Alternative hosts (if Render is down or blocked)

The grader doesn't care where you host as long as the URL is HTTPS and publicly
reachable. Equivalent free options:

- **Fly.io** — needs a credit card for verification but no charges on the free
  allowance. Use `fly launch` with the included Dockerfile.
- **Koyeb** — similar to Render, no card required.
- **A VPS you control** — fine, but you're on the hook for TLS (use Caddy or
  Traefik). Make sure HTTPS works; the grader will not accept `http://`.

Whatever you pick, the rules are the same: HTTPS, public, `grading.yml`
contains the host with no trailing slash.
