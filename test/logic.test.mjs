// Mirrors the Android unit tests (SpeechMatchTest, SrsTest, ContentTest) for the web port.
// Run: node web/test/logic.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as L from '../js/logic.js';
import { buildIndex, lemmas, lookup } from '../js/dict.js';

const json = (f) => JSON.parse(readFileSync(new URL(`../data/${f}.json`, import.meta.url), 'utf8'));
const content = json('content');
let n = 0;
const test = (name, fn) => { fn(); n++; console.log('✓', name); };

test('perfect sentence scores 100 with contractions expanded', () => {
  const r = L.compare("I'm fine, thanks!", 'i am fine thanks');
  assert.equal(r.score, 100);
  assert.ok(r.words.every(([, ok]) => ok));
});

test('missing words are marked red and lower the score', () => {
  const r = L.compare('Could you show me the meeting room?', 'could you show the room');
  assert.deepEqual(r.words.map(([, ok]) => ok), [true, true, true, false, true, false, true]);
  assert.equal(r.score, 71);
  assert.equal(r.stars, 2);
});

test('empty speech scores 0', () => assert.equal(L.compare('Hello there', '').score, 0));

test('judgeWord tells clear, confused and unclear apart', () => {
  assert.equal(L.judgeWord('think', 'sink', ['sink', 'think']).kind, 'clear');
  assert.deepEqual(L.judgeWord('think', 'sink', ['sink']), { kind: 'confused', heardAs: 'sink' });
  assert.equal(L.judgeWord('think', 'sink', ['pink']).kind, 'unclear');
});

test('SM-2 intervals grow 1 → 3 → longer, misses reset', () => {
  let c = { id: 'x', front: 'a', back: 'b' };
  c = L.schedule(c, 'GOOD', 100); assert.equal(c.due, 101);
  c = L.schedule(c, 'GOOD', 101); assert.equal(c.interval, 3);
  c = L.schedule(c, 'GOOD', 104); assert.ok(c.interval > 3);
  c = L.schedule(c, 'AGAIN', 110); assert.equal(c.due, 110); assert.equal(c.reps, 0);
  assert.equal(L.preview({ id: 'y' }, 'EASY'), '4 ngày');
});

test('streak: freeze covers one missed day, otherwise it breaks', () => {
  let p = L.addXp(L.defaultProgress(), 10, 200);
  assert.equal(p.streak, 1);
  const frozen = L.rollOver({ ...p, freezes: 1 }, 202);
  assert.equal(frozen.streak, 1); assert.equal(frozen.freezes, 0); assert.equal(frozen.freezeUsedDay, 202);
  assert.equal(L.rollOver(p, 202).streak, 0);
  assert.equal(L.addXp(p, 25, 200).freezes, 1); // goal of 30 reached
});

test('every topic has the fields the screens need', () => {
  for (const t of content.topics) {
    assert.ok(t.phrases.length && t.words.length && t.dialogue.lines.length, t.id);
    assert.ok(t.dialogue.lines.some(L.isUserLine), t.id);
    for (const n of t.native || []) assert.ok(n.plain && n.natural && n.vi, t.id);
  }
});

test('game sessions have the right length and solvable rounds', () => {
  for (const m of L.GAME_MODES) {
    const rounds = L.buildGame(content.topics, m.id);
    assert.equal(rounds.length, m.id === 'match' ? 4 : m.id === 'mix' ? 10 : 8);
    for (const r of rounds) {
      if (r.options) assert.ok(r.options.includes(r.answer));
      if (r.type === 'scramble') assert.equal([...r.tiles].sort().join(' '), r.sentence.split(' ').sort().join(' '));
    }
  }
});

test('AI request alternates roles and carries the schema', () => {
  const s = L.scenarios(content)[0];
  const body = L.requestBody(s, L.AI_LEVELS[1], [{ fromUser: true, text: L.START_CUE }, { fromUser: false, text: 'Hi', raw: '{}' }]);
  assert.deepEqual(body.contents.map((c) => c.role), ['user', 'model']);
  assert.equal(body.generationConfig.responseSchema.required.length, 4);
  assert.equal(L.scenarios(content).length, content.topics.length + 2);
});

test('lemmas find base forms of inflected words', () => {
  const has = (w, base) => assert.ok(lemmas(w).includes(base), `${w} -> ${base}: ${lemmas(w)}`);
  has('stopped', 'stop'); has('making', 'make'); has('running', 'run'); has('studies', 'study');
  has('cities', 'city'); has('went', 'go'); has('children', 'child'); has('happier', 'happy');
  has('biggest', 'big'); has('quickly', 'quick'); has('Wong’s', 'wong'); has('boxes', 'box'); has('lives', 'life');
});

test('every vocabulary word has what the screens show, and lookup finds inflected forms', () => {
  const vocab = json('vocab');
  const words = vocab.groups.flatMap((g) => g.words);
  for (const w of words) assert.ok(w.w && w.ipa && w.vi && w.ex && w.pos, JSON.stringify(w));
  assert.equal(new Set(words.map((w) => w.w.toLowerCase())).size, words.length);
  buildIndex(vocab, content);
  assert.equal(lookup('Children')?.w, 'child');
  assert.equal(lookup('went')?.w, 'go');
});

test('categories cover every topic', () => {
  const ids = new Set(content.categories.map((c) => c.id));
  for (const t of content.topics) assert.ok(ids.has(t.category), t.id);
  assert.ok(content.topics.length >= 46);
});

console.log(`\n${n} tests passed`);
