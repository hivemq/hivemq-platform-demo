# HiveMQ Platform Demo

A single-binary CLI that demonstrates two HiveMQ Cloud products — **Pulse** and **AgentX (Agentic
Orchestrator)** — end to end. One `curl | bash` logs you in via Auth0, provisions the cloud
resources both products need, starts the matching Docker containers locally, and publishes synthetic
sensor data that drives a marketplace AI agent which emails an alert whenever a reading drifts too
far from normal.

Everything is tied to the CLI process: on `Ctrl+C` the containers are torn down and the binary
exits. The only thing written to disk at runtime is a small broker build context in a temp dir.

## What it does

In one run it:

1. **Authenticates** you against Auth0 (Authorization Code + PKCE via a local loopback server; opens
   your browser).
2. **Provisions** the cloud-side resources for both products in parallel and mints their tokens — a
   **Pulse activation token** for the broker and an **AgentX enrollment token** for the orchestrator.
3. **Runs two local containers**: a **HiveMQ broker** (Pulse enabled, image built at runtime) and the
   **Agentic Orchestrator**, which deploys an agent from the **Demo Sensor Evaluation** marketplace
   template.
4. **Publishes synthetic sensor data** so the agent's anomaly rule fires — it emails an alert when a
   reading drifts >20 % from its rolling mean.

The alert agent emails via SendGrid; the key is fetched at runtime from the Console API during
provisioning, so there is nothing to configure.

## Requirements

- A running **Docker** daemon.
- A **browser** — login is browser + loopback, not stdin.

## Install & run

One line takes you from zero to the running demo: it detects your OS/arch, downloads the matching
prebuilt native binary from the latest GitHub Release, verifies its checksum, and launches straight
into the Auth0 sign-in.

```bash
curl -fsSL https://raw.githubusercontent.com/hivemq/hivemq-platform-demo/main/install.sh | bash
```

Alternatively, install via the [GitHub CLI](https://cli.github.com) — no URL to hardcode, `gh`
carries the auth (run `gh auth login` once). It fetches `internal_install.sh`, which then downloads
the binary with `gh release download`:

```bash
gh api repos/hivemq/hivemq-platform-demo/contents/internal_install.sh \
  -H "Accept: application/vnd.github.raw" | bash
```

Built targets: **`demo-linux-amd64`** and **`demo-darwin-arm64`**. Overrides (env):
`DEMO_VERSION=vX.Y.Z` pins a release (default: latest); `DEMO_REPO` targets a fork. Releases —
binaries, `.sha256` checksums, and the installer — are published automatically by
[`.github/workflows/release.yml`](.github/workflows/release.yml) on each tagged change.

## Build from source

Requires **GraalVM CE 25** on `JAVA_HOME` and a running Docker daemon.

```bash
./gradlew run              # JVM run (fastest iteration)

./gradlew nativeCompile    # native binary
./build/native/nativeCompile/demo
```

## Configuration

[`application.yaml`](src/main/resources/application.yaml) is bundled into the binary and read at
startup — the Auth0 settings plus `fallback.*` values used when a claim is missing from the token.
Everything else (ports, container names, healthcheck timings, sensor/anomaly parameters, the agent
template id) lives in
[`Constants.java`](src/main/java/com/hivemq/platform/demo/constants/Constants.java).
