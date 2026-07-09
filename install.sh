#!/usr/bin/env bash
#
# Remote installer for the HiveMQ Platform Demo (public repo).
#
# Downloads the prebuilt GraalVM native binary for the current OS/arch from this repo's public
# GitHub Release, verifies its sha256, runs it in the foreground (lifecycle bound to this script),
# and removes the temp dir on exit. No build tools and no auth needed on the target machine.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/hivemq/hivemq-platform-demo/main/install.sh | bash
#
# (While the repo is still private, use internal_install.sh instead — it downloads via the
#  GitHub CLI. This script's public URLs only resolve once the repo and its releases are public.)
#
# Overrides (env):
#   DEMO_REPO      owner/repo         (default: hivemq/hivemq-platform-demo)
#   DEMO_VERSION   release tag to pin (default: latest release)
#

set -euo pipefail

REPO="${DEMO_REPO:-hivemq/hivemq-platform-demo}"

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

# --- resolve public download URLs (no auth — public release assets) --------
base="https://github.com/${REPO}/releases"
if [ -n "${DEMO_VERSION:-}" ]; then
  bin_url="${base}/download/${DEMO_VERSION}/${asset}"
  sum_url="${base}/download/${DEMO_VERSION}/${asset}.sha256"
else
  bin_url="${base}/latest/download/${asset}"
  sum_url="${base}/latest/download/${asset}.sha256"
fi

# --- download into a temp dir (always cleaned up) --------------------------
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
bin="${tmp}/demo"

echo "Downloading ${asset} …" >&2
curl -fsSL -o "${bin}" "${bin_url}"

# --- verify checksum (recommended for curl|bash) ---------------------------
echo "Verifying checksum …" >&2
expected="$(curl -fsSL "${sum_url}" | tr -d '[:space:]')"
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
