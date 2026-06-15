# HiveMQ Platform Demo

A single-binary CLI that demonstrates two HiveMQ Cloud products end-to-end. It logs the user in
via Auth0, provisions the cloud resources both products need, spins up the matching Docker
containers locally, and publishes synthetic sensor data that drives a marketplace AI agent
template — all from one `curl | bash` away.

> **Status:** working demo wired against **staging**. It is also deliberately built as the
> foundation for HiveMQ's next full cloud-management CLI, so several choices (RxJava, Dagger,
> native image, config layering) are "bigger than the demo needs" on purpose. See
> [Design decisions](#design-decisions) and [Pre-production TODOs](#pre-production-todos).

---

## Table of contents

- [What it does](#what-it-does)
- [Install](#install)
- [End-to-end flow](#end-to-end-flow)
- [Execution sequence](#execution-sequence)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Runtime architecture (DI scopes)](#runtime-architecture-di-scopes)
- [Subsystem walkthroughs](#subsystem-walkthroughs)
  - [1. Auth0 login (loopback + PKCE)](#1-auth0-login-loopback--pkce)
  - [2. JWT claims](#2-jwt-claims)
  - [3. Resource provisioning](#3-resource-provisioning)
  - [4. Containers](#4-containers)
  - [5. Mock sensor data](#5-mock-sensor-data)
- [Configuration](#configuration)
- [Build & run](#build--run)
- [Native image](#native-image)
- [Design decisions](#design-decisions)
- [Conventions](#conventions)
- [Pre-production TODOs](#pre-production-todos)

---

## What it does

The demo showcases the **Pulse** and **AgentX (Agentic Orchestrator)** products. In one run it:

1. **Authenticates** the user against Auth0 (Authorization Code + PKCE via a loopback server).
2. **Provisions** the cloud-side resources for both products in parallel and **mints two tokens**:
   - a **Pulse activation token** (for the broker), and
   - an **AgentX enrollment / registration token** (for the orchestrator).
3. **Runs two local containers** wired with those tokens:
   - a **HiveMQ broker** (custom image built at runtime, Pulse enabled), and
   - the **Agentic Orchestrator** (`ghcr.io/hivemq/hivemq-agentic-orch-docker`), which deploys an
     agent from the **Demo Sensor Evaluation** marketplace template.
4. **Publishes synthetic sensor data** to the broker so the template's anomaly-detection rule
   fires (it emails an alert when a reading drifts >20 % from its rolling mean).

Everything is tied to the JVM process: on `Ctrl+C` the containers are torn down and the binary
exits. The only thing written to disk at runtime is the broker's small build context in a temp dir.

---

## Install

One line takes a customer from zero to the running demo: it detects their OS/arch, downloads the
matching prebuilt native binary from the latest GitHub Release, verifies its sha256, and launches
straight into the Auth0 sign-in. Release artifacts (binaries, `.sha256` checksums, and both
installer scripts) are published automatically by [`.github/workflows/release.yml`](.github/workflows/release.yml).

**Public repo (the target state) — `install.sh`, no auth:**

```bash
curl -fsSL https://raw.githubusercontent.com/hivemq/hivemq-platform-demo/main/install.sh | bash
```

**Private repo (internal testing, for now) — `internal_install.sh` via the GitHub CLI.** No token to
embed: `gh` carries the auth. Install it from <https://cli.github.com> and run `gh auth login` once
(with read access to the repo). The bootstrap one-liner pulls the internal script from the private
repo with `gh`, and the script then uses `gh release download` for the binary:

```bash
gh api repos/hivemq/hivemq-platform-demo/contents/internal_install.sh \
  -H "Accept: application/vnd.github.raw" | bash
```

`internal_install.sh` exists **only while the repo is private**; when it goes public it's deleted and
`install.sh` becomes the single entry point.

Overrides (env, both scripts): `DEMO_VERSION=vX.Y.Z` pins a release (default: latest), `DEMO_REPO`
targets a fork. Built targets today: **`demo-linux-amd64`** and **`demo-darwin-arm64`**. A running
**Docker** daemon is required, and login opens a browser (the auth flow is browser+loopback, not stdin).

---

## End-to-end flow

The whole pipeline lives in [`Main.java`](src/main/java/com/hivemq/platform/demo/Main.java) as one
RxJava chain:

```
containersRunner.ensureDockerAvailable()              // preflight: Docker daemon reachable? fail fast
  .andThen(loopbackServer.obtainToken())              // Auth0 login -> Oauth2TokenDto
  .flatMap(token -> sessionFactory.create(token)      // open a session subcomponent
                      .resourceProvisioner().provision())   // -> ProvisionResult(pulseToken, registrationToken)
  .flatMapCompletable(result ->
      containersRunner.run(brokerEnv, orchestratorEnv) // build/pull, start, health-gate both containers
        .andThen(mockDataPublisher.publish()))         // connect + publish forever
  .blockingAwait();                                     // main thread blocks until Ctrl+C
```

A JVM shutdown hook (`demo-shutdown`) calls `containersRunner.teardown()` to force-remove both
containers on exit.

---

## Execution sequence

A step-by-step trace of one run, from launch to teardown. **Legend:** `→` = sequential (waits for
the previous), `( a … & b … )` = **parallel** (run concurrently, join before the next step). Unless
noted, async work runs on the virtual-thread `ioScheduler`. Top-level steps are sequential: each
begins only after the previous fully completes.

```
1.  Bootstrap
    1.1 build the Dagger ApplicationComponent
    1.2 register JVM shutdown hook "demo-shutdown" (calls containersRunner.teardown() on exit)
    1.3 preflight — containersRunner.ensureDockerAvailable(): ping the Docker daemon and fail fast
        with a clear message if it's unreachable, before the user is asked to sign in   [ioScheduler]

2.  Auth — loopbackServer.obtainToken()              [ioScheduler, 1-minute timeout]
    2.1 open a loopback ServerSocket on localhost:8585        (Single.using → closed at the end)
    2.2 generate PKCE verifier/challenge + state, open the browser to Auth0 /authorize
    2.3 accept() the single callback request; validate state + code
    2.4 auth0Client.exchangeCode(code, verifier)
        POST https://<auth0.domain>/oauth/token
             (grant_type=authorization_code, client_id, code, code_verifier, redirect_uri)
        → Oauth2TokenDto (access_token, refresh_token, …)

3.  Open session — sessionFactory.create(token)
    3.1 decode the access-token claims → JwtClaimsDto (orgId, email, pulseBaseUrl, agentxBaseUrl)
    3.2 build the authenticated Pulse/AgentX Retrofit clients (per-product base URLs from the JWT)
        (every API call adds "Authorization: Bearer <accessToken>"; a 401 triggers TokenAuthenticator
         → POST /oauth/token grant_type=refresh_token, then retries the original call)

4.  Provision — ResourceProvisioner#provision = zip(PULSE, AGENTX)       [both arms PARALLEL]
    Each "ensure" is find-or-create (list → first if present, else create); see the linked
    methods for the exact endpoints, bodies, and extracted fields.
    (
      A: Pulse arm
         A1 ResourceProvisioner#ensureProject        → project "demo"
         A2 ResourceProvisioner#ensureAgent          → agent "demo"
         A3 ResourceProvisioner#mintPulseToken       → pulseToken
      &
      B: AgentX arm
         B1 ResourceProvisioner#ensureNetwork        → network "demo"
         B2 ResourceProvisioner#ensureOrchestrator   → orchestrator "demo"
         B3 ResourceProvisioner#mintEnrollmentToken  → registrationToken
         B4 ResourceProvisioner#ensureOrchestratorAgent
            (template …-102 v1.0.4; agent env ALERT_RECIPIENT=<email>, FACTORY_BROKER_URL)
    )
    → ProvisionResult(pulseToken, registrationToken)

5.  Containers — containersRunner.run(brokerEnv={PULSE_TOKEN},
                                      orchestratorEnv={HIVEMQ_AGENTIC_REGISTRATION_TOKEN})
    5.1 Phase 1                                                            [PARALLEL]
        ( a: ensure Docker network "hivemq"
        & b: build broker image (from docker/broker, extends hivemq/hivemq4)
        & c: pull orchestrator image )
    5.2 Phase 2                                                            [PARALLEL]
        ( a: recreate broker        → wait until broker healthy
        & b: recreate orchestrator  → wait until orchestrator healthy )
        recreate = force-remove-by-name → create + start
        (orchestrator container env also carries CONTROL_PLANE_URL + AGENT_BUS_BROKER_URL)

6.  Publish — mockDataPublisher.publish()            [ioScheduler, runs until disposed]
    6.1 connect the blocking MQTT client to localhost:1883   (Completable.using → disconnect on dispose)
    6.2 every 3 s, publish 3 readings to factory/sensor/{temperature,pressure,vibration} (QoS 0)
        (Gaussian baseline; anomalies armed after cycle 60 at a 5% rate)

7.  Wait — blockingAwait() parks the main thread (step 6 never completes on its own)

8.  Terminate — Ctrl+C / terminal close
    8.1 JVM runs the "demo-shutdown" hook → containersRunner.teardown()   [PARALLEL]
        ( a: force-remove broker & b: force-remove orchestrator )
    8.2 process exits (MQTT disconnected via the using() disposer; temp build dir already cleaned)
```

> To change ordering, point at the step number: e.g. "make 5.2 sequential (broker before
> orchestrator)" or "move B4 out of the AgentX arm". Steps 4, 5.1, 5.2, and 8.1 are the parallel
> joins; everything else is strict sequence.

**Classes referenced above** (the `Class#method` names above live here — click through for the
exact code):
[`Main`](src/main/java/com/hivemq/platform/demo/Main.java) ·
[`LoopbackServer`](src/main/java/com/hivemq/platform/demo/oauth2/LoopbackServer.java) ·
[`Auth0Client`](src/main/java/com/hivemq/platform/demo/oauth2/Auth0Client.java) ·
[`ResourceProvisioner`](src/main/java/com/hivemq/platform/demo/provision/ResourceProvisioner.java) ·
[`ContainersRunner`](src/main/java/com/hivemq/platform/demo/containers/ContainersRunner.java) ·
[`DockerManager`](src/main/java/com/hivemq/platform/demo/containers/DockerManager.java) ·
[`MockDataPublisher`](src/main/java/com/hivemq/platform/demo/mqtt/MockDataPublisher.java)

The Retrofit endpoints/bodies the provisioning methods call are defined in
[`PulseApi`](src/main/java/com/hivemq/platform/demo/domain/network/PulseApi.java) and
[`AgentxApi`](src/main/java/com/hivemq/platform/demo/domain/network/AgentxApi.java).

---

## Tech stack

Java **25** (compact source / instance-main style, records, `var`, unnamed `_`, `SequencedCollection`),
built with **Gradle 9.3** (version catalog in [`catalog.toml`](catalog.toml)) and compiled to a
**GraalVM CE 25** native image.

| Concern | Library | Version | Why                                                                                                                       |
|---|---|---|---------------------------------------------------------------------------------------------------------------------------|
| DI | Dagger | 2.59.2 | Compile-time DI — zero reflection, native-image friendly                                                                  |
| Reactive | RxJava | 3.1.12 | Parallel provisioning (`zip`, `mergeArray` ... ), timeouts, the publish stream; foundation for the full CLI |
| HTTP | Retrofit + OkHttp | 3.0.0 / 5.4.0 | Typed REST clients for Pulse/AgentX; OkHttp interceptors for auth + logging                                               |
| JSON / YAML | Jackson | 2.22.0 | SNAKE_CASE main mapper; YAML reader for bundled config                                                                    |
| Docker | docker-java | 3.7.1 | Build/pull/run containers (httpclient5 transport)                                                                         |
| MQTT | hivemq-mqtt-client | 1.3.15 | Publish sensor data (**blocking** client wrapped in RxJava3 — see decisions)                                              |
| Logging | SLF4J + slf4j-simple | 2.0.18 | `@Slf4j` everywhere                                                                                                       |
| Boilerplate | Lombok | 1.18.46 | `@RequiredArgsConstructor`, `@Slf4j`                                                                                      |
| Format | Spotless + palantir-java-format | 8.6.0 | 4-space, keeps fluent/lambda chains compact                                                                               |
| Versioning | axion-release | 1.21.2 | Git-tag-driven version                                                                                                    |
| Native | graalvm buildtools | 1.1.2 | `nativeCompile`; `metadataRepository` + hand-maintained metadata                                                          |

---

## Project structure

```
src/main/java/com/hivemq/platform/demo/
├── Main.java                  # the RxJava pipeline + shutdown hook
├── config/
│   ├── Configuration.java     # record: Auth0 + Fallback (deserialized from application.yaml)
│   └── Loader.java            # reads /application.yaml via the YAML ObjectMapper
├── constants/Constants.java   # ALL tunables: Api, Jwt, Loopback, Containers, Mqtt, Provisioning
├── oauth2/
│   ├── LoopbackServer.java    # Single.using(ServerSocket) loopback; builds the authorize URL
│   ├── Auth0Client.java       # blocking token exchange / refresh (raw OkHttp)
│   └── SessionManager.java    # session-scoped mutable token holder; refreshes via Auth0Client
├── okhttp/
│   ├── AuthorizationInterceptor.java  # adds "Authorization: Bearer <accessToken>"
│   ├── TokenAuthenticator.java        # refreshes the token on a 401 and retries
│   └── LoggingInterceptor.java        # concise request/response logging
├── domain/
│   ├── network/{PulseApi,AgentxApi}.java   # Retrofit interfaces (paths include /api/v1)
│   └── dto/*.java                          # request/response records (+ JwtClaimsDto, SensorReadingDto)
├── provision/
│   ├── ResourceProvisioner.java   # the find-or-create chains for both products
│   └── ProvisionResult.java       # (pulseToken, registrationToken)
├── containers/
│   ├── ContainersRunner.java  # high-level orchestration (network -> images -> start -> health)
│   ├── DockerManager.java     # low-level docker-java ops wrapped in RxJava
│   └── {ContainerSpec,PortBinding,Mount}.java  # declarative container model
├── mqtt/
│   ├── MockDataPublisher.java # the sensor publish stream
│   └── SensorProfile.java     # the 3 tuned sensor profiles
├── di/
│   ├── component/{ApplicationComponent,SessionComponent}.java
│   ├── module/{Configuration,Jackson,Network,Rx,Concurrency,Docker,Mqtt,SessionNetwork}Module.java
│   ├── scope/{ApplicationScope,SessionScope}.java
│   └── qualifier/{Pulse,Agentx,Authenticated,Yaml,Toml}.java
└── utils/{Jwt,Pkce,Url,Web,Os}Utils.java

src/main/resources/
├── application.yaml           # auth0 + fallback config (staging values)
├── docker/broker/{Dockerfile,pulse.xml}   # broker build context (extends hivemq/hivemq4)
└── META-INF/native-image/...  # hand-maintained reachability metadata (see Native image)

run.sh        # local: clean + nativeCompile + run (exec)
install.sh    # remote: curl|bash skeleton — download prebuilt binary + run
```

---

## Runtime architecture (DI scopes)

Two Dagger scopes mirror the app lifecycle:

- **`ApplicationComponent`** (`@ApplicationScope`) — everything available before login: config,
  ObjectMappers, the base OkHttp/Retrofit, the RxJava `Scheduler`, the Docker client, the MQTT
  client, `LoopbackServer`, `ContainersRunner`, `MockDataPublisher`.
- **`SessionComponent`** (`@SessionScope`, a `@Subcomponent`) — created **only after a token is
  obtained**, via `sessionFactory().create(token)`. The token is `@BindsInstance`-bound, and from
  it everything token-derived is built: the decoded `JwtClaimsDto`, the per-product base URLs, the
  authenticated OkHttp client, the `PulseApi`/`AgentxApi`, and the `ResourceProvisioner`.

Why a subcomponent: the Pulse/AgentX base URLs are **inside the JWT** (`orgs[0].pulse.serverUrl`
etc.), so the typed API clients literally cannot be constructed until login has happened.

**Qualifiers** disambiguate the per-product beans: `@Pulse` / `@Agentx` (base URL + Retrofit + API),
`@Authenticated` (the OkHttp client carrying the auth interceptor + 401 authenticator), `@Yaml` /
`@Toml` (Jackson mappers).

**Concurrency:** `ConcurrencyModule` provides a virtual-thread executor (named `io-N` so logs aren't
empty `[]`), wrapped by `RxModule` into a `Scheduler` (`Schedulers.from`). Blocking I/O runs on
virtual threads, so blocking is cheap.

---

## Subsystem walkthroughs

### 1. Auth0 login (loopback + PKCE)

[`LoopbackServer.obtainToken()`](src/main/java/com/hivemq/platform/demo/oauth2/LoopbackServer.java)
uses `Single.using(ServerSocket, …, close)` to manage a one-shot loopback HTTP server:

1. Generate PKCE `verifier`/`challenge` (S256) and a random `state`.
2. Open the system browser (`OsUtils.openUrl`, falls back to printing the URL) to the Auth0
   `/authorize` URL.
3. `accept()` the single callback request, validate `state` + `code`, return a simple HTML page.
4. Exchange the code for tokens via `Auth0Client.exchangeCode` (blocking raw OkHttp).

**Fixed callback `http://localhost:8585/callback`** — Auth0 requires an exact, pre-registered
redirect URI. Dynamic ports or `127.0.0.1` fail with "Callback URL mismatch", so the port/host are
constants, not ephemeral. A 1-minute `timeout` aborts a stalled login.

After login, `SessionManager` holds the mutable token; `AuthorizationInterceptor` attaches the
bearer to every API call and `TokenAuthenticator` transparently refreshes on a 401.

### 2. JWT claims

[`JwtClaimsDto.from`](src/main/java/com/hivemq/platform/demo/domain/dto/JwtClaimsDto.java) base64-
decodes the **access token** payload (`JwtUtils.decodeClaims`) and extracts:

- `orgId` ← `orgs[0].id`
- `pulseBaseUrl` ← `orgs[0].pulse.serverUrl`
- `agentxBaseUrl` ← `orgs[0].agentx.serverUrl`
- `email` ← the **namespaced claim `https://hmqc.cloud.email`** (added by an Auth0 Action; **not**
  the standard OIDC `email`)

Two safety nets: `withScheme()` prepends `https://` to bare-host URLs (claims carry `pulse2.dev…`
without a scheme, which Retrofit rejects), and `coalesce()` falls back to `application.yaml`
`fallback.*` values when a claim is missing (e.g. tokens often lack `agentx.serverUrl`, so AgentX
falls back to the configured staging URL). The extracted values are logged so you can see what
resolved.

### 3. Resource provisioning

[`ResourceProvisioner.provision()`](src/main/java/com/hivemq/platform/demo/provision/ResourceProvisioner.java)
= `Single.zip(pulseToken(), agentxToken(), ProvisionResult::from)`. The two arms run **in parallel**
because the Retrofit adapter is `RxJava3CallAdapterFactory.createWithScheduler(ioScheduler)` — each
arm's calls execute on a separate virtual thread. Each step is **find-or-create** (list → take first
if present, else create):

- **Pulse arm:** ensure project (`demo`) → ensure agent → mint **agent activation token**.
- **AgentX arm:** ensure network (`demo`) → ensure orchestrator (`docker`/`http`) → mint
  **enrollment token** → ensure orchestrator **agent** (the marketplace template).

The orchestrator agent is created from template `00000000-0000-4000-a000-000000000102` version
`1.0.4`, with environment:

| Var | Value |
|---|---|
| `ALERT_RECIPIENT` | the logged-in user's email (`claims.email()`) |
| `FACTORY_BROKER_URL` | `mqtt://broker:1883` (the broker's in-network address) |

`ProvisionResult(pulseToken, registrationToken)` feeds the container env in the next step.

### 4. Containers

[`ContainersRunner.run()`](src/main/java/com/hivemq/platform/demo/containers/ContainersRunner.java)
orchestrates, [`DockerManager`](src/main/java/com/hivemq/platform/demo/containers/DockerManager.java)
wraps docker-java calls in RxJava on the io scheduler. The flow is two parallel phases (every
`DockerManager` op carries its own `subscribeOn(ioScheduler)`, so `mergeArray` arms run on
separate virtual threads and fail fast):

```
Phase 1 (mergeArray):  ensure network "hivemq"  ||  build broker image  ||  pull orchestrator image
Phase 2 (mergeArray):  [recreate broker      -> wait broker healthy]
                    || [recreate orchestrator -> wait orchestrator healthy]
```

> **Trade-off:** Phase 2 drops the reference compose's `depends_on: broker healthy` ordering — the
> orchestrator starts alongside the broker rather than after it. This is expected to be safe because
> the orchestrator does not open an MQTT connection at startup (see
> [MQTT broker connectivity](#mqtt-broker-connectivity-troubleshooting) below). If a run ever shows
> the orchestrator wedged on a missing broker, re-gate its start on broker health.

- **Broker** — image **built at runtime** from [`docker/broker`](src/main/resources/docker/broker):
  `Dockerfile` extends `hivemq/hivemq4:latest` and copies `pulse.xml` (which reads
  `${ENV:PULSE_TOKEN}`). Uses image defaults otherwise (Control Center `admin`/`hivemq`, ports
  **1883** MQTT + **8080** Control Center, both published to the host). Network alias `broker`.
  Env: `PULSE_TOKEN`. Health: TCP check on 1883.
- **Orchestrator** — pulled image, mounts `/var/run/docker.sock`, env `CONTROL_PLANE_URL` +
  `AGENT_BUS_BROKER_URL` (`mqtt://broker:1883`) + `HIVEMQ_AGENTIC_REGISTRATION_TOKEN`. Health:
  HTTP `/health` on port 3000.

Re-runs are idempotent (force-remove by name + recreate). `restartUnlessStopped=false` so the
containers don't outlive the JVM; teardown force-removes both on `Ctrl+C`.

> Build-context resources (`docker/broker/Dockerfile`, `pulse.xml`) are read at runtime via
> `getResourceAsStream`, so they're embedded as native-image resource globs (see below).

#### MQTT broker connectivity (troubleshooting)

> ⚠️ The following is the current **working understanding** and is **not yet verified end-to-end** —
> confirm before relying on it. It explains why the parallel container start (above) is safe and
> what to check if the broker path misbehaves.

There are **two distinct broker references** — don't conflate them:

- **`AGENT_BUS_BROKER_URL`** (`mqtt://broker:1883`, on the **orchestrator**) — the *agent bus* for
  **inter-agent** MQTT messaging. Belief: the orchestrator does **not** dial this at startup; it
  only connects when a deployed agent actually needs to talk to *other* agents.
- **`FACTORY_BROKER_URL`** (`mqtt://broker:1883`, on the deployed **agent**) — where the Demo Sensor
  Evaluation agent is expected to read the `factory/sensor/#` data that `MockDataPublisher` produces.

Implications:
- **Parallel start is safe** because the orchestrator opens no MQTT connection at boot — there's
  nothing to "wait for the broker" for. And the agent we deploy runs **solo** (no peer agents), so
  `AGENT_BUS_BROKER_URL` is effectively **unused** for this demo.
- **Open question to confirm:** does the solo agent actually read sensor data from the broker via
  `FACTORY_BROKER_URL`? If **yes**, the broker is the essential conduit
  (`MockDataPublisher → broker → agent`) and these configs matter. If the agent ingests data some
  other way, then the broker — and possibly the whole local publish path — is **redundant** for this
  demo and could be simplified.

Troubleshooting cues:
- Orchestrator wedged at startup complaining about the broker → the "no startup connection"
  assumption is wrong; re-add broker-first ordering in `ContainersRunner.run()` (gate the
  orchestrator arm on broker health instead of running both in one `mergeArray`).
- Agent never flags anomalies even though `MockDataPublisher` logs them → the agent isn't receiving
  the data; check `FACTORY_BROKER_URL`, that `broker:1883` resolves inside the `hivemq` Docker
  network, and that the agent subscribes to `factory/sensor/#`.

**Candidate reordering — defer B4 until the containers are healthy (if a readiness issue appears):**
Today **B4** (`ensure orchestrator agent`, step 4) runs during cloud provisioning, **before any
container exists**. B4 is the step where the agent is created and (likely) deployed onto the
orchestrator — i.e. the moment the agent first actually needs the broker. If creating the agent
that early causes it to be deployed/activated against a broker that isn't up yet, the fix is to
**move B4 out of the AgentX provisioning arm and run it after step 5 (both containers healthy),
right before step 6 (publishing).** New order:

```
4. provision: A1–A3 (Pulse) ‖ B1–B3 (AgentX, stops at the enrollment token)
5. containers: broker ‖ orchestrator → both healthy
   B4. ensure orchestrator agent  (deploys onto the now-healthy orchestrator + reachable broker)
6. publish sensor data
```

Notes / constraints:
- **B3 must stay in step 4** — its enrollment token is the `HIVEMQ_AGENTIC_REGISTRATION_TOKEN` the
  orchestrator container needs at step 5. Only **B4** moves.
- B4 only needs the **`orchestratorId`** from B2 (it's a cloud call: `POST
  api/v1/orchestrators/{orchestratorId}/agents`), so deferring it just means carrying that id forward
  past step 5 — it does not need the container to make the call, only for the resulting agent to land
  on a ready orchestrator.
- This is **unverified** — only worth doing if B4-at-provisioning-time actually races the broker.
  Confirm the agent's real broker dependency first (see the open question above).

### 5. Mock sensor data

[`MockDataPublisher.publish()`](src/main/java/com/hivemq/platform/demo/mqtt/MockDataPublisher.java)
uses `Completable.using(connect, stream, disconnect)` — the connection is the managed resource, so
`disconnect()` only runs if `connect()` succeeded. It connects the **blocking** HiveMQ client to
`localhost:1883` (host-published port) and, every **3 s**, publishes one reading per sensor:

| Sensor | Topic | Mean ± stddev | Unit |
|---|---|---|---|
| temperature | `factory/sensor/temperature` | 22.0 ± 1.0 | C |
| pressure | `factory/sensor/pressure` | 101.3 ± 0.5 | kPa |
| vibration | `factory/sensor/vibration` | 0.5 ± 0.08 | mm/s |

Payload: `{"value": <rounded 3dp>, "unit": "...", "ts": <epoch-ms>}` (QoS 0). Values are a Gaussian
baseline; after **60 cycles**, each sample has a **5 %** chance of an **anomaly** (±30 % of the mean
+ noise) so the agent's ±20 %-of-rolling-mean rule trips. These numbers exactly replicate the
reference `demo-sensor-publisher.py` and are **tuned to the template** — don't relax them. The
stream is pinned to the io scheduler so the blocking publishes run on virtual threads.

---

## Configuration

[`application.yaml`](src/main/resources/application.yaml) is bundled into the binary and read by
`Loader` into the `Configuration` record. **All values are currently staging.**

```yaml
auth0:
  domain: auth.staging.hmqc.dev
  client-id: Q6lrZn0pNmfMfgWGFq1TBirs150K2F3d
  audience: hivemq-cloud-api
  scope: openid profile email offline_access
fallback:                       # used when a claim is missing from the token
  org-id: "dummy"
  pulse-base-url: "https://pulse2.dev.hmqc.dev"
  agentx-base-url: "https://staging.act.hivemq.com"
```

Everything else (ports, names, healthcheck timings, sensor/anomaly params, the email claim key,
template id) lives in [`Constants.java`](src/main/java/com/hivemq/platform/demo/constants/Constants.java).

---

## Build & run

Prerequisites: **GraalVM CE 25** on `JAVA_HOME` (the build uses it directly —
`toolchainDetection = false`) and a running **Docker** daemon.

```bash
# JVM run (fastest iteration)
./gradlew run

# Native image
./gradlew nativeCompile
./build/native/nativeCompile/demo

```

The above builds from source; **`install.sh`** is the remote counterpart — it downloads the
prebuilt binary for the host OS/arch into a temp dir, verifies its checksum, and runs it (no build
tools needed on the target). See [Install](#install) for the one-liner; the binaries it pulls are
published by [`.github/workflows/release.yml`](.github/workflows/release.yml).

Login needs an interactive browser; the auth flow is browser+loopback, not stdin.

---

## Native image

The binary ships via `curl | sh` and runs from a temp dir, so the codebase stays native-friendly:
resources are embedded, no AWT, compile-time DI (Dagger) instead of reflection.

`graalvmNative` config: `metadataRepository { enabled = true }` (pulls community metadata for
OkHttp, Apache HttpClient, etc.), `buildArgs` `--no-fallback` + `-H:+ReportExceptionStackTraces`.
**Three metadata dirs are maintained by hand** under
`src/main/resources/META-INF/native-image/` (originally seeded by the tracing agent; the agent task
is no longer wired):

| Dir | Contents | Why |
|---|---|---|
| `com.hivemq.platform.demo/demo` | baseline: TLS/crypto (`sun.security.*`), SPI resources, ICU/IDN, Retrofit dynamic proxies, **+ the `docker/broker/*` resource globs** | hand-deriving TLS/SPI is a footgun, so keep the agent seed |
| `com.hivemq.platform.demo/dto` | our DTO + config records | Jackson (de)serialization of our types |
| `com.github.docker-java/docker-java` | `api.model` + `api.command` + **`core.command`** + the **`core` config-file classes** (`DockerConfigFile`, `DockerContextMetaFile` + nested) — 481 classes | docker-java serializes request bodies via these impls **and Jackson-deserializes `~/.docker/config.json` + docker context meta files at client build time** |

**Fixing a new `MissingReflectionRegistrationError`:** add the type to the matching `reflect-config.json`
(`{"name": "...", "allDeclaredConstructors": true, "allDeclaredMethods": true, "allDeclaredFields": true}`)
and recompile — same loop used for docker-java's `core.command`. New build-context files need both a
`Constants.Containers.BROKER_BUILD_FILES` entry **and** a resource glob in the `demo` metadata.

> Three native-only bugs already fixed this way: docker-java sending an empty container `config`
> (missing `core.command` reflection), the broker build context not being embedded (missing
> resource globs), and `DefaultDockerClientConfig.build()` failing to parse `~/.docker/config.json`
> (missing `core.DockerConfigFile` / `DockerContextMetaFile` reflection — the error this surfaced as
> `Cannot construct instance of DockerConfigFile … this appears to be a native image`).

---

## Design decisions

- **Blocking MQTT client wrapped in RxJava3, not the client's "RX" API.** The hivemq-mqtt-client's
  reactive API is **RxJava 2** (`io.reactivex.*`) — confirmed in 1.3.15's POM, the resolved
  classpath, and even GitHub `master`; there is no RxJava3 release. Bridging RxJava2↔3 added noise
  (`fromCompletionStage` over the async client + a `defer` to avoid eager `connect()`), so we use
  the **blocking** client wrapped in `Completable.fromAction` / `Completable.using`. Cleaner, no
  bridge, and free because everything runs on virtual threads. (Vert.x MQTT is the only natively-RX3
  client but is heavier and Netty-based like this one anyway.)
- **RxJava kept despite a mostly-linear flow.** Justified by parallel provisioning (`zip`),
  timeouts, the publish stream, and being the foundation for the full CLI. Virtual threads +
  `StructuredTaskScope` was the alternative.
- **`Single.using` / `Completable.using` for resource lifecycles** (the loopback `ServerSocket`, the
  MQTT connection) — disposer only runs on successful acquisition, avoiding "disconnect after a
  failed connect".
- **Token-scoped subcomponent** because the API base URLs come from the JWT.
- **Broker image built at runtime** (not pre-baked) so the build context can be tested
  independently and only Pulse is customized.
- **palantir-java-format** (over google-java-format) — GJF is zero-config and broke fluent/lambda
  chains aggressively; palantir keeps them compact (4-space indent, wider lines).
- **No Javadoc** on methods/classes by request — code reads top-to-bottom; comments only for
  non-obvious *why*.

---

## Conventions

- **No Javadoc.** Short `//` comments only where the *why* isn't obvious.
- **Lombok** `@RequiredArgsConstructor` + `@Slf4j`; constructor-injected `final` fields.
- **Constants** centralize tunables in `Constants.java` (nested interfaces by area).
- **Logging** via SLF4J; concise network logging in `LoggingInterceptor`; virtual threads are named
  `io-N` so log lines show a thread.
- **Formatting** enforced by `./gradlew spotlessApply` (palantir, 4-space).

---

## Pre-production TODOs

Tracked in detail in the session memory; summary:

1. **Production cutover** — everything is staging. Switch together:
   - `application.yaml` `auth0.domain`, `auth0.client-id`, and the `fallback.*` Pulse/AgentX URLs.
   - Deploy a **prod Auth0 app** with the same Action (injecting `orgs` and the
     `https://hmqc.cloud.email` claim) and use its client id.
   - **Make the repo public**, then **delete `internal_install.sh`** (the interim `gh`-based
     installer); `install.sh` becomes the single, auth-free entry point (see [Install](#install)).
2. **Make the email claim configurable** — `Constants.Jwt.EMAIL = "https://hmqc.cloud.email"` is a
   hardcoded namespaced key. Move it to `application.yaml` (`auth0.email-claim`) and fall back to the
   standard `email` claim, so a different prod namespace doesn't silently yield an empty
   `ALERT_RECIPIENT` (the `Email: (...)` provisioning log exposes it today).
3. **Release pipeline — done.** [`.github/workflows/release.yml`](.github/workflows/release.yml)
   tags each `main` change via axion-release, builds `demo-linux-amd64` + `demo-darwin-arm64` (per-OS
   runners, since native images can't be cross-compiled), and publishes the binaries, `.sha256`
   checksums, and both installer scripts. `install.sh` (public) downloads from the release's public
   URL; `internal_install.sh` (interim) downloads via the `gh` CLI while the repo is private. Both
   verify the checksum and support a `DEMO_VERSION` pin. Remaining: add `arm64` Linux / `amd64` macOS
   targets if needed.
4. **MQTT publisher native pass** — `nativeCompile` in CI may surface Netty metadata gaps from the
   MQTT client; resolve via the hand-metadata loop above.
```
