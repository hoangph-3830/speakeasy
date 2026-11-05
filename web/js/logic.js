// Pure learning logic, ported 1:1 from the Android app (data/*.kt, speech/SpeechMatch.kt).
// No DOM or storage here, so it can be checked with `node web/test/logic.test.mjs`.

export const ACTIVITIES = [
  { id: 'PHRASES', label: 'Cụm từ & từ vựng', emoji: '📚', xp: 10 },
  { id: 'DIALOGUE', label: 'Hội thoại nhập vai', emoji: '💬', xp: 15 },
  { id: 'SPEAK', label: 'Luyện nói', emoji: '🎤', xp: 15 },
  { id: 'GAME', label: 'Chơi mà học', emoji: '🎮', xp: 10 },
];
export const activity = (id) => ACTIVITIES.find((a) => a.id === id);

/** Local calendar day as a day count, like LocalDate.toEpochDay(). */
export function epochDay(date = new Date()) {
  return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000);
}

export const shuffled = (list) => {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
};
export const pick = (list) => list[Math.floor(Math.random() * list.length)];

const distinctBy = (list, key) => {
  const seen = new Set();
  return list.filter((x) => (seen.has(key(x)) ? false : seen.add(key(x))));
};

// ---- Content -------------------------------------------------------------

/** Every English sentence in the topic with its translation, for speaking and listening drills. */
export const topicSentences = (t) =>
  distinctBy(
    [
      ...t.phrases.map((p) => [p.en, p.vi]),
      ...(t.native || []).map((n) => [n.natural, n.vi]),
      ...t.dialogue.lines.map((l) => [l.en, l.vi]),
    ],
    (s) => s[0],
  );

export const isUserLine = (line) => line.s === 'B';

/** CEFR levels the path climbs through, with a learner-friendly name and accent colour. */
export const LEVELS = {
  A1: { name: 'Mới bắt đầu', color: 'mint' },
  A2: { name: 'Sơ cấp', color: 'sky' },
  B1: { name: 'Trung cấp', color: 'lavender' },
  B2: { name: 'Khá', color: 'peach' },
};

/** The first topic along the learning path that still has an unfinished step, with its stage. */
export function nextInPath(path, topics, p) {
  for (const [i, stage] of path.entries())
    for (const id of stage.topics) {
      const topic = topics.find((t) => t.id === id);
      if (topic && doneCount(p, id) < ACTIVITIES.length) return { stage, stageNo: i + 1, topic };
    }
  return null;
}

/** Steps finished across a stage, out of 4 per topic. */
export const stageProgress = (stage, p) => {
  const done = stage.topics.reduce((sum, id) => sum + doneCount(p, id), 0);
  return { done, total: stage.topics.length * ACTIVITIES.length, topicsDone: stage.topics.filter((id) => doneCount(p, id) === ACTIVITIES.length).length };
};

/** Rotates through all topics, one per calendar day. */
export const topicOfTheDay = (topics, day = epochDay()) => topics[((day % topics.length) + topics.length) % topics.length];

// ---- Reading aloud -------------------------------------------------------------

const ABBREVIATIONS = /\b(?:Mr|Mrs|Ms|Dr|Prof|St|Jr|Sr|vs|etc|Inc|Ltd|Co|Corp|Gov|Sen|Rep|Gen|No|U\.S|U\.K|U\.N|E\.U|a\.m|p\.m|i\.e|e\.g)\.$/i;

/**
 * Sentence-sized pieces for text-to-speech: engines stumble or stop on long utterances, and
 * queued sentences flow without the restart pause a cancelled utterance has.
 */
