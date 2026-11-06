#!/usr/bin/env bash
# verify-ios.sh — macOS: is the iOS side usable right now? (Xcode selected, Simulator runtimes visible)
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")/../common" && pwd)/lib.sh"

xcode_version_check() { local v; v=$(xcodebuild -version 2>/dev/null) || return 1; printf '%s · %s' "$(first_line "$v")" "$(xcode-select -p 2>/dev/null)"; }
run_check "Xcode" xcode_version_check "Xcode.app not installed or not selected — App Store, then: sudo xcode-select -s /Applications/Xcode.app" warn

simulators_check() {
  local json n
  json=$(xcrun simctl list devices available -j 2>/dev/null) || return 1
  n=$(printf '%s' "$json" | grep -c '"udid"' || true)
  if [ "${n:-0}" -gt 0 ]; then printf '%s simulator(s) available (xcrun simctl list devices available)' "$n"
  else printf 'no Simulator runtime — Xcode → Settings → Components, add an iOS Simulator runtime'; return 1; fi
}
run_check "iOS Simulators" simulators_check "xcrun simctl is not usable — needs Xcode.app (real-iPhone runs need it too: WebDriverAgent signing)" warn
