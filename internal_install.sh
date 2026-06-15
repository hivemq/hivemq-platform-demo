#!/usr/bin/env bash
#
# Internal installer for the HiveMQ Platform Demo — PRIVATE repo, pre-public.
#
# Uses the GitHub CLI (`gh`) for both auth and download, so there is no token to embed anywhere.
# Requires `gh` installed (https://cli.github.com) and authenticated (`gh auth login`) with read
# access to the repo. Downloads the prebuilt native binary for the current OS/arch from the latest
# release, verifies its sha256, runs it in the foreground, and cleans up the temp dir on exit.
#
# Bootstrap one-liner (fetches THIS script from the private repo via gh, then runs it):
#   gh api repos/hivemq/hivemq-platform-demo/contents/internal_install.sh \
#     -H "Accept: application/vnd.github.raw" | bash
#
# >> Temporary: when the repo goes public, delete this script and use install.sh instead. <<
#
# Overrides (env):
#   DEMO_REPO      owner/repo         (default: hivemq/hivemq-platform-demo)
#   DEMO_VERSION   release tag to pin (default: latest release)
#

set -euo pipefail

REPO="${DEMO_REPO:-hivemq/hivemq-platform-demo}"

# --- require an authenticated gh -------------------------------------------
if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required: https://cli.github.com" >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

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

# --- download via gh (no token needed — gh uses its stored credentials) ----
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Downloading ${asset} via gh …" >&2
# A bare tag arg targets that release; omitting it takes the latest. --pattern globs asset names,
# so we fetch exactly the binary and its checksum.
gh release download ${DEMO_VERSION:+"$DEMO_VERSION"} \
  --repo "$REPO" \
  --pattern "$asset" \
  --pattern "${asset}.sha256" \
  --dir "$tmp"

# --- verify checksum -------------------------------------------------------
echo "Verifying checksum …" >&2
expected="$(tr -d '[:space:]' < "${tmp}/${asset}.sha256")"
if command -v sha256sum >/dev/null 2>&1; then
  actual="$(sha256sum "${tmp}/${asset}" | cut -d' ' -f1)"
else
  actual="$(shasum -a 256 "${tmp}/${asset}" | cut -d' ' -f1)"
fi
if [ "$expected" != "$actual" ]; then
  echo "checksum mismatch for ${asset}:" >&2
  echo "  expected ${expected}" >&2
  echo "  actual   ${actual}" >&2
  exit 1
fi

bin="${tmp}/demo"
mv "${tmp}/${asset}" "${bin}"
chmod +x "${bin}"

# --- run in the foreground; lifecycle bound to this script -----------------
# NOT exec: we want the EXIT trap above to clean the temp dir after the binary ends. Ctrl+C
# still reaches the binary (it's the foreground process), and its own JVM shutdown hook tears
# down the containers. stdin is the curl/gh pipe, so reattach the terminal for any interactive
# prompts (the Auth0 sign-in) when one is available.
echo "Starting demo — Ctrl+C stops it and tears down its containers." >&2
if [ -e /dev/tty ]; then
  "${bin}" < /dev/tty
else
  "${bin}"
fi
