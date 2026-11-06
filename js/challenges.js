// Pure rules for the vocabulary challenges (speed quiz, memory, hangman, true/false, fill the gap,
// letter scramble, spelling), kept free of
// the DOM so `node web/test/logic.test.mjs` checks them. Words come from the 3000-word list.
import { pick, shuffled } from './logic.js';

export const CHALLENGES = [
  { id: 'speed', emoji: '⚡', title: 'Đua 60 giây', about: 'Chọn nghĩa đúng nhanh nhất có thể, chuỗi 5 câu đúng được x2 điểm' },
  { id: 'memory', emoji: '🃏', title: 'Lật thẻ trí nhớ', about: 'Tìm cặp từ tiếng Anh và nghĩa tiếng Việt, càng ít lượt càng giỏi', lowerIsBetter: true },
  { id: 'hangman', emoji: '🔤', title: 'Đoán chữ', about: 'Đoán từng chữ cái theo nghĩa và phiên âm, có 6 mạng' },
  { id: 'truefalse', emoji: '✅', title: 'Đúng hay Sai', about: 'Nghĩa này đúng không? Trả lời thật nhanh trong 45 giây' },
  { id: 'cloze', emoji: '🧩', title: 'Điền từ vào câu', about: 'Chọn từ còn thiếu trong câu ví dụ, 10 câu' },
  { id: 'anagram', emoji: '🔀', title: 'Xếp chữ cái', about: 'Sắp lại các chữ cái bị xáo trộn thành từ đúng nghĩa' },
  { id: 'spelling', emoji: '👂', title: 'Nghe & viết từ', about: 'Nghe phát âm rồi gõ lại đúng chính tả, 10 từ' },
];
export const challenge = (id) => CHALLENGES.find((c) => c.id === id);

export const SPEED_SECONDS = 60;
export const SPEED_PENALTY = 3;
export const TRUEFALSE_SECONDS = 45;
export const HANGMAN_ROUNDS = 5;
export const HANGMAN_LIVES = 6;
export const CLOZE_ROUNDS = 10;
export const ANAGRAM_ROUNDS = 8;
export const SPELLING_ROUNDS = 10;

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

// ---- Fill the gap -------------------------------------------------------------

const BLANK = '_____';
const tokens = (text) => text.toLowerCase().split(/[^a-z']+/);
/** Words whose example sentence uses the word itself, so it can be blanked out. */
const clozeable = (w) => !!w.ex && /^[a-z]+$/i.test(w.w) && tokens(w.ex).includes(w.w.toLowerCase());

/** The example sentence with the word's first whole-word use replaced by a blank. */
export const blankOut = (sentence, word) =>
  sentence.replace(new RegExp(`(^|[^A-Za-z'])${word}(?![A-Za-z'])`, 'i'), (_, before) => before + BLANK);

/** A sentence with one word missing and four words to choose from, of the same part of speech if possible. */
export function clozeRound(groups) {
  const all = words(groups);
  const word = pick(all.filter(clozeable));
  const same = all.filter((w) => w.pos === word.pos && w.w !== word.w && /^[a-z]+$/i.test(w.w));
  const pool = same.length >= 3 ? same : all.filter((w) => w.w !== word.w && /^[a-z]+$/i.test(w.w));
  const others = new Set();
  while (others.size < 3) others.add(pick(pool).w);
  return { word, sentence: blankOut(word.ex, word.w), translation: word.exVi, options: shuffled([...others, word.w]), answer: word.w };
}

// ---- Letter scramble ----------------------------------------------------------

/** Words of 4-8 plain letters, so every tile is one keyboard letter. */
export const anagramWords = (groups, n = ANAGRAM_ROUNDS) =>
  shuffled(words(groups).filter((w) => /^[a-z]{4,8}$/.test(w.w) && new Set(w.w).size > 1)).slice(0, n);

/** The letters in a new order that never spells the word already. */
export function scrambleLetters(word) {
  let letters = shuffled([...word]);
  for (let i = 0; i < 10 && letters.join('') === word; i++) letters = shuffled([...word]);
  if (letters.join('') === word) letters = [...letters.slice(1), letters[0]];
  return letters;
}

// ---- Spelling -----------------------------------------------------------------

/** Plain words of 3-10 letters, which are fair to spell from sound alone. */
export const spellingWords = (groups, n = SPELLING_ROUNDS) =>
  shuffled(words(groups).filter((w) => /^[a-z]{3,10}$/.test(w.w))).slice(0, n);

/** Spelling is checked letter for letter; case and surrounding spaces do not count. */
export const spelledRight = (typed, word) => typed.trim().toLowerCase() === word.toLowerCase();
