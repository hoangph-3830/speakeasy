import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import yaml from 'yaml';

export class TestDataLoader {
  private data: Record<string, any>;
  // Data-driven (@cases): when set (via withRow), get() prefers this row's columns.
  private row?: Record<string, any>;

  private constructor(data: Record<string, any>) {
    this.data = data;
  }

  /**
   * Load test data for a screen/feature or flow/feature combination.
   *
   * Priority (later wins):
   * 1. {feature}.yaml — base data
   * 2. {feature}.{SUNGEN_ENV}.yaml — environment-specific (if SUNGEN_ENV set)
   *
   * Paths: screenName starting with "flows/" loads from qa/flows/, otherwise qa/screens/
   */
  static load(screenName: string, featureName: string): TestDataLoader {
    let baseDir: string;
    if (screenName.startsWith('flows/') || screenName.startsWith('api/')) {
      // flows/<flow> · api/<area> · api/flows/<flow> → qa/<screenName>/test-data
      baseDir = path.join(process.cwd(), 'qa', screenName, 'test-data');
    } else {
      baseDir = path.join(process.cwd(), 'qa', 'screens', screenName, 'test-data');
    }
    const env = process.env.SUNGEN_ENV;

    let data = loadYamlSync(path.join(baseDir, `${featureName}.yaml`)) || {};

    if (env) {
      const envData = loadYamlSync(path.join(baseDir, `${featureName}.${env}.yaml`));
      if (envData) data = deepMerge(data, envData);
    }

    data = resolveDynamicVars(data);
    loadEnvQa();
    data = resolveEnvRefs(data);

    return new TestDataLoader(data);
  }

  get(key: string): string {
    const value = this.resolve(key);
    if (value === undefined || value === null) {
      throw new Error(`Test data key not found: ${key}`);
    }
    // A projected array (e.g. `{{q.rows[*].name}}`) and a PLAIN object (an API response
    // body) serialize as JSON text — String([...]) flattens to a lossy comma-join and
    // String({}) is "[object Object]", both un-assertable. Embedded `{{ref}}`s interpolate
    // INSIDE the value before stringify, so a ref carrying quotes/backslashes can never
    // corrupt the serialized JSON (the old order substituted raw text into JSON text).
    // Only PLAIN objects take this path: Date/Buffer/ObjectId values bound by @query from
    // a real driver keep their String()/toString() output — JSON.stringify would wrap a
    // Date in quotes and flip every assertion that already compares its string form.
    const result = isJsonContainer(value)
      ? JSON.stringify(this.interpolateDeep(value))
      : this.interpolate(String(value));
    assertNoUnresolvedEnvRef(key, result);
    return result;
  }

  /**
   * Interpolate `{{ref}}`s in every string leaf AND key of a JSON container,
   * recursively (matching the old whole-JSON-text pass). Non-plain nested
   * objects are passed through untouched — JSON.stringify serializes them via
   * toJSON. A truly cyclic object throws a RangeError; every current bind
   * source (JSON.parse output, DB rows, YAML after load-time resolution) is
   * acyclic. Built with Object.fromEntries, which DEFINES data properties — a
   * body field literally named "__proto__" survives instead of silently
   * rewiring the result's prototype to external input.
   */
  private interpolateDeep(value: any): any {
    if (typeof value === 'string') return this.interpolate(value);
    if (Array.isArray(value)) return value.map((v) => this.interpolateDeep(v));
    if (isPlainObject(value)) {
      return Object.fromEntries(
        Object.entries(value).map(([k, v]) => [this.interpolate(k), this.interpolateDeep(v)])
      );
    }
    return value;
  }

