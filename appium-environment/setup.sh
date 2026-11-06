#!/usr/bin/env bash
# setup.sh — macOS entry point of the sungen Appium environment bundle.
#
#   ./appium-environment/setup.sh [--check] [--skip-android] [--skip-ios] [--no-profile]
#
# Emitted by `sungen init --mobile` / `sungen capability add mobile`, which also run it once.
# Re-run it any time: every step is check → install → verify and a satisfied step is skipped.
#   --check          report what is missing, install nothing (side-effect free)
#   --skip-android   skip JDK / Android SDK / uiautomator2
#   --skip-ios       skip Xcode CLT / applesimutils / xcuitest
#   --no-profile     do not write the ANDROID_HOME / JAVA_HOME / PATH block to your shell profile
# Exit: 0 ready (or only warnings) · 1 something failed · 2 unsupported OS / bad flag
set -u

BUNDLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIR=$(cd "$BUNDLE_DIR/.." && pwd)
export PROJECT_DIR
. "$BUNDLE_DIR/scripts/common/lib.sh"

usage() { sed -n '4,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

for arg in "$@"; do
  case "$arg" in
    --check)        SUNGEN_ENV_CHECK_ONLY=1 ;;
    --skip-android) SUNGEN_ENV_SKIP_ANDROID=1 ;;
    --skip-ios)     SUNGEN_ENV_SKIP_IOS=1 ;;
    --no-profile)   SUNGEN_ENV_NO_PROFILE=1 ;;
    -h|--help)      usage; exit 0 ;;
    *) printf 'unknown flag: %s\n\n' "$arg" >&2; usage >&2; exit 2 ;;
  esac
done

case "$(uname -s)" in
  Darwin) ;;
  Linux)
    printf 'ℹ Linux detected — this bundle provisions macOS and Windows.\n'
    printf '  Android on Linux: see README.md → "Manual fallback" (JDK + Android SDK via your package manager,\n'
    printf '  then `sungen capability add mobile` for the project-local Appium + uiautomator2).\n'
    exit 0 ;;
  *) printf '✗ unsupported OS: %s (macOS: setup.sh · Windows: setup.ps1)\n' "$(uname -s)" >&2; exit 2 ;;
esac

cd "$PROJECT_DIR"
if [ "$SUNGEN_ENV_CHECK_ONLY" = 1 ]; then section "sungen · Appium environment — check only (macOS)"
else section "sungen · Appium environment setup (macOS)"; fi
printf '%sproject: %s%s\n' "$C_DIM" "$PROJECT_DIR" "$C_RESET"

# ── 1/5 prerequisites ──────────────────────────────────────────────────────────────────────────
section "1/5  Prerequisites"
. "$BUNDLE_DIR/scripts/common/verify-node.sh"

brew_check()   { have brew && printf 'brew at %s' "$(command -v brew)"; }
brew_install() {
  NONINTERACTIVE=1 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)" || return 1
  # The installer does not touch the running shell — pick brew up for the rest of this run.
  if [ -x /opt/homebrew/bin/brew ]; then eval "$(/opt/homebrew/bin/brew shellenv)"
  elif [ -x /usr/local/bin/brew ]; then eval "$(/usr/local/bin/brew shellenv)"; fi
}
brew_verify()  { brew_check || { printf 'Homebrew not found after install — https://brew.sh, then re-run'; return 1; }; }
run_step "Homebrew" brew_check brew_install brew_verify

# ── 2/5 Android ────────────────────────────────────────────────────────────────────────────────
if [ "$SUNGEN_ENV_SKIP_ANDROID" != 1 ]; then
  section "2/5  Android (JDK, SDK, adb, emulator)"
  . "$BUNDLE_DIR/scripts/macos/install-android.sh"
else
  section "2/5  Android"; log_skip "Android toolchain" "--skip-android"
fi

# ── 3/5 iOS ────────────────────────────────────────────────────────────────────────────────────
if [ "$SUNGEN_ENV_SKIP_IOS" != 1 ]; then
  section "3/5  iOS (Xcode, applesimutils)"
  . "$BUNDLE_DIR/scripts/macos/install-ios.sh"
else
  section "3/5  iOS"; log_skip "iOS toolchain" "--skip-ios"
fi

# ── 4/5 Appium ─────────────────────────────────────────────────────────────────────────────────
section "4/5  Appium (project-local) + platform drivers"
. "$BUNDLE_DIR/scripts/common/install-appium.sh"

# ── 5/5 health ─────────────────────────────────────────────────────────────────────────────────
section "5/5  Health check"
[ "$SUNGEN_ENV_SKIP_ANDROID" != 1 ] && . "$BUNDLE_DIR/scripts/macos/verify-android.sh"
[ "$SUNGEN_ENV_SKIP_IOS" != 1 ]     && . "$BUNDLE_DIR/scripts/macos/verify-ios.sh"
. "$BUNDLE_DIR/scripts/common/verify-appium.sh"

print_summary