export function speechChunks(text, max = 220) {
  const parts = [];
  let current = '';
  // Marks break points with \u0000 instead of a lookbehind, which older iPad Safari can't parse.
  for (const piece of String(text).replace(/([.!?…]["”’)]?)\s+/g, '$1\u0000').split('\u0000')) {
    current = current ? `${current} ${piece}` : piece;
    // "U.S. officials" must not end a sentence at "U.S."
    if (ABBREVIATIONS.test(current.split(/\s+/).pop())) continue;
    parts.push(current);
    current = '';
  }
  if (current) parts.push(current);
  // Very long sentences are split at commas, semicolons or dashes.
  return parts.flatMap((p) => {
    if (p.length <= max) return [p.trim()];
    const out = [];
    let acc = '';
    for (const bit of p.replace(/([,;:–—])\s+/g, '$1\u0000').split('\u0000')) {
      if (acc && (acc + ' ' + bit).length > max) { out.push(acc); acc = bit; } else acc = acc ? `${acc} ${bit}` : bit;
    }
    if (acc) out.push(acc);
    return out;
  }).filter(Boolean);
}

// ---- Speech matching -----------------------------------------------------

const CONTRACTIONS = {
  "i'm": 'i am', "you're": 'you are', "we're": 'we are', "they're": 'they are',
  "it's": 'it is', "that's": 'that is', "what's": 'what is', "here's": 'here is',
  "there's": 'there is', "let's": 'let us', "i'll": 'i will', "you'll": 'you will',
  "we'll": 'we will', "i'd": 'i would', "i've": 'i have', "we've": 'we have',
  "don't": 'do not', "doesn't": 'does not', "can't": 'can not', cannot: 'can not',
  "isn't": 'is not', "aren't": 'are not', "won't": 'will not', "she's": 'she is',
  "he's": 'he is', "who's": 'who is', "how's": 'how is', "where's": 'where is',
  "they've": 'they have',
};

const normalize = (word) =>
  word.toLowerCase().replace(/’/g, "'").replace(/^[^\p{L}\p{N}']+|[^\p{L}\p{N}']+$/gu, '').replace(/^'+|'+$/g, '');

/** Splits into comparable tokens, expanding contractions so "I'm" matches "I am". */
export function tokens(text) {
  return text
    .split(/\s+/)
    .map(normalize)
    .filter(Boolean)
    .flatMap((w) => (CONTRACTIONS[w] ?? w).replace(/-/g, ' ').split(' '))
    .filter(Boolean);
}

/** Marks which target tokens appear, in order, in what was heard (longest common subsequence). */
function lcsMatches(target, heard) {
  const dp = Array.from({ length: target.length + 1 }, () => new Array(heard.length + 1).fill(0));
  for (let a = target.length - 1; a >= 0; a--)
    for (let b = heard.length - 1; b >= 0; b--)
      dp[a][b] = target[a] === heard[b] ? dp[a + 1][b + 1] + 1 : Math.max(dp[a + 1][b], dp[a][b + 1]);
  const out = new Array(target.length).fill(false);
  let a = 0, b = 0;
  while (a < target.length && b < heard.length) {
    if (target[a] === heard[b]) { out[a] = true; a++; b++; }
    else if (dp[a + 1][b] >= dp[a][b + 1]) a++;
    else b++;
  }
  return out;
}

export function stars(score) {
  return score >= 90 ? 3 : score >= 70 ? 2 : score >= 40 ? 1 : 0;
}

export function feedback(score) {
  return [
    'Chưa nghe rõ, nghe mẫu rồi thử lại nhé 🙂',
    'Khá rồi, thử lại chậm hơn nhé 💪',
    'Tốt lắm! Gần như hoàn hảo 👍',
    'Xuất sắc! Phát âm rất chuẩn 🎉',
  ][stars(score)];
}

/** Compares what the learner said with the target: `words` is [displayWord, wasSaid][]. */
export function compare(target, spoken) {
  const display = target.split(/\s+/).filter((w) => w.trim());
  // Each displayed word expands to one or more tokens; it counts as said if all of them align.
  const groups = display.map(tokens);
  const flat = groups.flat();
  const matched = lcsMatches(flat, tokens(spoken));
  let i = 0;
  const words = display.map((w, idx) => {
    const n = groups[idx].length;
    let ok = n > 0;
    for (let k = i; k < i + n; k++) ok = ok && matched[k];
    i += n;
    return [w, ok];
  });
  const score = flat.length === 0 ? 0 : Math.floor((matched.filter(Boolean).length * 100) / flat.length);
  return { words, score, stars: stars(score), feedback: feedback(score) };
}

// ---- Pronunciation lab ---------------------------------------------------

const containsAll = (have, need) => need.every((t) => have.includes(t));

/**
 * The recognizer acts as a native listener: if any of its guesses is the target word,
 * a native ear would understand it too. Returns {kind: 'clear'|'confused'|'unclear', ...}.
 */
export function judgeWord(target, lookAlike, alternatives) {
  const t = tokens(target);
  const heard = alternatives.map(tokens);
  if (heard.some((h) => containsAll(h, t))) return { kind: 'clear' };
  if (lookAlike && heard[0] && containsAll(heard[0], tokens(lookAlike))) return { kind: 'confused', heardAs: lookAlike };
  return { kind: 'unclear', heard: alternatives[0] || '' };
}

/** Best match across the recognizer's guesses, so one odd top guess doesn't sink a good attempt. */
export function bestSentenceScore(target, alternatives) {
  const all = alternatives.map((a) => compare(target, a));
  return all.length ? all.reduce((x, y) => (y.score > x.score ? y : x)) : compare(target, '');
}

export const percent = (correct, total) => (total === 0 ? 0 : Math.floor((correct * 100) / total));

// ---- Progress: streaks and quests -----------------------------------------

export const MAX_FREEZES = 2;

export const defaultProgress = () => ({
  name: '',
  xp: 0,
  todayXp: 0,
  dailyGoal: 30,
  streak: 0,
  bestStreak: 0,
  lastActiveDay: -1,
  done: [], // "topicId:ACTIVITY" keys, plus "story:<id>"
  learnedWords: [],
  speechRate: 0.9,
  accent: 'US',
  voiceName: '',
  freezes: 0,
  freezeEarnedDay: -1,
  freezeUsedDay: -1,
  counters: {},
  countersDay: -1,
  claimed: [],
  soundScores: {},
});

export const isDone = (p, topicId, activityId) => p.done.includes(`${topicId}:${activityId}`);
export const doneCount = (p, topicId) => ACTIVITIES.filter((a) => isDone(p, topicId, a.id)).length;
export const level = (p) => Math.floor(p.xp / 100) + 1;
export const goalProgress = (p) => Math.min(1, Math.max(0, p.todayXp / p.dailyGoal));
export const speakingScore = (p) => {
  const v = Object.values(p.soundScores);
  return v.length ? Math.floor(v.reduce((a, b) => a + b, 0) / v.length) : null;
};

export const QUEST = { SPEAK: 'speak', LISTEN: 'listen', REVIEW: 'review', XP: 'xp' };
export const questCount = (p, key) => (key === QUEST.XP ? p.todayXp : p.counters[key] || 0);
export const questsToday = (p) => [
  { id: QUEST.SPEAK, title: 'Nói 10 câu tiếng Anh', emoji: '🎤', target: 10, reward: 10 },
  { id: QUEST.LISTEN, title: 'Hoàn thành 5 bài nghe', emoji: '🎧', target: 5, reward: 10 },
  { id: QUEST.REVIEW, title: 'Ôn 10 thẻ ghi nhớ', emoji: '🔁', target: 10, reward: 10 },
  { id: QUEST.XP, title: `Đạt mục tiêu ${p.dailyGoal} XP`, emoji: '🎯', target: p.dailyGoal, reward: 5 },
];

/**
 * Brings progress up to `day`: resets today's XP and quest counters, and either breaks the streak
 * or spends one freeze per missed day when there are enough of them.
 */
export function rollOver(p, day) {
  const fresh = p.countersDay === day ? p : { ...p, counters: {}, claimed: [], countersDay: day };
  if (p.lastActiveDay === day || p.lastActiveDay < 0) return fresh;
  const missed = day - p.lastActiveDay - 1;
  if (missed === 0) return { ...fresh, todayXp: 0 };
  if (p.streak > 0 && p.freezes >= missed)
    return { ...fresh, todayXp: 0, freezes: p.freezes - missed, lastActiveDay: day - 1, freezeUsedDay: day };
  return { ...fresh, todayXp: 0, streak: 0 };
}

export function addXp(p, amount, day) {
  if (amount <= 0) return p;
  const streak = p.lastActiveDay === day ? p.streak : p.streak + 1;
  const todayXp = p.todayXp + amount;
  // Meeting the daily goal earns a streak freeze, once per day.
  const earnsFreeze = todayXp >= p.dailyGoal && p.freezeEarnedDay !== day && p.freezes < MAX_FREEZES;
  return {
    ...p,
    xp: p.xp + amount,
    todayXp,
    streak,
    bestStreak: Math.max(p.bestStreak, streak),
    lastActiveDay: day,
    freezes: earnsFreeze ? p.freezes + 1 : p.freezes,
    freezeEarnedDay: earnsFreeze ? day : p.freezeEarnedDay,
  };
}

// ---- Spaced repetition (simplified SM-2) ----------------------------------

export const GRADES = [
  { id: 'AGAIN', label: 'Quên', emoji: '😵', color: 'var(--coral)' },
  { id: 'HARD', label: 'Khó', emoji: '😓', color: 'var(--peach)' },
  { id: 'GOOD', label: 'Nhớ', emoji: '🙂', color: 'var(--sky)' },
  { id: 'EASY', label: 'Dễ', emoji: '😎', color: 'var(--mint)' },
];

// FSRS-4.5 (Free Spaced Repetition Scheduler) with its default weights: each card keeps a
// stability (days until recall drops to 90%) and a difficulty (1-10), updated on every review.
const W = [0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031, 1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755];
const DECAY = -0.5;
const FACTOR = 19 / 81;
const GRADE_VALUE = { AGAIN: 1, HARD: 2, GOOD: 3, EASY: 4 };
const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));

export const retrievability = (elapsed, stability) => (1 + (FACTOR * elapsed) / stability) ** DECAY;
const initStability = (g) => W[g - 1];
const initDifficulty = (g) => clamp(W[4] - (g - 3) * W[5], 1, 10);
const nextDifficulty = (d, g) => clamp(W[7] * initDifficulty(3) + (1 - W[7]) * (d - W[6] * (g - 3)), 1, 10);
const recallStability = (d, s, r, g) =>
  s * (Math.exp(W[8]) * (11 - d) * s ** -W[9] * (Math.exp(W[10] * (1 - r)) - 1) * (g === 2 ? W[15] : 1) * (g === 4 ? W[16] : 1) + 1);
const forgetStability = (d, s, r) =>
  Math.min(s, W[11] * d ** -W[12] * ((s + 1) ** W[13] - 1) * Math.exp(W[14] * (1 - r)));
/** Days until recall probability falls to 90%. */
const intervalFor = (s) => clamp(Math.round((s / FACTOR) * (0.9 ** (1 / DECAY) - 1)), 1, 36500);

export function schedule(card, grade, today) {
  let c = { interval: 0, ease: 2.5, due: 0, reps: 0, stability: 0, difficulty: 0, lapses: 0, last: -1, ...card };
  // Cards scheduled by the old SM-2 code carry an interval and ease but no stability yet.
  if (c.reps > 0 && !c.stability)
    c = { ...c, stability: Math.max(c.interval, 1), difficulty: clamp(11 - c.ease * 2, 1, 10), last: today - c.interval };
  const g = GRADE_VALUE[grade];
  let s, d;
  if (!c.stability) {
    s = initStability(g);
    d = initDifficulty(g);
  } else {
    const r = retrievability(Math.max(0, today - c.last), c.stability);
    s = g === 1 ? forgetStability(c.difficulty, c.stability, r) : recallStability(c.difficulty, c.stability, r, g);
    d = nextDifficulty(c.difficulty, g);
  }
  // A forgotten card comes back later in this same session.
  const days = g === 1 ? 0 : intervalFor(s);
  return {
    ...c,
    stability: s,
    difficulty: d,
    last: today,
    due: today + days,
    interval: days,
    reps: g === 1 && c.reps > 0 ? 0 : c.reps + 1,
    lapses: g === 1 && c.reps > 0 ? c.lapses + 1 : c.lapses,
  };
}

/** Short label for the gap a grade would give, shown under each button. */
export function preview(card, grade, today = 0) {
  const days = schedule(card, grade, today).due - today;
  if (days === 0) return 'ngay';
  if (days >= 365) return `${Math.round((days / 365) * 10) / 10} năm`;
  if (days >= 60) return `${Math.round(days / 30)} tháng`;
  return `${days} ngày`;
}

/** Cards a finished lesson adds to the deck: its words, phrases and native expressions. */
export const cardsFor = (t) => [
  ...t.words.map((w) => ({ id: `w:${w.word}`, front: w.word, back: w.vi, extra: `${w.ipa} · ${w.example}` })),
  ...t.phrases.map((p) => ({ id: `p:${p.en}`, front: p.en, back: p.vi, extra: p.tip || null })),
  ...(t.native || []).map((n) => ({ id: `n:${n.natural}`, front: n.natural, back: n.vi, extra: n.note || null })),
];

// ---- Games ---------------------------------------------------------------

export const GAME_MODES = [
  { id: 'mix', title: 'Thử thách tổng hợp', emoji: '🌈', description: 'Mỗi vòng một kiểu chơi khác nhau', color: 'sun' },
  { id: 'match', title: 'Ghép cặp từ', emoji: '🧩', description: 'Nối từ tiếng Anh với nghĩa tiếng Việt', color: 'sky' },
  { id: 'scramble', title: 'Xếp câu', emoji: '🧱', description: 'Sắp xếp các từ thành câu hoàn chỉnh', color: 'peach' },
  { id: 'listen', title: 'Nghe & chọn', emoji: '🎧', description: 'Nghe câu nói rồi chọn đáp án đúng', color: 'mint' },
  { id: 'dictation', title: 'Nghe & chép', emoji: '✍️', description: 'Nghe người bản xứ nói rồi gõ lại từng từ', color: 'coral' },
  { id: 'meaning', title: 'Đoán nghĩa', emoji: '💡', description: 'Chọn nghĩa đúng của mẫu câu', color: 'lavender' },
];
export const gameMode = (id) => GAME_MODES.find((m) => m.id === id) || GAME_MODES[0];

/** Builds a session from the given topics; one topic for a lesson, all topics for free play. */
export function buildGame(topics, mode, size = 8) {
  const words = distinctBy(topics.flatMap((t) => t.words), (w) => w.word);
  const sentences = distinctBy(topics.flatMap(topicSentences), (s) => s[0]);
  let short = sentences.filter((s) => { const n = s[0].split(' ').length; return n >= 3 && n <= 9; });
  if (!short.length) short = sentences;

  const match = () => ({ type: 'match', pairs: shuffled(words).slice(0, 5).map((w) => [w.word, w.vi]) });
  const scramble = () => {
    const [en, vi] = pick(short);
    const tiles = en.split(' ');
    let mixed = shuffled(tiles);
    // Avoid handing out an already-solved puzzle.
    for (let i = 0; i < 5 && mixed.join(' ') === en; i++) mixed = shuffled(tiles);
    return { type: 'scramble', sentence: en, hint: vi, tiles: mixed };
  };
  const listen = () => {
    const useWords = Math.random() < 0.5 && words.length >= 4;
    const pool = useWords ? words.map((w) => w.word) : short.map((s) => s[0]);
    const answer = pick(pool);
    const options = shuffled([...shuffled(pool.filter((x) => x !== answer)).slice(0, 3), answer]);
    return { type: 'listen', audio: answer, options, answer };
  };
  const meaning = () => {
    const reverse = Math.random() < 0.5;
    const [en, vi] = pick(short);
    const others = shuffled(short.filter((s) => s[0] !== en)).slice(0, 3);
    return reverse
      ? { type: 'meaning', prompt: vi, options: shuffled([...others.map((o) => o[0]), en]), answer: en, speakPrompt: false }
      : { type: 'meaning', prompt: en, options: shuffled([...others.map((o) => o[1]), vi]), answer: vi, speakPrompt: true };
  };
  const dictation = () => { const [en, vi] = pick(short); return { type: 'dictation', sentence: en, hint: vi }; };

  const makers = { match: [match], scramble: [scramble], listen: [listen], meaning: [meaning], dictation: [dictation] }[mode]
    || [match, scramble, listen, dictation, meaning];
  const count = mode === 'match' ? 4 : mode === 'mix' ? size + 2 : size;
  return Array.from({ length: count }, (_, i) => makers[i % makers.length]());
}

// ---- AI tutor prompt ------------------------------------------------------

export const AI_LEVELS = [
  { id: 'EASY', label: 'Dễ', prompt: 'Beginner (A2): simple everyday words, short sentences, speak slowly and clearly.' },
  { id: 'MEDIUM', label: 'Vừa', prompt: 'Intermediate (B1): normal everyday English with common phrasal verbs.' },
  { id: 'NATIVE', label: 'Như bản xứ', prompt: 'Advanced: talk exactly like a native speaker, with idioms, slang and fillers (you know, kind of, I mean), still kind.' },
];

/** Free talk plus one role-play per lesson topic, so chat practice follows what was just learned. */
export function scenarios(content) {
  return [
    {
      id: 'free', title: 'Trò chuyện tự do', emoji: '☕',
      setting: 'A relaxed chat between new friends at a café. Talk about anything the learner brings up: work, hobbies, travel, food, plans.',
      aiRole: 'Alex, a friendly 28-year-old from Canada', learnerRole: 'themselves',
    },
    {
      id: 'smalltalk', title: 'Small talk với đồng nghiệp', emoji: '💬',
      setting: 'Small talk with a colleague in the office kitchen on a Monday morning.',
      aiRole: 'Sam, a cheerful colleague from London', learnerRole: 'a colleague',
    },
    ...content.topics.map((t) => ({
      id: `topic:${t.id}`, title: t.titleVi, emoji: t.emoji,
      setting: `${t.title}. Use situations and phrases like these: ${t.phrases.map((p) => p.en).join(' / ')}`,
      aiRole: t.dialogue.roleA, learnerRole: `the person talking to ${t.dialogue.roleA}`,
    })),
  ];
}

/** Cue that makes the AI open the conversation, since the first message must come from the user. */
export const START_CUE = '[The learner has just joined. Open the conversation with your first line.]';

export const systemPrompt = (s, lvl) => `You are a friendly native English speaker helping a Vietnamese learner practise spoken English for work, daily life, travel and socialising.
Situation: ${s.setting}
You play: ${s.aiRole}. The learner plays: ${s.learnerRole}.

How to talk (the "reply" field):
- Stay in character and keep the conversation moving like a real one. Usually end with a question or a natural prompt so the learner has something to answer.
- 1 to 3 short sentences, the way people actually speak (contractions, everyday phrasing). No lists, no emojis: the reply is read aloud by text-to-speech.
- Level: ${lvl.prompt}
- Never teach inside "reply"; corrections only go in "correction".

Correcting the learner (the "correction" field), for the learner's latest message:
- Their messages come from speech recognition, so ignore capitalisation, punctuation and obvious transcription slips.
- If it was natural, correct English for this situation, set has_issue to false and leave the other fields empty.
- Otherwise (grammar, wrong word, translated too literally from Vietnamese, unnatural or too blunt), set has_issue to true, put what a native speaker would say in "corrected", and explain the key point in one short Vietnamese sentence in "explanation_vi".
- If they wrote Vietnamese or got stuck, give the English they could say in "corrected", explain briefly, and carry on the role-play.
- A message in square brackets is a cue from the app, not the learner: never correct it.

"reply_vi": a natural Vietnamese translation of your reply.
"suggestions": 2 short, natural things the learner could say next (under 12 words each), different in meaning.`;

const T = (type) => ({ type });
/** Gemini's OpenAPI-style response schema for a tutor reply. */
export const REPLY_SCHEMA = {
  type: 'OBJECT',
  properties: {
    reply: T('STRING'),
    reply_vi: T('STRING'),
    correction: {
      type: 'OBJECT',
      properties: { has_issue: T('BOOLEAN'), corrected: T('STRING'), explanation_vi: T('STRING') },
      required: ['has_issue', 'corrected', 'explanation_vi'],
    },
    suggestions: { type: 'ARRAY', items: T('STRING') },
  },
  required: ['reply', 'reply_vi', 'correction', 'suggestions'],
};

/** Gemini wants alternating user/model turns; the AI's turns are replayed as the JSON it produced. */
export const requestBody = (scenario, lvl, history) => ({
  systemInstruction: { parts: [{ text: systemPrompt(scenario, lvl) }] },
  contents: history.map((t) => ({ role: t.fromUser ? 'user' : 'model', parts: [{ text: t.raw ?? t.text }] })),
  generationConfig: { responseMimeType: 'application/json', responseSchema: REPLY_SCHEMA, temperature: 0.8 },
});