  /**
   * Resolve a key to its RAW, uncoerced value (array/object/number kept as-is) — for binding
   * DB query / API request params, where type-strict sinks (MySQL numeric columns, MongoDB,
   * a JSON request body) require the native type. get() stringifies for Gherkin text; raw()
   * must not. A string value still has its embedded `{{ref}}` cross-references resolved (same
   * as get()), so an override like `token={{login.body.token}}` or `id={{prefix}}-001` still
   * interpolates — only the final scalar/array/object type is preserved uncoerced. Arrays and
   * objects are returned as-is; refs embedded inside their elements are not interpolated (unlike
   * get(), which interpolates every string leaf of a plain container before serializing), since
   * raw() callers bind structured values directly, not text.
   * Throws on missing, mirroring get()'s guard.
   */
  raw(key: string): any {
    const value = this.resolve(key);
    if (value === undefined || value === null) {
      throw new Error(`Test data key not found: ${key}`);
    }
    if (typeof value === 'string') {
      const result = this.interpolate(value);
      assertNoUnresolvedEnvRef(key, result);
      return result;
    }
    return value;
  }

  /**
   * Substitute embedded `{{ref}}` cross-references inside a resolved string value.
   * Each ref is looked up via resolve() (flat key, @cases row column, or dotted path),
   * so `expected_operator_id_label: 'ID : {{operator.id}}'` yields the real value at
   * runtime — matching the delivery report's substituteTestDataVars. `$`-prefixed
   * dynamic vars ({{$timestamp}} …) are already substituted at load time and never
   * match `[\w.]+`, so they are left untouched. Resolution recurses (a referenced value
   * may itself hold refs) up to a depth cap that guards against reference cycles; an
   * unknown or empty ref is left as the literal `{{ref}}` — never blanked.
   */
  private interpolate(value: string, depth: number = 0): string {
    if (depth > 10 || value.indexOf('{{') === -1) return value;
    return value.replace(/\{\{\s*([\w.]+)\s*\}\}/g, (match, ref: string) => {
      const resolved = this.resolve(ref);
      if (resolved === undefined || resolved === null || resolved === '') return match;
      return this.interpolate(String(resolved), depth + 1);
    });
  }

  /**
   * Resolve a `{{...}}` reference to its raw value. Supports:
   *  - flat keys (incl. captured runtime vars stored under a literal dotted key);
   *  - `@cases` row columns (the current row wins);
   *  - structured paths over nested data AND `@query`-bound result arrays:
   *      `q.count` / `q.length`  → number of rows
   *      `q.first.col` / `q.last.col` / `q[2].col` → a specific row's column
   *      `q.col`                → shorthand for the first row's column
   *      `q.rows[*].col`        → the `col` of EVERY row, projected to an ordered array
   */
  private resolve(key: string): any {
    // 1. Exact flat key — captured vars (set()) live under a literal, possibly dotted, key.
    if (this.row && key in this.row && this.row[key] !== undefined && this.row[key] !== null) {
      return this.row[key];
    }
    if (this.data[key] !== undefined && this.data[key] !== null) {
      return this.data[key];
    }
    // 2. Structured path: head from the row (cases) or shared data, then walk segments.
    //    `[n]` → `.n` (numeric index); `[*]` → `.*` (project the rest of the path over each element).
    const tokens = String(key).replace(/\[(\d+)\]/g, '.$1').replace(/\[\*\]/g, '.*').split('.');
    const cur: any = (this.row && tokens[0] in this.row) ? this.row[tokens[0]] : this.data[tokens[0]];
    return TestDataLoader.walk(cur, tokens, 1);
  }

  /**
   * Walk the remaining path tokens from `i`. A `*` token on an array projects: the rest of the
   * path is mapped over every element and the collected array returned (recurses for nested `[*]`).
   * Non-`*` tokens use the linear single-step navigation, so numeric/first/last/object access is
   * unchanged.
   */
  private static walk(cur: any, tokens: string[], i: number): any {
    for (; i < tokens.length && cur != null; i++) {
      if (tokens[i] === '*' && Array.isArray(cur)) {
        const rest = tokens.slice(i + 1);
        return cur.map((el) => TestDataLoader.walk(el, rest, 0));
      }
      cur = TestDataLoader.step(cur, tokens[i]);
    }
    return cur;
  }

  /** One navigation step over an array (with count/first/last/index/field-shorthand) or object. */
  private static step(cur: any, token: string): any {
    if (Array.isArray(cur)) {
      if (token === 'count' || token === 'length') return cur.length;
      if (token === 'first') return cur[0];
      if (token === 'last') return cur[cur.length - 1];
      if (/^\d+$/.test(token)) return cur[Number(token)];
      return cur[0] == null ? undefined : cur[0][token]; // shorthand: first row's field
    }
    if (cur && typeof cur === 'object') return cur[token];
    return undefined;
  }

