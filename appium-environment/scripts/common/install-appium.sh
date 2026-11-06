#!/usr/bin/env bash
# install-appium.sh — Appium stays PROJECT-LOCAL (a devDependency pulled in by @sungen/driver-mobile),
# never `npm i -g appium`; the platform drivers (uiautomator2 / xcuitest) live in ~/.appium and are
# installed through that local CLI. Mirrors what `sungen init --mobile` does in TypeScript, so this
# script also works on a fresh clone where a teammate ran init.
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

APPIUM_BIN=$SUNGEN_ENV_APPIUM_BIN

appium_check() {
  [ -x "$APPIUM_BIN" ] || return 1
  printf 'appium %s (project-local: node_modules/.bin/appium)' "$("$APPIUM_BIN" -v 2>/dev/null)"
}
appium_install() {
  # Only re-install when the project already declares the mobile driver — otherwise the fix is
  # `sungen capability add mobile`, which is a consent-gated sungen command, not this script's call.
  if grep -q '"@sungen/driver-mobile"' "$PROJECT_DIR/package.json" 2>/dev/null; then
    (cd "$PROJECT_DIR" && npm install)
  else
    return 2
  fi
}
appium_verify() {
  appium_check || { printf 'Appium is project-local (never global). Add the mobile driver + runtime with:\n  sungen capability add mobile'; return 1; }
}
run_step "Appium (project-local)" appium_check appium_install appium_verify

# ── platform drivers — one run_step per driver, CURRENT_DRIVER carries the name (bash 3.2: no closures)
installed_drivers_json() { "$APPIUM_BIN" driver list --installed --json 2>/dev/null; }
drv_check() {
  local json ver
  json=$(installed_drivers_json) || return 1
  printf '%s' "$json" | grep -q "\"$CURRENT_DRIVER\"" || return 1
  ver=$(printf '%s' "$json" | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{try{const j=JSON.parse(s);process.stdout.write((j['$CURRENT_DRIVER']||{}).version||'')}catch{}})" 2>/dev/null)
  printf '%s %s in ~/.appium' "$CURRENT_DRIVER" "${ver:-}"
}
drv_install() { "$APPIUM_BIN" driver install "$CURRENT_DRIVER"; }
drv_verify()  { drv_check || { printf 'retry: npx appium driver install %s' "$CURRENT_DRIVER"; return 1; }; }

if [ -x "$APPIUM_BIN" ]; then
  APPIUM_DRIVERS="uiautomator2"
  [ "$SUNGEN_ENV_SKIP_ANDROID" = 1 ] && APPIUM_DRIVERS=""
  # xcuitest builds WebDriverAgent with Xcode at run time → only meaningful on a Mac.
  if [ "$(uname -s)" = Darwin ] && [ "$SUNGEN_ENV_SKIP_IOS" != 1 ]; then APPIUM_DRIVERS="$APPIUM_DRIVERS xcuitest"; fi
  for CURRENT_DRIVER in $APPIUM_DRIVERS; do
    run_step "Appium driver: $CURRENT_DRIVER" drv_check drv_install drv_verify
  done
else
  log_warn "Appium platform drivers (uiautomator2 / xcuitest)" "skipped — Appium is not installed in this project yet (see the step above)"
fi
