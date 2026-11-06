#!/usr/bin/env bash
# lib.sh — shared step runner for the sungen appium-environment bundle (macOS / Linux side).
# Sourced by setup.sh and by every scripts/**/*.sh; never run directly.
# Bash 3.2 compatible (the /bin/bash that ships with macOS): no associative arrays, no ${var,,}.
#
# Step model (see docs: Mobile → Appium setup → "One command"):
#   check   → exit 0 when already satisfied (side-effect free — this is all `--check` runs)
#   install → idempotent; output streams to the terminal; exit 2 = started but needs a user action
#   verify  → exit 0 when satisfied; stdout is the detail (ok) or the hint (fail)

[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] && return 0
SUNGEN_ENV_LIB_LOADED=1

# Flags — set by setup.sh's argument parser, read by every step.
SUNGEN_ENV_CHECK_ONLY=${SUNGEN_ENV_CHECK_ONLY:-0}
SUNGEN_ENV_SKIP_ANDROID=${SUNGEN_ENV_SKIP_ANDROID:-0}
SUNGEN_ENV_SKIP_IOS=${SUNGEN_ENV_SKIP_IOS:-0}
SUNGEN_ENV_NO_PROFILE=${SUNGEN_ENV_NO_PROFILE:-0}
# Flipped to 1 by write_profile_block when the shell profile actually changed.
SUNGEN_ENV_PROFILE_CHANGED=0

# Paths: <project>/appium-environment/scripts/common/lib.sh → bundle = ../.., project = bundle/..
SUNGEN_ENV_BUNDLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
PROJECT_DIR=${PROJECT_DIR:-$(cd "$SUNGEN_ENV_BUNDLE_DIR/.." && pwd)}
# Appium is project-local by design (never `npm i -g appium`) — see install-appium.sh.
SUNGEN_ENV_APPIUM_BIN="$PROJECT_DIR/node_modules/.bin/appium"

# Environment as it was BEFORE this run — lets the profile step recognise a machine whose own
# shell profile already exports everything (then no block is written).
SUNGEN_ENV_INITIAL_ANDROID_HOME=${ANDROID_HOME:-}
SUNGEN_ENV_INITIAL_JAVA_HOME=${JAVA_HOME:-}
SUNGEN_ENV_INITIAL_ADB=$(command -v adb 2>/dev/null || true)

COUNT_OK=0; COUNT_SKIP=0; COUNT_WARN=0; COUNT_FAIL=0

if [ -t 1 ]; then
  C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_FAIL=$'\033[31m'; C_DIM=$'\033[2m'; C_BOLD=$'\033[1m'; C_RESET=$'\033[0m'
else
  C_OK=''; C_WARN=''; C_FAIL=''; C_DIM=''; C_BOLD=''; C_RESET=''
fi

have() { command -v "$1" >/dev/null 2>&1; }
# version_major "v22.17.1" → 22
version_major() { printf '%s' "$1" | sed -E 's/^[^0-9]*([0-9]+).*/\1/'; }
# java_major_of <java binary> → 21 (empty when not runnable). "1.8.0_x" → 1, so JDK 8 fails a ≥17 gate.
java_major_of() { "$1" -version 2>&1 | head -n1 | sed -E 's/^[^"]*"([0-9]+)(\.[0-9]+)*.*$/\1/' | grep -E '^[0-9]+$'; }
# first_line <multi-line string>
first_line() { printf '%s' "${1%%$'\n'*}"; }

section() { printf '\n%s%s%s\n' "$C_BOLD" "$1" "$C_RESET"; }
_detail() { [ -n "$1" ] && printf '            %s%s%s\n' "$C_DIM" "$1" "$C_RESET"; return 0; }
_detail_lines() { printf '%s\n' "$1" | while IFS= read -r line; do [ -n "$line" ] && _detail "$line"; done; return 0; }

log_ok()      { COUNT_OK=$((COUNT_OK+1));     printf '%s✓ ok%s       %s\n' "$C_OK" "$C_RESET" "$1"; _detail_lines "${2:-}"; }
log_skip()    { COUNT_SKIP=$((COUNT_SKIP+1)); printf '%s– skip%s     %s\n' "$C_DIM" "$C_RESET" "$1"; _detail_lines "${2:-}"; }
log_warn()    { COUNT_WARN=$((COUNT_WARN+1)); printf '%s⚠ warn%s     %s\n' "$C_WARN" "$C_RESET" "$1"; _detail_lines "${2:-}"; }
log_fail()    { COUNT_FAIL=$((COUNT_FAIL+1)); printf '%s✗ fail%s     %s\n' "$C_FAIL" "$C_RESET" "$1"; _detail_lines "${2:-}"; }
log_install() { printf '📦 install  %s\n' "$1"; }

