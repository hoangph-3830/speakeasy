// Mirrors the Android unit tests (SpeechMatchTest, SrsTest, ContentTest) for the web port.
// Run: node web/test/logic.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as L from '../js/logic.js';
import { buildIndex, lemmas, lookup } from '../js/dict.js';
import * as A from '../js/audio.js';
import * as C from '../js/challenges.js';
import * as P from '../js/learning-plan.js';
import * as O from '../js/online-voice.js';
import * as D from '../js/daily-word.js';

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
  // Each audience's path runs easy to hard on its own; the kids' stages sit outside the adults' one.
  const kids = content.path.filter((s) => s.audience === 'kids');
  for (const part of [kids, L.visiblePath(content.path, 'adult')]) {
    const levels = part.map((s) => order.indexOf(s.level));
    assert.deepEqual(levels, [...levels].sort((a, b) => a - b));
  }
  assert.ok(kids.length >= 2 && kids.every((s) => s.topics.every((id) => content.topics.find((t) => t.id === id).category === 'kids')));
  assert.equal(L.visiblePath(content.path, 'kids')[0].audience, 'kids');
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

test('video channels list real-looking YouTube ids with levels', () => {
  const { channels } = json('videos');
  assert.ok(channels.length >= 5);
  for (const c of channels) {
    assert.ok(Object.keys(L.LEVELS).includes(c.level) && c.name && c.about, c.handle);
    assert.ok(c.videos.length >= 10, c.handle);
    for (const v of c.videos) assert.match(v.id, /^[A-Za-z0-9_-]{11}$/);
  }
});

test('every topic builds every game mode with four options per choice round', () => {
  for (const t of content.topics)
    for (const m of L.GAME_MODES)
      for (const r of L.buildGame([t], m.id))
        if (r.options) {
          assert.equal(r.options.length, 4, `${t.id}/${m.id}`);
          assert.equal(r.options.filter((o) => o === r.answer).length, 1, `${t.id}/${m.id}`);
        }
});

test('challenges: options hold the answer once, memory pairs, hangman rules, scoring, records', () => {
  const { groups } = json('vocab');
  for (let i = 0; i < 50; i++) {
    const r = C.speedRound(groups);
    assert.equal(r.options.length, 4);
    assert.equal(r.options.filter((o) => o === r.answer).length, 1);
    const tf = C.trueFalseRound(groups);
    assert.equal(tf.shown === tf.word.vi, tf.correct);
  }
  const deck = C.memoryDeck(groups);
  assert.equal(deck.length, 12);
  assert.equal(new Set(deck.map((c) => c.text)).size, 12);
  const a = deck.find((c) => c.side === 'en');
  assert.ok(C.isPair(a, deck.find((c) => c.pair === a.pair && c.side === 'vi')));
  assert.ok(!C.isPair(a, a));
  const words = C.hangmanWords(groups);
  assert.equal(words.length, C.HANGMAN_ROUNDS);
  for (const w of words) assert.match(w.w, /^[a-z]{4,8}$/);
  assert.equal(C.revealMask('hello', new Set(['l'])), '_ _ l l _');
  assert.ok(C.solved('hello', new Set(['h', 'e', 'l', 'o'])));
  assert.equal(C.livesLeft('hello', new Set(['h', 'x', 'z'])), C.HANGMAN_LIVES - 2);
  assert.deepEqual([1, 4, 5, 9].map(C.speedPoints), [1, 1, 2, 2]);
  assert.ok(C.isRecord(undefined, 3) && C.isRecord(5, 6) && !C.isRecord(5, 5));
  assert.ok(C.isRecord(12, 10, true) && !C.isRecord(10, 12, true));
});

test('new challenges: fill the gap, letter scramble and spelling', () => {
  const groups = json('vocab').groups;
  for (let i = 0; i < 200; i++) {
    const r = C.clozeRound(groups);
    assert.ok(r.sentence.includes('_____'), r.sentence);
    assert.equal(new Set(r.options).size, 4);
    assert.equal(r.options.filter((o) => o === r.answer).length, 1);
  }
  assert.equal(C.blankOut('He ate an apple. An apple a day.', 'apple'), 'He ate an _____. An apple a day.');
  assert.equal(C.blankOut('Pineapple and apple', 'apple'), 'Pineapple and _____');
  assert.equal(C.blankOut('Read it.', 'read'), '_____ it.');
  const words = C.anagramWords(groups);
  assert.equal(words.length, C.ANAGRAM_ROUNDS);
  for (const w of words) {
    const letters = C.scrambleLetters(w.w);
    assert.notEqual(letters.join(''), w.w);
    assert.equal([...letters].sort().join(''), [...w.w].sort().join(''));
  }
  assert.equal(C.spellingWords(groups).length, C.SPELLING_ROUNDS);
  assert.ok(C.spelledRight('  Apple ', 'apple') && !C.spelledRight('aple', 'apple') && !C.spelledRight('', 'apple'));
});

