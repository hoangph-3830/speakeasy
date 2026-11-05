// Mirrors the Android unit tests (SpeechMatchTest, SrsTest, ContentTest) for the web port.
// Run: node web/test/logic.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as L from '../js/logic.js';
import { buildIndex, lemmas, lookup } from '../js/dict.js';
import * as A from '../js/audio.js';

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

test('FSRS: first answers use the initial stabilities, intervals grow, lapses drop stability', () => {
  const fresh = { id: 'x', front: 'a', back: 'b' };
  assert.equal(L.schedule(fresh, 'AGAIN', 100).due, 100);
  assert.equal(L.schedule(fresh, 'HARD', 100).interval, 1);
  assert.equal(L.schedule(fresh, 'GOOD', 100).interval, 4);
  assert.equal(L.schedule(fresh, 'EASY', 100).interval, 14);
  let c = L.schedule(fresh, 'GOOD', 100);
  const next = L.schedule(c, 'GOOD', c.due);
  assert.ok(next.interval > c.interval, `${next.interval} > ${c.interval}`);
  const lapse = L.schedule(next, 'AGAIN', next.due);
  assert.ok(lapse.stability < next.stability);
  assert.equal(lapse.lapses, 1); assert.equal(lapse.reps, 0); assert.equal(lapse.due, next.due);
  assert.equal(L.preview(fresh, 'EASY', 100), '14 ngày');
});

test('FSRS takes over cards saved by the old SM-2 scheduler', () => {
  const old = { id: 'w:x', front: 'a', back: 'b', interval: 10, ease: 2.5, due: 200, reps: 3 };
  const c = L.schedule(old, 'GOOD', 200);
  assert.ok(c.stability > 10 && c.interval > 10, JSON.stringify(c));
  assert.ok(c.difficulty >= 1 && c.difficulty <= 10);
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

test('shadowing: pitch of steady tones, rising sweep, intonation hints, speed', () => {
  const rate = 16000;
  const tone = (hz, secs) => Float32Array.from({ length: rate * secs }, (_, i) => 0.5 * Math.sin((2 * Math.PI * hz * i) / rate));
  for (const hz of [150, 220]) {
    const voiced = A.pitchCurve(tone(hz, 1), rate).filter((f) => f > 0);
    const m = voiced.sort((a, b) => a - b)[Math.floor(voiced.length / 2)];
    assert.ok(Math.abs(m - hz) < 5, `${hz} Hz detected as ${m}`);
  }
  // 120 Hz rising to 240 Hz over 2 s (phase accumulated so the sweep is smooth).
  let phase = 0;
  const sweep = Float32Array.from({ length: rate * 2 }, (_, i) => {
    phase += (2 * Math.PI * (120 + (120 * i) / (rate * 2))) / rate;
    return 0.5 * Math.sin(phase);
  });
  assert.equal(A.pitchTrend(A.pitchCurve(sweep, rate)), 'rising');
  assert.equal(A.pitchCurve(new Float32Array(rate), rate).every((f) => f === 0), true);
  assert.equal(A.intonationHint('Where is the meeting room?').want, 'falling');
  assert.equal(A.intonationHint('Is this seat taken?').want, 'rising');
  assert.equal(A.intonationHint('I just joined the team.').want, 'falling');
  assert.match(A.speedFeedback(6, 2), /chậm/);
  assert.match(A.speedFeedback(2, 2), /ổn/);
  const d = A.downsample(tone(200, 1), 48000 / 1);
  assert.equal(d.rate, 16000);
});

test('reading aloud splits into sentences, keeps abbreviations, caps long ones', () => {
  assert.deepEqual(L.speechChunks('Hello there. How are you? I am fine!'), ['Hello there.', 'How are you?', 'I am fine!']);
  assert.deepEqual(L.speechChunks('U.S. officials met Dr. Smith on Monday. Talks went well.'), ['U.S. officials met Dr. Smith on Monday.', 'Talks went well.']);
  assert.deepEqual(L.speechChunks('He said: “We won.” Then he left.'), ['He said: “We won.”', 'Then he left.']);
  const long = Array.from({ length: 30 }, (_, i) => `clause number ${i}`).join(', ') + '.';
  const chunks = L.speechChunks(long);
  assert.ok(chunks.length > 1 && chunks.every((c) => c.length <= 220), chunks.map((c) => c.length).join());
  assert.equal(chunks.join(' '), long);
});

test('learning path covers every topic once, easy to hard, and finds the next topic', () => {
  const ids = content.path.flatMap((s) => s.topics);
  assert.equal(ids.length, content.topics.length);
  assert.equal(new Set(ids).size, ids.length);
  const order = Object.keys(L.LEVELS);
  const levels = content.path.map((s) => order.indexOf(s.level));
  assert.deepEqual(levels, [...levels].sort((a, b) => a - b));
  for (const s of content.path) for (const id of s.topics) assert.equal(content.topics.find((t) => t.id === id).level, s.level, id);
  const p = L.defaultProgress();
  assert.equal(L.nextInPath(content.path, content.topics, p).topic.id, content.path[0].topics[0]);
  const first = content.path[0].topics[0];
  const half = { ...p, done: L.ACTIVITIES.map((a) => `${first}:${a.id}`) };
  assert.equal(L.nextInPath(content.path, content.topics, half).topic.id, content.path[0].topics[1]);
  const all = { ...p, done: content.topics.flatMap((t) => L.ACTIVITIES.map((a) => `${t.id}:${a.id}`)) };
  assert.equal(L.nextInPath(content.path, content.topics, all), null);
});

test('picture books are bilingual page by page, credited and sorted by level', () => {
  const { books } = json('picturebooks');
  assert.ok(books.length >= 40);
  const order = Object.keys(L.LEVELS);
  for (const b of books) {
    assert.ok(b.title && b.titleVi && b.license && (b.text || b.illustration), b.id);
    assert.ok(order.includes(b.level), b.id);
    for (const p of b.pages) assert.ok(p.en && p.vi && (!p.img || p.img.startsWith('https://')), b.id);
  }
  const ranks = books.map((b) => order.indexOf(b.level));
  assert.deepEqual(ranks, [...ranks].sort((a, b) => a - b));
});

console.log(`\n${n} tests passed`);
