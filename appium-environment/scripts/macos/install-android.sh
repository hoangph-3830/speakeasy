#!/usr/bin/env bash
# install-android.sh — macOS: JDK ≥ 17, Android SDK root, platform-tools + emulator, shell profile block.
# Headless via Homebrew (decision D2): `openjdk@21` + the `android-commandlinetools` cask + sdkmanager.
# Android Studio is optional (only for an on-screen emulator) and is never installed here.
# Sourced by setup.sh (shares the runner + counters); runnable standalone for debugging.
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")/../common" && pwd)/lib.sh"

JDK_MIN=17
BREW_SDK_ROOT=$( { have brew && printf '%s/share/android-commandlinetools' "$(brew --prefix)"; } 2>/dev/null || true)

# ── JDK ────────────────────────────────────────────────────────────────────────────────────────
# Any working JDK ≥ 17 is accepted (Temurin, Corretto, Oracle, asdf…) — we never replace a good one.
# resolve_* run in THIS shell (not a subshell) so their exports survive; *_check run captured.
resolve_java_home() {
  local major home
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    major=$(java_major_of "$JAVA_HOME/bin/java"); [ -n "$major" ] && [ "$major" -ge "$JDK_MIN" ] && return 0
  fi
  if have java; then
    major=$(java_major_of java)
    if [ -n "$major" ] && [ "$major" -ge "$JDK_MIN" ]; then
      home=$(java -XshowSettings:properties -version 2>&1 | awk -F' = ' '/java\.home/{print $2}')
      [ -d "$home" ] && { export JAVA_HOME=$home; return 0; }
    fi
  fi
  # Homebrew openjdk@21 present but not on PATH (brew keeps it keg-only).
  if have brew && [ -x "$(brew --prefix openjdk@21 2>/dev/null)/bin/java" ]; then export JAVA_HOME; JAVA_HOME=$(brew --prefix openjdk@21); return 0; fi
  return 1
}
jdk_check() {
  local major
  [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] || return 1
  major=$(java_major_of "$JAVA_HOME/bin/java"); [ -n "$major" ] && [ "$major" -ge "$JDK_MIN" ] || return 1
  printf 'java %s · JAVA_HOME=%s' "$major" "$JAVA_HOME"
}
jdk_install() { brew install openjdk@21 && { export JAVA_HOME; JAVA_HOME=$(brew --prefix openjdk@21); }; }
jdk_verify()  { jdk_check || { printf 'no JDK ≥ %s — brew install openjdk@21 && export JAVA_HOME=$(brew --prefix openjdk@21)' "$JDK_MIN"; return 1; }; }
resolve_java_home || true
run_step "JDK ≥ $JDK_MIN" jdk_check jdk_install jdk_verify

