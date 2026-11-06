#!/usr/bin/env bash
# verify-node.sh — Node.js ≥ 18 (sungen's own engine floor). Never installs Node: sungen is already
# running on it when this bundle is invoked from `sungen init`; a standalone run gets the guide.
[ -n "${SUNGEN_ENV_LIB_LOADED:-}" ] || . "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

NODE_MIN=18

node_check() {
  local v major
  have node || return 1
  v=$(node -v 2>/dev/null) || return 1
  major=$(version_major "$v")
  [ "$major" -ge "$NODE_MIN" ] || { printf 'node %s is below %s' "$v" "$NODE_MIN"; return 1; }
  printf 'node %s · npm %s' "$v" "$(npm -v 2>/dev/null || printf '?')"
}
run_check "Node.js ≥ $NODE_MIN" node_check "install Node LTS: https://nodejs.org  (or: brew install node · nvm install --lts)"
