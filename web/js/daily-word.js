// Rules of "Từ của ngày": one five-letter word a day, the same for every learner, six tries with
// green/yellow/grey letter hints. Pure functions, checked by `node web/test/logic.test.mjs`.

export const WORD_LENGTH = 5;
export const MAX_GUESSES = 6;

/** Five plain letters, once each word, in a stable order so every device picks the same word. */
export const puzzleWords = (groups) =>
  [...new Map(groups.flatMap((g) => g.words).filter((w) => /^[a-z]{5}$/.test(w.w)).map((w) => [w.w, w])).values()]
    .sort((a, b) => (a.w < b.w ? -1 : 1));

/** Spreads consecutive days across the list (a fixed step coprime with its length). */
export function wordOfDay(words, day) {
  let step = 7919;
  const gcd = (a, b) => (b ? gcd(b, a % b) : a);
  while (gcd(step, words.length) !== 1) step++;
  return words[(((day * step) % words.length) + words.length) % words.length];
}

/**
 * Colour of each letter: 'hit' right place, 'near' in the word elsewhere, 'miss' not (or no more
 * copies left). Exact hits are counted first so a repeated letter is never over-reported.
 */
export function scoreGuess(guess, answer) {
  const result = Array(guess.length).fill('miss');
  const left = {};
  [...answer].forEach((ch, i) => { if (guess[i] !== ch) left[ch] = (left[ch] || 0) + 1; });
  [...guess].forEach((ch, i) => {
    if (answer[i] === ch) result[i] = 'hit';
    else if (left[ch] > 0) { result[i] = 'near'; left[ch]--; }
  });
  return result;
}

export const validGuess = (text) => new RegExp(`^[a-z]{${WORD_LENGTH}}$`).test(text);
export const isWon = (guesses, answer) => guesses.includes(answer);
export const isOver = (guesses, answer) => isWon(guesses, answer) || guesses.length >= MAX_GUESSES;

/** Best colour seen so far for each letter, for the on-screen keyboard. */
export function keyColours(guesses, answer) {
  const rank = { miss: 0, near: 1, hit: 2 };
  const out = {};
  for (const g of guesses)
    scoreGuess(g, answer).forEach((c, i) => {
      if (out[g[i]] == null || rank[c] > rank[out[g[i]]]) out[g[i]] = c;
    });
  return out;
}

/** Stats after finishing the puzzle of `day`; the streak counts days solved in a row. */
export function recordResult(stats, day, won, tries) {
  const dist = [...stats.dist];
  if (won) dist[tries - 1]++;
  const streak = won ? (stats.lastWonDay === day - 1 ? stats.streak + 1 : 1) : 0;
  return {
    played: stats.played + 1,
    won: stats.won + (won ? 1 : 0),
    streak,
    best: Math.max(stats.best, streak),
    lastWonDay: won ? day : stats.lastWonDay,
    dist,
  };
}

/** The coloured squares people paste into a chat, without giving the word away. */
export function shareText(guesses, answer, day) {
  const squares = { hit: '🟩', near: '🟨', miss: '⬜' };
  const won = isWon(guesses, answer);
  return [`SpeakEasy · Từ của ngày #${day} ${won ? guesses.length : 'X'}/${MAX_GUESSES}`,
    ...guesses.map((g) => scoreGuess(g, answer).map((c) => squares[c]).join(''))].join('\n');
}