# ── Android SDK root ───────────────────────────────────────────────────────────────────────────
# find_sdkmanager <sdk root> → path of sdkmanager (cmdline-tools/latest, any cmdline-tools/*, legacy tools/bin)
find_sdkmanager() {
  local root=$1 c
  [ -n "$root" ] || return 1
  for c in "$root/cmdline-tools/latest/bin/sdkmanager" "$root"/cmdline-tools/*/bin/sdkmanager "$root/tools/bin/sdkmanager"; do
    [ -x "$c" ] && { printf '%s' "$c"; return 0; }
  done
  return 1
}
sdk_root_usable() { [ -n "$1" ] && [ -d "$1" ] && { [ -x "$1/platform-tools/adb" ] || find_sdkmanager "$1" >/dev/null; }; }
# Precedence: what the user already exports → Android Studio's default → the Homebrew cask root.
resolve_android_home() {
  local c
  for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$BREW_SDK_ROOT"; do
    if sdk_root_usable "$c"; then export ANDROID_HOME=$c ANDROID_SDK_ROOT=$c; return 0; fi
  done
  return 1
}
sdk_root_check()   { sdk_root_usable "${ANDROID_HOME:-}" || return 1; printf 'ANDROID_HOME=%s' "$ANDROID_HOME"; }
sdk_root_install() { brew install --cask android-commandlinetools && export ANDROID_HOME=$BREW_SDK_ROOT ANDROID_SDK_ROOT=$BREW_SDK_ROOT; }
sdk_root_verify()  {
  find_sdkmanager "${ANDROID_HOME:-}" >/dev/null || { printf 'sdkmanager not found under %s — brew install --cask android-commandlinetools' "${ANDROID_HOME:-<unset>}"; return 1; }
  printf 'ANDROID_HOME=%s' "$ANDROID_HOME"
}
resolve_android_home || true
run_step "Android SDK root (cmdline-tools)" sdk_root_check sdk_root_install sdk_root_verify

# ── platform-tools (adb) + emulator ────────────────────────────────────────────────────────────
sdk_pkgs_check() {
  local v
  [ -x "${ANDROID_HOME:-}/platform-tools/adb" ] && [ -x "$ANDROID_HOME/emulator/emulator" ] || return 1
  v=$("$ANDROID_HOME/platform-tools/adb" version 2>/dev/null) || v='adb'
  printf 'platform-tools + emulator · %s' "$(first_line "$v")"
}
sdk_pkgs_install() {
  local sm
  sm=$(find_sdkmanager "${ANDROID_HOME:-}") || { printf 'no sdkmanager — the "Android SDK root" step must succeed first\n' >&2; return 1; }
  # sdkmanager needs a JDK; --sdk_root is mandatory for the cask layout. Licenses are accepted
  # non-interactively (they are Google's SDK terms — the same prompt Android Studio shows).
  yes 2>/dev/null | JAVA_HOME=${JAVA_HOME:-} "$sm" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
  JAVA_HOME=${JAVA_HOME:-} "$sm" --sdk_root="$ANDROID_HOME" "platform-tools" "emulator"
}
sdk_pkgs_verify() { sdk_pkgs_check || { printf 'adb / emulator still missing — sdkmanager --sdk_root="%s" platform-tools emulator' "${ANDROID_HOME:-}"; return 1; }; }
run_step "Android platform-tools (adb) + emulator" sdk_pkgs_check sdk_pkgs_install sdk_pkgs_verify

# Make the tools reachable for the rest of THIS run (the profile block covers new terminals).
if [ -n "${ANDROID_HOME:-}" ]; then
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi

# ── shell profile block (decision D3) ─────────────────────────────────────────────────────────
PROFILE_TITLE="Shell profile (ANDROID_HOME, JAVA_HOME, PATH)"
if [ -z "${ANDROID_HOME:-}" ] || [ -z "${JAVA_HOME:-}" ]; then
  log_warn "$PROFILE_TITLE" "skipped — fix the JDK / SDK steps above first, then re-run"
elif [ "$SUNGEN_ENV_INITIAL_ANDROID_HOME" = "$ANDROID_HOME" ] && [ "$SUNGEN_ENV_INITIAL_JAVA_HOME" = "$JAVA_HOME" ] \
  && [ -n "$SUNGEN_ENV_INITIAL_ADB" ] && grep -qs 'ANDROID_HOME' "$(profile_file)"; then
  # The user's own profile already exports the right values (and adb was on PATH before we touched it) —
  # nothing to add. A value exported only in this terminal session does not count: the profile must name it.
  log_skip "$PROFILE_TITLE" "already exported by your shell profile (ANDROID_HOME, JAVA_HOME, adb on PATH) — no block written"
else
  profile_step "$PROFILE_TITLE" "$(printf 'export ANDROID_HOME="%s"\nexport ANDROID_SDK_ROOT="$ANDROID_HOME"\nexport JAVA_HOME="%s"\nexport PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"' "$ANDROID_HOME" "$JAVA_HOME")"
fi

# ── Android Studio — optional, GUI, never installed here ───────────────────────────────────────
if [ -d "/Applications/Android Studio.app" ]; then
  log_skip "Android Studio (optional)" "/Applications/Android Studio.app"
else
  log_skip "Android Studio (optional)" "not installed — only needed for the AVD manager / an on-screen emulator: https://developer.android.com/studio"
fi