# run_step <title> <check_fn> <install_fn> <verify_fn> — an install-type step.
run_step() {
  local title=$1 check_fn=$2 install_fn=$3 verify_fn=$4 found out rc
  if found=$("$check_fn" 2>/dev/null); then log_skip "$title" "${found:-already installed}"; return 0; fi
  if [ "$SUNGEN_ENV_CHECK_ONLY" = 1 ]; then log_fail "$title" "not installed — run without --check to install"; return 0; fi
  log_install "$title"
  "$install_fn"; rc=$?
  if out=$("$verify_fn" 2>&1); then log_ok "$title" "$out"; return 0; fi
  if [ "$rc" -eq 2 ]; then log_warn "$title" "${out:-needs a manual step — re-run setup.sh afterwards}"
  else log_fail "$title" "${out:-install did not complete}"; fi
  return 0
}

# run_check <title> <check_fn> <hint> [warn] — a verification-only step (ok / fail, or warn for informational checks).
run_check() {
  local title=$1 check_fn=$2 hint=$3 level=${4:-fail} out msg
  if out=$("$check_fn" 2>&1); then log_ok "$title" "$out"; return 0; fi
  msg=$hint; [ -n "$out" ] && msg="$out"$'\n'"$hint"
  if [ "$level" = warn ]; then log_warn "$title" "$msg"; else log_fail "$title" "$msg"; fi
  return 0
}

# run_guided <title> <check_fn> <guidance> — GUI / App Store installs this bundle never performs itself.
run_guided() {
  local title=$1 check_fn=$2 guidance=$3 found
  if found=$("$check_fn" 2>/dev/null); then log_skip "$title" "${found:-already installed}"; return 0; fi
  log_warn "$title" "$guidance"
  return 0
}

# ── shell profile block ────────────────────────────────────────────────────────────────────────
PROFILE_BEGIN='# >>> sungen mobile env >>>'
PROFILE_END='# <<< sungen mobile env <<<'
# zsh → ~/.zshrc, bash → ~/.bash_profile, anything else → ~/.profile
profile_file() {
  case "${SHELL:-}" in
    *zsh)  printf '%s' "$HOME/.zshrc" ;;
    *bash) printf '%s' "$HOME/.bash_profile" ;;
    *)     printf '%s' "$HOME/.profile" ;;
  esac
}
profile_block_content() {
  local file; file=$(profile_file); [ -f "$file" ] || return 0
  awk -v b="$PROFILE_BEGIN" -v e="$PROFILE_END" '$0==b{f=1;next} $0==e{f=0} f' "$file"
}
# write_profile_block <content> — append the marked block, replace it when different, keep it when identical.
# Returns 0 = unchanged, 3 = written. Sets PROFILE_MSG. Must NOT be called in a subshell (it sets globals).
write_profile_block() {
  local content=$1 file tmp
  file=$(profile_file); [ -f "$file" ] || : > "$file"
  if [ "$(profile_block_content)" = "$content" ]; then PROFILE_MSG="block already up to date in $file"; return 0; fi
  tmp=$(mktemp)
  awk -v b="$PROFILE_BEGIN" -v e="$PROFILE_END" '$0==b{f=1;next} $0==e{f=0;next} !f' "$file" > "$tmp"
  [ -s "$tmp" ] && [ -n "$(tail -c1 "$tmp")" ] && printf '\n' >> "$tmp"
  { cat "$tmp"; printf '%s\n%s\n%s\n' "$PROFILE_BEGIN" "$content" "$PROFILE_END"; } > "$file"
  rm -f "$tmp"
  SUNGEN_ENV_PROFILE_CHANGED=1
  PROFILE_MSG="written to $file — open a new terminal to apply"
  return 3
}
# profile_step <title> <content> — the profile block as a step (honours --no-profile and --check).
profile_step() {
  local title=$1 content=$2
  if [ "$SUNGEN_ENV_NO_PROFILE" = 1 ]; then log_skip "$title" "--no-profile"; return 0; fi
  if [ "$SUNGEN_ENV_CHECK_ONLY" = 1 ]; then
    if [ "$(profile_block_content)" = "$content" ]; then log_skip "$title" "block already up to date in $(profile_file)"
    else log_fail "$title" "block missing or outdated in $(profile_file) — run without --check to write it"; fi
    return 0
  fi
  write_profile_block "$content"
  case $? in 3) log_ok "$title" "$PROFILE_MSG" ;; *) log_skip "$title" "$PROFILE_MSG" ;; esac
  return 0
}

# print_summary — tallies + the "new terminal" notice. Exit 1 when anything failed.
print_summary() {
  printf '\n%s%d ok · %d skipped · %d warned · %d failed%s\n' "$C_BOLD" "$COUNT_OK" "$COUNT_SKIP" "$COUNT_WARN" "$COUNT_FAIL" "$C_RESET"
  [ "$SUNGEN_ENV_PROFILE_CHANGED" = 1 ] && printf '%s→ Open a NEW terminal so PATH / JAVA_HOME / ANDROID_HOME apply.%s\n' "$C_WARN" "$C_RESET"
  [ "$COUNT_WARN" -gt 0 ] && printf '  warnings need a manual step — do it, then re-run this script.\n'
  [ "$COUNT_FAIL" -gt 0 ] && return 1
  return 0
}