  /**
   * Bind a raw value (e.g. an `@query` result array) under `key` so `{{key.…}}` paths resolve.
   * Unlike set(), the value is stored as-is (array/object), not coerced to a string.
   */
  bind(key: string, value: any): void {
    this.data[key] = value;
  }

  /**
   * Set a value at runtime (used by captured-variable steps:
   * `User remember [X] text as {{var}}`). Stored at top level so a later
   * `{{var}}` reference resolves via get().
   */
  set(key: string, value: string): void {
    this.data[key] = value;
  }

  /**
   * Data-driven (@cases): return the list of rows at `key` (after env-overlay merge),
   * each stamped with `__label` for the test title / report. Throws if missing or not a list.
   */
  cases(key: string): Array<Record<string, any>> {
    const list = this.data[key];
    if (!Array.isArray(list)) {
      throw new Error(`@cases dataset "${key}" not found or not a list in test-data (got ${typeof list}).`);
    }
    return list.map((row: any, i: number) => {
      const r: Record<string, any> = (row && typeof row === 'object' && !Array.isArray(row)) ? { ...row } : { value: row };
      r.__label = String(r.case ?? r.name ?? r.label ?? `row ${i + 1}`);
      return r;
    });
  }

  /**
   * Data-driven (@cases): a view whose get() prefers the given row's columns and falls
   * back to the shared data. Used inside the per-row test() loop.
   */
  withRow(row: Record<string, any>): TestDataLoader {
    const view = new TestDataLoader({ ...this.data }); // clone → per-row set() stays isolated
    view.row = row;
    return view;
  }
}

/**
 * A value get() must serialize as JSON: an array, or a PLAIN object (literal or
 * null-prototype). Class instances at the TOP level (Date, Buffer, ObjectId…,
 * and driver row wrappers like mysql2's RowDataPacket) are NOT containers —
 * they keep the String()/toString() path their existing assertions rely on;
 * nested inside a plain container they serialize through JSON.stringify/toJSON.
 */
function isJsonContainer(value: any): boolean {
  return Array.isArray(value) || isPlainObject(value);
}

// Prototype check only — a `value.constructor === Object` probe is defeated by
// a body FIELD literally named "constructor" (JSON.parse gives it as an own
// property), which would silently fall back to "[object Object]" again.
function isPlainObject(value: any): boolean {
  if (value === null || typeof value !== 'object') return false;
  const proto = Object.getPrototypeOf(value);
  return proto === Object.prototype || proto === null;
}

function loadYamlSync(filePath: string): Record<string, any> | null {
  if (!fs.existsSync(filePath)) return null;
  const content = fs.readFileSync(filePath, 'utf-8');
  return yaml.parse(content) || null;
}

function deepMerge(base: Record<string, any>, override: Record<string, any>): Record<string, any> {
  const result = { ...base };
  for (const [key, value] of Object.entries(override)) {
    if (value && typeof value === 'object' && !Array.isArray(value) &&
        result[key] && typeof result[key] === 'object' && !Array.isArray(result[key])) {
      result[key] = deepMerge(result[key], value);
    } else {
      result[key] = value;
    }
  }
  return result;
}

/**
 * SECRET test data: only `${QA_*}` (uppercase, QA_ prefix) is an env reference — resolved
 * from .env.qa / process.env so real credentials never live in the committed yaml. The
 * prefix doubles as the resolution ALLOWLIST: any other `${...}` text is ordinary literal
 * data (XSS payloads, template strings, passwords containing `${`) — never touched, and a
 * yaml can never exfiltrate arbitrary environment variables like ${AWS_SECRET_ACCESS_KEY}.
 */
const QA_ENV_REF = /\$\{(QA_[A-Z0-9_]+)\}/g;

/**
 * Load .env.qa files into process.env. Precedence (first set wins, real env highest):
 * shell/CI env > .env.qa.<SUNGEN_ENV> > .env.qa — the env-specific file OVERLAYS the base,
 * mirroring how <feature>.<env>.yaml overlays <feature>.yaml.
 */
