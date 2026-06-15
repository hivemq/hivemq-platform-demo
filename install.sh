#!/usr/bin/env bash
#
# Remote installer for the HiveMQ Platform Demo.
#
# Downloads the prebuilt GraalVM native binary for the current OS/arch from this repo's
# GitHub Release, verifies its sha256, runs it in the foreground (lifecycle bound to this
# script), and removes the temp dir on exit. No build tools needed on the target machine.
#
# --- Usage (private repo, current) -----------------------------------------
# The repo is private for now, so every fetch needs a PAT (fine-grained, this repo,
# Contents: Read-only — or a classic token with `repo` scope). The token appears twice:
# once to fetch this script via the Contents API, once exported so it can pull the binary.
#
#   curl -fsSL \
#     -H "Authorization: Bearer ghp_xxx" \
#     -H "Accept: application/vnd.github.raw" \
#     https://api.github.com/repos/hivemq/hivemq-platform-demo/contents/install.sh \
#     | GH_TOKEN=ghp_xxx bash
#
# --- Usage (once the repo is public) ---------------------------------------
# No token needed; this collapses to the clean form:
#
#   curl -fsSL https://raw.githubusercontent.com/hivemq/hivemq-platform-demo/main/install.sh | bash
#
# (When public, GH_TOKEN can be omitted and the download falls back to the public asset URL.)
#
# --- Overrides (env) -------------------------------------------------------
#   GH_TOKEN / GITHUB_TOKEN   GitHub PAT (required while the repo is private)
#   DEMO_REPO                 owner/repo            (default: hivemq/hivemq-platform-demo)
#   DEMO_VERSION              release tag to pin    (default: latest release)
#

set -euo pipefail

REPO="${DEMO_REPO:-hivemq/hivemq-platform-demo}"
TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
API="https://api.github.com/repos/${REPO}"

# --- detect platform -------------------------------------------------------
os="$(uname -s | tr '[:upper:]' '[:lower:]')"
arch="$(uname -m)"
case "$arch" in
  x86_64 | amd64) arch="amd64" ;;
  aarch64 | arm64) arch="arm64" ;;
  *) echo "unsupported architecture: $arch" >&2; exit 1 ;;
esac
case "$os" in
  linux | darwin) ;;
  *) echo "unsupported OS: $os" >&2; exit 1 ;;
esac

asset="demo-${os}-${arch}"

# Only these targets are published today (see .github/workflows/release.yml).
case "$asset" in
  demo-linux-amd64 | demo-darwin-arm64) ;;
  *)
    echo "no prebuilt binary for ${asset}." >&2
    echo "available targets: demo-linux-amd64, demo-darwin-arm64." >&2
    exit 1
    ;;
esac

# --- auth helpers ----------------------------------------------------------
# Curl args for authenticated GitHub API calls. While the repo is private a token is
# required; fail early with a clear message rather than a confusing 404 later.
auth_args=()
if [ -n "$TOKEN" ]; then
  auth_args=(-H "Authorization: Bearer ${TOKEN}")
else
  echo "GH_TOKEN (or GITHUB_TOKEN) is required while ${REPO} is private." >&2
  echo "Create a fine-grained PAT (Contents: Read-only) and pass it, e.g.:" >&2
  echo "  ... | GH_TOKEN=ghp_xxx bash" >&2
  exit 1
fi

# --- resolve the release ---------------------------------------------------
if [ -n "${DEMO_VERSION:-}" ]; then
  release_url="${API}/releases/tags/${DEMO_VERSION}"
else
  release_url="${API}/releases/latest"
fi

echo "Resolving release from ${REPO} …" >&2
release_json="$(curl -fsSL "${auth_args[@]}" -H "Accept: application/vnd.github+json" "${release_url}")"

# Map an asset NAME to its API download URL. Private repos have no name-based authenticated
# download — only the numeric asset id works — so we parse it out of the release JSON.
# No jq/python dependency: awk over the pretty-printed JSON. Within each asset object the
# `.../releases/assets/{id}` url line precedes the name line; the uploader's url does not
# match the releases/assets/ pattern, so it can't clobber the captured value.
asset_url_for() {
  printf '%s\n' "$release_json" | awk -v want="$1" '
    /"url":/ && /releases\/assets\// { match($0, /https[^"]*/); u = substr($0, RSTART, RLENGTH) }
    $0 ~ "\"name\": \"" want "\"" { print u; exit }
  '
}

bin_url="$(asset_url_for "$asset")"
sum_url="$(asset_url_for "${asset}.sha256")"

if [ -z "$bin_url" ]; then
  echo "release does not contain asset '${asset}'." >&2
  exit 1
fi

# --- download into a temp dir (always cleaned up) --------------------------
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
bin="${tmp}/demo"

echo "Downloading ${asset} …" >&2
curl -fsSL "${auth_args[@]}" -H "Accept: application/octet-stream" -L "${bin_url}" -o "${bin}"

# --- verify checksum (recommended for curl|bash) ---------------------------
if [ -n "$sum_url" ]; then
  echo "Verifying checksum …" >&2
  expected="$(curl -fsSL "${auth_args[@]}" -H "Accept: application/octet-stream" -L "${sum_url}" | tr -d '[:space:]')"
  if command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "${bin}" | cut -d' ' -f1)"
  else
    actual="$(shasum -a 256 "${bin}" | cut -d' ' -f1)"
  fi
  if [ "$expected" != "$actual" ]; then
    echo "checksum mismatch for ${asset}:" >&2
    echo "  expected ${expected}" >&2
    echo "  actual   ${actual}" >&2
    exit 1
  fi
else
  echo "warning: no .sha256 published for ${asset}; skipping verification." >&2
fi

chmod +x "${bin}"

# --- run in the foreground; lifecycle bound to this script -----------------
# NOT exec: we want the EXIT trap above to clean the temp dir after the binary ends. Ctrl+C
# still reaches the binary (it's the foreground process), and its own JVM shutdown hook tears
# down the containers. stdin is the curl pipe, so reattach the terminal for any interactive
# prompts (the Auth0 sign-in) when one is available.
echo "Starting demo — Ctrl+C stops it and tears down its containers." >&2
if [ -e /dev/tty ]; then
  "${bin}" < /dev/tty
else
  "${bin}"
fi