test('placement test: three questions per level, early stop and the level passed', () => {
  const quiz = P.placementQuiz(content.topics);
  assert.equal(quiz.length, P.LEVEL_ORDER.length * P.PLACEMENT_PER_LEVEL);
  for (const q of quiz) {
    assert.equal(new Set(q.options).size, 4, q.prompt);
    assert.equal(q.options.filter((o) => o === q.answer).length, 1);
  }
  const ans = (lv, ...oks) => oks.map((ok) => ({ level: lv, ok }));
  assert.equal(P.placementResult([]), '');
  assert.equal(P.placementResult(ans('A1', true, false, false)), '');
  assert.equal(P.placementResult([...ans('A1', true, true, false), ...ans('A2', true, true, true), ...ans('B1', false, false)]), 'A2');
  // A pass above a failed level does not count.
  assert.equal(P.placementResult([...ans('A1', false, false), ...ans('A2', true, true, true)]), '');
  assert.ok(P.levelFailed(ans('B1', false, false), 'B1') && !P.levelFailed(ans('B1', true, false), 'B1'));
  assert.equal(P.studyLevel(''), 'A1');
  assert.equal(P.studyLevel('B1'), 'B2');
  assert.equal(P.studyLevel('C2'), 'C2');
});

test('placement moves "continue learning" past easier stages, then back to unfinished ones', () => {
  const path = content.path;
  const base = L.defaultProgress();
  const next = (p) => L.nextInPath(path, content.topics, p).stage.level;
  assert.equal(next(base), 'A1');
  assert.equal(next({ ...base, placedLevel: '' }), 'A1');
  assert.equal(next({ ...base, placedLevel: 'B1' }), 'B2');
  assert.equal(next({ ...base, placedLevel: 'C2' }), 'A1');
  // Every placed stage finished: the learner is sent back to what is left below.
  const above = path.filter((st) => !L.skippedStage(st, 'B1')).flatMap((st) => st.topics);
  const done = above.flatMap((id) => L.ACTIVITIES.map((a) => id + ':' + a.id));
  assert.equal(next({ ...base, placedLevel: 'B1', done }), 'A1');
});

test('study calendar keeps XP per day for half a year and compares weeks', () => {
  let p = L.defaultProgress();
  p = L.addXp(p, 10, 1000);
  p = L.addXp(p, 5, 1000);
  p = L.addXp(p, 7, 1001);
  assert.deepEqual(p.history, { 1000: 15, 1001: 7 });
  assert.deepEqual(Object.keys(L.recordDay(p.history, 1000 + L.HISTORY_DAYS + 1, 3)), ['1001', String(1001 + L.HISTORY_DAYS)]);
  const days = P.calendarDays(p.history, 1001, 2);
  assert.equal(days.length, 14);
  assert.deepEqual(days.slice(-2), [{ day: 1000, xp: 15 }, { day: 1001, xp: 7 }]);
  assert.deepEqual(P.weekTotals({ 1001: 7, 994: 4, 980: 100 }, 1001), { thisWeek: 7, lastWeek: 4 });
  assert.deepEqual([0, 5, 20, 30, 59, 60].map((x) => P.heat(x, 30)), [0, 1, 2, 3, 3, 4]);
});

test('daily reminder: evening warning and a repeating calendar event', () => {
  assert.ok(P.streakAtRisk({ streak: 3, todayXp: 0 }, 19));
  assert.ok(!P.streakAtRisk({ streak: 3, todayXp: 5 }, 19));
  assert.ok(!P.streakAtRisk({ streak: 3, todayXp: 0 }, 12));
  assert.ok(!P.streakAtRisk({ streak: 0, todayXp: 0 }, 21));
  const ics = P.reminderIcs('07:05', 'https://example.org/', new Date(2026, 10, 6, 9, 0));
  assert.ok(ics.includes('DTSTART:20261106T070500\r\n'));
  assert.ok(ics.includes('RRULE:FREQ=DAILY'));
  assert.match(ics, /BEGIN:VALARM[\s\S]*END:VALARM/);
  assert.ok(ics.startsWith('BEGIN:VCALENDAR\r\n') && ics.endsWith('END:VCALENDAR\r\n'));
});