function loadEnvQa(): void {
  const env = process.env.SUNGEN_ENV;
  for (const name of [env ? `.env.qa.${env}` : '', '.env.qa']) {
    if (!name) continue;
    const p = path.join(process.cwd(), name);
    if (!fs.existsSync(p)) continue;
    for (const line of fs.readFileSync(p, 'utf-8').split('\n')) {
      const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
      if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
    }
  }
}

/** Substitute `${QA_*}` refs in every string value. Unset vars stay literal — the guard in
 *  get()/raw() throws with the var name the moment such a key is actually used. */
function resolveEnvRefs(data: Record<string, any>): Record<string, any> {
  function resolveValue(value: any): any {
    if (typeof value === 'string') {
      return value.replace(QA_ENV_REF, (match, name) =>
        process.env[name] !== undefined ? process.env[name]! : match);
    }
    if (Array.isArray(value)) return value.map(resolveValue);
    if (value && typeof value === 'object') {
      const resolved: Record<string, any> = {};
      for (const [k, v] of Object.entries(value)) resolved[k] = resolveValue(v);
      return resolved;
    }
    return value;
  }
  return resolveValue(data);
}

/** Fail fast — and only for the test that actually uses the key — when a `${QA_*}` secret
 *  was referenced but never provided. */
function assertNoUnresolvedEnvRef(key: string, value: string): void {
  const m = value.match(/\$\{(QA_[A-Z0-9_]+)\}/);
  if (m) {
    throw new Error(
      `Test data "${key}" references \${${m[1]}} but the environment variable is not set — ` +
      `add ${m[1]}=... to .env.qa (gitignored; see .env.qa.example), or export it in the shell/CI.`);
  }
}

function resolveDynamicVars(data: Record<string, any>): Record<string, any> {
  const ts = String(Date.now());
  const uid = crypto.randomUUID();
  const now = new Date();
  // `{{$date}}` is the runner's LOCAL calendar date — the "today" the browser sees when
  // playwright.config sets no `timezoneId`. toISOString() is UTC, so east of Greenwich it named
  // yesterday until the offset passed (00:00–06:59 at UTC+7). Node reads the `TZ` env var, so a
  // config that pins `timezoneId` pins this too by exporting the same zone as `TZ`.
  // `{{$datetime}}` stays an ISO-8601 UTC instant: it names a moment, not a calendar day.
  const pad = (n: number): string => String(n).padStart(2, '0');
  // `{{$date:+1d}}` / `{{$date:-7d}}` shift by whole days; built from the local Y/M/D, so it stays
  // the right calendar day across a DST change.
  const localDate = (offsetDays: number): string => {
    const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offsetDays);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  };
  const date = localDate(0);
  const datetime = now.toISOString();

  function resolveValue(value: any): any {
    if (typeof value === 'string') {
      return value.replace(/\{\{\$(\w+)(?::([^}]*))?\}\}/g, (match, name, args) => {
        switch (name) {
          case 'timestamp':
            return ts;
          case 'uuid':
            return uid;
          case 'random': {
            const [min, max] = (args || '1:9999').split(':').map(Number);
            return String(Math.floor(Math.random() * (max - min + 1)) + min);
          }
          case 'date': {
            // An argument this does not understand stays literal, like an unknown variable — it used
            // to be ignored, so `{{$date:+1d}}` silently produced today. Up to 5 digits (~270 years):
            // a typo like +99999999999d would build an Invalid Date.
            if (!args) return date;
            const off = /^([+-]\d{1,5})d$/.exec(args.trim());
            return off ? localDate(Number(off[1])) : match;
          }
          case 'datetime':
            return args ? match : datetime;
          default:
            return match;
        }
      });
    }
    if (Array.isArray(value)) {
      return value.map(resolveValue);
    }
    if (value && typeof value === 'object') {
      const resolved: Record<string, any> = {};
      for (const [k, v] of Object.entries(value)) {
        resolved[k] = resolveValue(v);
      }
      return resolved;
    }
    return value;
  }

  return resolveValue(data);
}
