# Live Stream Platform

Phase 1 foundation: Maven + Dropwizard + Guice + config (Hibernate/WebSocket deps ready for later phases).

## Prerequisites

1. **JDK 17+** (required by Dropwizard 4)
   - Install: [Adoptium Temurin 17](https://adoptium.net/) or use IntelliJ’s bundled JDK (see below).
2. **IntelliJ IDEA** (Community or Ultimate)
3. **Maven** — bundled with IntelliJ, or install separately.

## Open in IntelliJ (first time)

1. Open **IntelliJ IDEA**.
2. **File → Open…** (not “New Project” — the project already exists).
3. Select this folder: `/Users/macharla.h/projects/live-stream`
4. Click **Open**.
5. When asked **“Trust Project?”** → **Trust Project**.
6. Wait for the bottom-right **Maven import** to finish (indexing + downloading dependencies).

### Set JDK in IntelliJ

1. **File → Project Structure…** (macOS: `⌘ ;`)
2. **Project** → **SDK** → choose **17** (or **21**).
   - If none listed: **Add SDK → Download JDK…** → version **17**, vendor **Eclipse Temurin** → Download.
3. **Project language level** → **17**.
4. Click **OK**.

### Run the server from IntelliJ

1. Open `LiveStreamApplication.java`.
2. Click the green **▶** gutter icon next to `main`, or right-click → **Run 'LiveStreamApplication.main()'**.
3. First run may fail without program arguments. Fix:
   - **Run → Edit Configurations…**
   - Select **LiveStreamApplication**
   - **Program arguments:** `server config/config.yml`
   - **Working directory:** `$PROJECT_DIR$` (project root)
   - **Apply** → **OK** → Run again.

### Verify it works

- App: http://localhost:8080/
- Admin health: http://localhost:8081/healthcheck  
  You should see `app` → healthy.

Stop the server: red **Stop** button in the Run tool window.

## Run from terminal (optional)

```bash
cd /Users/macharla.h/projects/live-stream
mvn clean package
java -jar target/live-stream-1.0-SNAPSHOT.jar server config/config.yml
```

## Project layout (Phase 1)

```
live-stream/
├── pom.xml                          # Maven dependencies
├── config/config.yml                # Ports, DB, FFmpeg paths
└── src/main/java/com/livestream/
    ├── LiveStreamApplication.java   # Main entry (like Spring Boot's @SpringBootApplication)
    ├── LiveStreamConfiguration.java # Maps config.yml → Java fields
    ├── LiveStreamModule.java        # Guice bindings (expanded in Phase 2+)
    └── health/AppHealthCheck.java   # /healthcheck endpoint
```

## Fix: `Could not find or load main class com.livestream.LiveStreamApplication`

IntelliJ has not compiled the project yet (or Maven import failed).

1. Right-click `pom.xml` → **Maven** → **Reload project** (wait for it to finish).
2. **Build** → **Rebuild Project**.
3. **File → Project Structure → Modules** — you should see **live-stream**. If not: **+** → **Import Module** → select `pom.xml`.
4. **Run → Edit Configurations** — **Main class** must be exactly `com.livestream.LiveStreamApplication`, **Module** must be `live-stream` (not "no module").

**Terminal fallback (works after `./mvnw compile`):**

```bash
cd /Users/macharla.h/projects/live-stream
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.0.19/Contents/Home"
./mvnw compile exec:java
```

Then open http://localhost:8081/healthcheck

## Phase 2 — Control Plane (current)

Metadata in PostgreSQL + REST APIs.

### 1. Run the app (no Docker, no PostgreSQL)

Use the **embedded H2 in-memory database** (no Docker, no file locks). Data resets each restart; demo users are re-seeded automatically.

**IntelliJ:** run `LiveStreamApplication` with program arguments:

```text
server config/config-dev.yml
```

(The default run configuration is already set to this.)

**Terminal:**

```bash
./mvnw compile exec:java
```

### Optional: PostgreSQL instead of H2

Only if you have PostgreSQL installed and running on port 5432:

```bash
./scripts/setup-postgres.sh
```

Then run with `server config/config.yml` (run configuration: **LiveStream (PostgreSQL)**).

**If you see "database file locked":** another copy of the app is still running. Click the red **Stop** button in IntelliJ, or in Terminal run `pkill -f LiveStreamApplication`. Then start again.

### 2. Verify startup

### 3. Try the APIs

**List live streams:**

```bash
curl -s http://localhost:8080/streams | jq
```

**Start a stream** (uses seeded broadcaster `id=1`):

```bash
curl -s -X POST http://localhost:8080/streams/start \
  -H 'Content-Type: application/json' \
  -d '{"broadcasterId":1,"title":"My First Live Stream"}' | jq
```

**List again** — should show your stream with `status: LIVE` and a `streamKey`.

### New files (Phase 2)

| Path | Role |
|------|------|
| `model/User.java`, `LiveStream.java` | Hibernate entities |
| `dao/UserDAO.java`, `LiveStreamDAO.java` | Database access |
| `service/StreamService.java` | Start/list stream logic |
| `api/StreamResource.java` | `GET /streams`, `POST /streams/start` |
| `db/migration/V1__schema.sql` | Tables |
| `db/migration/V2__seed_dev_users.sql` | Demo users |

## Phase 3 — Data Plane (HLS + FFmpeg)

When you **start a stream**, the server also starts **FFmpeg**, which writes video chunks to `data/hls/{streamId}/`.

### Prerequisites

Install FFmpeg (macOS):

```bash
which ffmpeg
# If missing: install from https://ffmpeg.org/download.html
```

Set `ffmpegPath` in `config/config-dev.yml` to match `which ffmpeg`.

### Try it

1. Restart the app (`server config/config-dev.yml`).
2. Start a stream:

```bash
curl -s -X POST http://localhost:8080/streams/start \
  -H "Content-Type: application/json" \
  -d '{"broadcasterId":1,"title":"Test Stream"}' | jq
```

Note the new field **`playbackUrl`** (e.g. `/hls/1/index.m3u8`).

3. Wait ~5 seconds for FFmpeg to create segments, then open:

http://localhost:8080/ui/viewer.html

Enter stream id `1` and click **Play**.

4. Stop the stream:

```bash
curl -X POST http://localhost:8080/streams/1/stop
```

**Laptop camera (macOS):** `config-dev.yml` sets `videoInput: camera`. FFmpeg reads your webcam directly.

1. **System Settings → Privacy & Security → Camera** → enable **IntelliJ IDEA**.
2. Same for **Microphone** if you use `cameraDevice: "0:0"`.
3. If the wrong camera is used, list devices:
   ```bash
   ffmpeg -f avfoundation -list_devices true -i ""
   ```
   Then set `cameraDevice` (e.g. `"1:0"`) in `config-dev.yml`.

**Color bars again?** Set `videoInput: test` in `config-dev.yml`.

**bufferStalledError:** the player was too close to “live edge”. The viewer now buffers ~12s behind live and auto-recovers.

## Next: Phase 4

WebSockets for live chat.
