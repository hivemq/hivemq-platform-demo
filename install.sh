#!/usr/bin/env bash
#
# Remote installer — hosted and run as:
#   curl -fsSL https://<host>/install.sh | bash
#
# Downloads the prebuilt GraalVM native binary for the current OS/arch into a
# temp dir, runs it in the foreground (lifecycle bound to this script), and
# removes the temp dir on exit. No build tools needed on the target machine.
#

set -euo pipefail

# Where the published binaries live. Override for local testing, e.g.:
#   curl -fsSL .../install.sh | DEMO_BASE_URL=http://localhost:8000 bash
BASE_URL="${DEMO_BASE_URL:-https://REPLACE-ME.example.com/demo}"   # TODO: real release host

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

# --- download into a temp dir (always cleaned up) --------------------------
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
bin="${tmp}/demo"

echo "Downloading ${asset} …" >&2
curl -fsSL "${BASE_URL}/${asset}" -o "${bin}"
chmod +x "${bin}"

# Recommended for curl|bash: verify integrity. Publish ${asset}.sha256 next to
# the binary and uncomment (sha256sum on Linux, `shasum -a 256` on macOS):
# echo "Verifying checksum …" >&2
# curl -fsSL "${BASE_URL}/${asset}.sha256" -o "${bin}.sha256"
# (cd "${tmp}" && sha256sum -c "demo.sha256")

# --- run in the foreground; lifecycle bound to this script -----------------
# NOT exec: we want the EXIT trap above to clean the temp dir after the binary
# ends. Ctrl+C still reaches the binary (it's the foreground process), and its
# own JVM shutdown hook tears down the containers. stdin is the curl pipe, so
# reattach the terminal for any interactive prompts when one is available.
echo "Starting demo — Ctrl+C stops it and tears down its containers." >&2
if [ -e /dev/tty ]; then
  "${bin}" < /dev/tty
else
  "${bin}"
fi
