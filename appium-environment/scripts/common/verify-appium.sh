#!/usr/bin/env bash
# verify-appium.sh — the final gate: `appium driver doctor <driver>` checks the whole chain the
# driver needs at run time (JAVA_HOME, ANDROID_HOME, adb; Xcode + simctl for xcuitest).
# Doctor problems are reported as warnings with the doctor's own output — it names the missing piece.
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

APPIUM_BIN=$SUNGEN_ENV_APPIUM_BIN

appium_cli_check() { [ -x "$APPIUM_BIN" ] || return 1; printf 'appium %s' "$("$APPIUM_BIN" -v 2>/dev/null)"; }
run_check "Appium CLI" appium_cli_check "not installed in this project — sungen capability add mobile"

doctor_check() {
  local out rc req opt
  out=$("$APPIUM_BIN" driver doctor "$CURRENT_DRIVER" 2>&1); rc=$?
  # Doctor marks BOTH required and optional problems with "✖", so read its own summary line instead:
  #   "### Diagnostic completed, N required fixes needed, M optional fixes possible. ###"
  req=$(printf '%s\n' "$out" | sed -n -E 's/.*Diagnostic completed, ([0-9]+) required fix.*/\1/p' | tail -n1)
  opt=$(printf '%s\n' "$out" | sed -n -E 's/.*, ([0-9]+) optional fix.*/\1/p' | tail -n1)
  if [ -z "$req" ]; then
    # No summary line (older driver) — fall back to the exit code.
    [ "$rc" -eq 0 ] && { printf 'doctor exited 0'; return 0; }
    printf '%s' "$out"; return 1
  fi
  if [ "$req" -gt 0 ]; then
    printf '%s required fix(es) needed:\n%s\nfull report: npx appium driver doctor %s' "$req" \
      "$(printf '%s\n' "$out" | grep '✖' | sed -E 's/^.*Doctor +//')" "$CURRENT_DRIVER"
    return 1
  fi
  printf '0 required fixes · %s optional (extras like bundletool / gstreamer — see: npx appium driver doctor %s)' "${opt:-0}" "$CURRENT_DRIVER"
}

if [ -x "$APPIUM_BIN" ]; then
  DOCTOR_DRIVERS=""
  [ "$SUNGEN_ENV_SKIP_ANDROID" != 1 ] && DOCTOR_DRIVERS="uiautomator2"
  if [ "$(uname -s)" = Darwin ] && [ "$SUNGEN_ENV_SKIP_IOS" != 1 ]; then DOCTOR_DRIVERS="$DOCTOR_DRIVERS xcuitest"; fi
  for CURRENT_DRIVER in $DOCTOR_DRIVERS; do
    if printf '%s' "$("$APPIUM_BIN" driver list --installed --json 2>/dev/null)" | grep -q "\"$CURRENT_DRIVER\""; then
      run_check "appium driver doctor $CURRENT_DRIVER" doctor_check "fix the required items above (open a NEW terminal first if the profile block was just written), then re-run" warn
    else
      log_warn "appium driver doctor $CURRENT_DRIVER" "driver not installed — see the Appium step above"
    fi
  done
fi
