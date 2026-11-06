#!/usr/bin/env bash
# verify-android.sh — macOS: is the Android side usable right now? (adb, env vars, connected devices)
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")/../common" && pwd)/lib.sh"

# Prefer the adb under ANDROID_HOME (this run may have just installed it) over a stale one on PATH.
android_adb() {
  if [ -x "${ANDROID_HOME:-}/platform-tools/adb" ]; then printf '%s/platform-tools/adb' "$ANDROID_HOME"
  else command -v adb 2>/dev/null; fi
}

adb_check() {
  local adb v
  adb=$(android_adb) && [ -x "$adb" ] || return 1
  v=$("$adb" version 2>/dev/null) || return 1
  printf '%s' "$(first_line "$v")"
}
run_check "adb" adb_check "adb not found — re-run without --skip-android, or open a NEW terminal if the profile block was just written"

env_check() {
  [ -n "${ANDROID_HOME:-}" ] && [ -n "${JAVA_HOME:-}" ] || return 1
  printf 'ANDROID_HOME=%s · JAVA_HOME=%s' "$ANDROID_HOME" "$JAVA_HOME"
}
run_check "ANDROID_HOME / JAVA_HOME" env_check "not set in this shell — open a NEW terminal (profile block) or export them"

devices_check() {
  local adb n
  adb=$(android_adb) && [ -x "$adb" ] || return 1
  n=$("$adb" devices 2>/dev/null | awk 'NR>1 && $2=="device"{n++} END{print n+0}')
  if [ "$n" -gt 0 ]; then printf '%s device(s) connected' "$n"
  else printf 'no device connected — plug in a phone (USB debugging on) or start an emulator before /sungen:run-test'; fi
}
run_check "Android devices" devices_check "adb is not usable yet" warn