test('online voice: pieces under the length limit, request address, which devices default to it', () => {
  const long = 'word '.repeat(100).trim();
  const pieces = O.onlineChunks(['Hello there.', long]);
  assert.equal(pieces[0], 'Hello there.');
  assert.ok(pieces.slice(1).every((p) => p.length <= 180 && !p.startsWith(' ')));
  assert.equal(pieces.slice(1).join(' '), long);
  const url = new URL(O.onlineUrl('Hi & bye?', 'UK', 0.1));
  assert.equal(url.searchParams.get('q'), 'Hi & bye?');
  assert.equal(url.searchParams.get('tl'), 'en-GB');
  assert.equal(url.searchParams.get('ttsspeed'), '0.30');
  assert.equal(new URL(O.onlineUrl('Hi', 'US', 1.3)).searchParams.get('ttsspeed'), '1.00');
  const ipad27 = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Safari/605.1.15';
  const iphone27 = 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Mobile/15E148 Safari/604.1';
  const iphone17 = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1';
  assert.ok(O.speechEngineMayBeSilent(ipad27, 5));
  assert.ok(!O.speechEngineMayBeSilent(ipad27, 0)); // a real Mac has no touch screen
  assert.ok(O.speechEngineMayBeSilent(iphone27, 5));
  assert.ok(O.speechEngineMayBeSilent(iphone17, 5)); // the user agent no longer tells the real iOS version
  assert.ok(!O.speechEngineMayBeSilent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/152.0 Safari/537.36', 0));
});

test('write mode: every topic gives a Vietnamese prompt with an English answer that passes itself', () => {
  for (const t of content.topics)
    for (const r of L.buildGame([t], 'write')) {
      assert.equal(r.type, 'write');
      assert.ok(r.prompt && r.answer, t.id);
      assert.equal(L.compare(r.answer, r.answer.toLowerCase()).score, 100);
    }
  assert.ok(L.buildGame(content.topics, 'mix', 12).some((r) => r.type === 'write'));
  assert.ok(L.compare('I would like a coffee, please.', 'I would like coffee please').score >= L.WRITE_PASS);
  assert.ok(L.compare('I would like a coffee, please.', 'I like tea').score < L.WRITE_PASS);
});

test('kids path comes first for children and stays hidden from adults', () => {
  const path = [{ id: 'a', level: 'A1' }, { id: 'k', level: 'A1', audience: 'kids' }, { id: 'b', level: 'A2' }];
  assert.deepEqual(L.visiblePath(path, 'adult').map((s) => s.id), ['a', 'b']);
  assert.deepEqual(L.visiblePath(path, 'kids').map((s) => s.id), ['k', 'a', 'b']);
  assert.deepEqual(L.visiblePath(path, undefined).map((s) => s.id), ['a', 'b']);
});

test('word of the day: same word for a day, letter colours with repeats, streaks and sharing', () => {
  const words = D.puzzleWords(json('vocab').groups);
  assert.ok(words.length > 100 && words.every((w) => /^[a-z]{5}$/.test(w.w)));
  assert.equal(new Set(words.map((w) => w.w)).size, words.length);
  assert.equal(D.wordOfDay(words, 20400).w, D.wordOfDay(words, 20400).w);
  const week = new Set(Array.from({ length: 7 }, (_, i) => D.wordOfDay(words, 20400 + i).w));
  assert.equal(week.size, 7);
  assert.deepEqual(D.scoreGuess('crane', 'crane'), ['hit', 'hit', 'hit', 'hit', 'hit']);
  assert.deepEqual(D.scoreGuess('eerie', 'there'), ['near', 'miss', 'near', 'miss', 'hit']);
  assert.deepEqual(D.scoreGuess('lllll', 'hello'), ['miss', 'miss', 'hit', 'hit', 'miss']);
  assert.deepEqual(D.scoreGuess('apple', 'paper'), ['near', 'near', 'hit', 'miss', 'near']);
  assert.ok(D.validGuess('hello') && !D.validGuess('hell') && !D.validGuess('Hello') && !D.validGuess('he1lo'));
  assert.ok(D.isOver(['aaaaa', 'bbbbb', 'ccccc', 'ddddd', 'eeeee', 'fffff'], 'hello') && !D.isWon(['aaaaa'], 'hello'));
  assert.deepEqual(D.keyColours(['hxllx', 'hello'], 'hello'), { h: 'hit', x: 'miss', l: 'hit', e: 'hit', o: 'hit' });
  const start = { played: 0, won: 0, streak: 0, best: 0, lastWonDay: -1, dist: [0, 0, 0, 0, 0, 0] };
  let s = D.recordResult(start, 100, true, 3);
  s = D.recordResult(s, 101, true, 4);
  assert.deepEqual([s.played, s.won, s.streak, s.best, s.dist[2], s.dist[3]], [2, 2, 2, 2, 1, 1]);
  assert.equal(D.recordResult(s, 103, true, 2).streak, 1); // skipped a day
  assert.deepEqual([D.recordResult(s, 102, false, 6).streak, D.recordResult(s, 102, false, 6).best], [0, 2]);
  assert.equal(D.shareText(['crate', 'crane'], 'crane', 7), 'SpeakEasy · Từ của ngày #7 2/6\n🟩🟩🟩⬜🟩\n🟩🟩🟩🟩🟩');
});

console.log(`\n${n} tests passed`);
