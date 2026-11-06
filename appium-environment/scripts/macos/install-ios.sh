#!/usr/bin/env bash
# install-ios.sh — macOS: Xcode Command Line Tools, Xcode.app (checked + guided), license, applesimutils.
# Xcode.app comes from the App Store and is never installed here (decision D2); xcuitest needs it
# because WebDriverAgent is built by Xcode at run time.
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")/../common" && pwd)/lib.sh"

# ── Command Line Tools ─────────────────────────────────────────────────────────────────────────
clt_check()   { xcode-select -p 2>/dev/null; }
# `xcode-select --install` opens Apple's dialog and returns immediately — the install finishes
# outside this script, so report "needs a manual step" (exit 2) rather than success or failure.
clt_install() { xcode-select --install >/dev/null 2>&1; return 2; }
clt_verify()  { clt_check || { printf 'finish the "Install Command Line Developer Tools" dialog, then re-run setup.sh'; return 1; }; }
run_step "Xcode Command Line Tools" clt_check clt_install clt_verify

# ── Xcode.app (guided) ─────────────────────────────────────────────────────────────────────────
# Only a full Xcode answers `xcodebuild -version`; a CLT-only machine errors out here.
xcode_check() { local v; v=$(xcodebuild -version 2>/dev/null) || return 1; first_line "$v"; }
run_guided "Xcode.app" xcode_check "not installed — required for iOS (Xcode builds WebDriverAgent). Install Xcode from the App Store, open it once, then re-run setup.sh"

# ── license — only meaningful once Xcode.app is there ──────────────────────────────────────────
if xcode_check >/dev/null 2>&1; then
  license_check()   { xcodebuild -license check >/dev/null 2>&1 && printf 'accepted'; }
  license_install() { sudo xcodebuild -license accept; }
  license_verify()  { license_check || { printf 'run: sudo xcodebuild -license accept'; return 1; }; }
  run_step "Xcode license" license_check license_install license_verify
fi

# ── applesimutils — Simulator permission steps (`grant permission …`) ──────────────────────────
asu_check()   { have applesimutils && printf 'applesimutils at %s' "$(command -v applesimutils)"; }
asu_install() { brew tap wix/brew && brew install applesimutils; }
asu_verify()  { asu_check || { printf 'brew tap wix/brew && brew install applesimutils'; return 1; }; }
run_step "applesimutils (Simulator permission steps)" asu_check asu_install asu_verify
