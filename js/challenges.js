// Pure rules for the vocabulary challenges (speed quiz, memory, hangman, true/false), kept free of
// the DOM so `node web/test/logic.test.mjs` checks them. Words come from the 3000-word list.
import { pick, shuffled } from './logic.js';

export const CHALLENGES = [
  { id: 'speed', emoji: '⚡', title: 'Đua 60 giây', about: 'Chọn nghĩa đúng nhanh nhất có thể, chuỗi 5 câu đúng được x2 điểm' },
  { id: 'memory', emoji: '🃏', title: 'Lật thẻ trí nhớ', about: 'Tìm cặp từ tiếng Anh và nghĩa tiếng Việt, càng ít lượt càng giỏi', lowerIsBetter: true },
  { id: 'hangman', emoji: '🔤', title: 'Đoán chữ', about: 'Đoán từng chữ cái theo nghĩa và phiên âm, có 6 mạng' },
  { id: 'truefalse', emoji: '✅', title: 'Đúng hay Sai', about: 'Nghĩa này đúng không? Trả lời thật nhanh trong 45 giây' },
];
export const challenge = (id) => CHALLENGES.find((c) => c.id === id);

export const SPEED_SECONDS = 60;
export const SPEED_PENALTY = 3;
export const TRUEFALSE_SECONDS = 45;
export const HANGMAN_ROUNDS = 5;
export const HANGMAN_LIVES = 6;

const words = (groups) => groups.flatMap((g) => g.words.map((w) => ({ ...w, group: g.id })));

/** A word plus three other meanings, preferably from the same theme so the choice is not obvious. */
export function speedRound(groups) {
  const all = words(groups);
  const word = pick(all);
  const pool = all.filter((w) => w.group === word.group && w.vi !== word.vi);
  const others = new Set();
  while (others.size < 3) others.add(pick(pool.length >= 3 ? pool : all.filter((w) => w.vi !== word.vi)).vi);
  return { word, options: shuffled([...others, word.vi]), answer: word.vi };
}

/** Points for a correct answer given the streak including it: double from the fifth in a row. */
export const speedPoints = (streak) => (streak >= 5 ? 2 : 1);

/** Twelve face-down cards: six words and their six meanings. */
export function memoryDeck(groups, pairs = 6) {
  const chosen = [];
  for (const w of shuffled(words(groups))) {
    if (chosen.length === pairs) break;
    // Two cards with the same meaning text would make a pair ambiguous.
    if (!chosen.some((c) => c.vi === w.vi || c.w === w.w)) chosen.push(w);
  }
  return shuffled(chosen.flatMap((w, i) => [
    { pair: i, side: 'en', text: w.w, word: w },
    { pair: i, side: 'vi', text: w.vi, word: w },
  ]));
}

export const isPair = (a, b) => a.pair === b.pair && a.side !== b.side;

/** Words a-z only, 4-8 letters, so every letter can be guessed on the keyboard. */
export function hangmanWords(groups, n = HANGMAN_ROUNDS) {
  return shuffled(words(groups).filter((w) => /^[a-z]{4,8}$/.test(w.w))).slice(0, n);
}

/** "e _ _ e _" style display of the guessed letters. */
export const revealMask = (word, guessed) => [...word].map((ch) => (guessed.has(ch) ? ch : '_')).join(' ');
export const solved = (word, guessed) => [...word].every((ch) => guessed.has(ch));
export const livesLeft = (word, guessed) => HANGMAN_LIVES - [...guessed].filter((ch) => !word.includes(ch)).length;

/** A word with a meaning that is its own half of the time. */
export function trueFalseRound(groups) {
  const all = words(groups);
  const word = pick(all);
  const correct = Math.random() < 0.5;
  if (correct) return { word, shown: word.vi, correct };
  const sameGroup = all.filter((w) => w.group === word.group && w.vi !== word.vi);
  return { word, shown: pick(sameGroup.length ? sameGroup : all.filter((w) => w.vi !== word.vi)).vi, correct };
}

/** Whether `score` beats `best` (none yet counts as beaten); memory is won in fewer moves. */
export const isRecord = (best, score, lowerIsBetter = false) =>
  best == null || (lowerIsBetter ? score < best : score > best);
