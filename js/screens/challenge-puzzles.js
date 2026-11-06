// Puzzle challenges without a clock: "Lật thẻ trí nhớ" (memory pairs) and "Đoán chữ" (hangman).
import {
  HANGMAN_LIVES, HANGMAN_ROUNDS, hangmanWords, isPair, livesLeft, memoryDeck, revealMask, solved,
} from '../challenges.js';
import { speaker } from '../speech.js';
import { accent, bigBtn, esc, topBar } from '../ui.js';
import { showResult } from './challenge-timed.js';

const loading = (title, emoji) => `<div class="screen">${topBar(title)}<div class="empty"><div class="huge">${emoji}</div><p class="muted">Đang chuẩn bị…</p></div></div>`;

export function memoryScreen(app) {
  let deck = null, open = [], matched = new Set(), moves = 0, started = 0, busy = false;

  function start() {
    deck = memoryDeck(app.vocab.groups);
    open = []; matched = new Set(); moves = 0; started = Date.now(); busy = false;
  }

  return {
    enter() { app.loadVocab().then(() => { start(); app.update(); }); },
    view() {
      if (!deck) return loading('Lật thẻ trí nhớ', '🃏');
      return `<div class="screen" ${accent('lavender')}>
        ${topBar('🃏 Lật thẻ trí nhớ', `${moves} lượt · ${matched.size / 2}/6 cặp`)}
        <div class="scroll pad">
          <p class="muted small center">Lật 2 thẻ: một từ tiếng Anh và nghĩa tiếng Việt của nó.</p>
          <div class="memory">${deck.map((c, i) => {
            const up = open.includes(i) || matched.has(i);
            return `<button class="mem-card ${up ? 'up' : ''} ${matched.has(i) ? 'done' : ''} ${c.side}" data-act="flip" data-i="${i}" ${up ? 'disabled' : ''}>
              ${up ? esc(c.text) : '?'}</button>`;
          }).join('')}</div>
        </div>
      </div>`;
    },
    actions: {
      flip: (d) => {
        const i = +d.i;
        if (busy || open.includes(i) || matched.has(i)) return false;
        open.push(i);
        if (deck[i].side === 'en') speaker.say(deck[i].text);
        if (open.length < 2) return;
        moves++;
        const [a, b] = open.map((k) => deck[k]);
        if (isPair(a, b)) {
          open.forEach((k) => matched.add(k));
          open = [];
          if (matched.size === deck.length) {
            const secs = Math.round((Date.now() - started) / 1000);
            setTimeout(() => showResult(app, 'memory', { title: 'Xong rồi!', score: moves, label: `${moves} lượt · ${secs} giây`, xp: 5, restart: () => { start(); app.update(); } }), 300);
          }
          return;
        }
        // Not a pair: let the learner see both cards before they turn back.
        busy = true;
        setTimeout(() => { open = []; busy = false; app.update(); }, 800);
      },
    },
  };
}

const LETTERS = 'abcdefghijklmnopqrstuvwxyz'.split('');

export function hangmanScreen(app) {
  let words = null, index = 0, guessed = new Set(), wins = 0;

  function start() {
    words = hangmanWords(app.vocab.groups);
    index = 0; guessed = new Set(); wins = 0;
  }
  const finish = () =>
    showResult(app, 'hangman', { title: 'Xong rồi!', score: wins, label: `Đoán đúng ${wins}/${HANGMAN_ROUNDS} từ`, xp: wins * 2, restart: () => { start(); app.update(); } });

  return {
    enter() { app.loadVocab().then(() => { start(); app.update(); }); },
    view() {
      if (!words) return loading('Đoán chữ', '🔤');
      const w = words[index];
      const lives = livesLeft(w.w, guessed);
      const won = solved(w.w, guessed);
      const lost = lives <= 0;
      return `<div class="screen" ${accent('sky')}>
        ${topBar('🔤 Đoán chữ', `Từ ${index + 1}/${HANGMAN_ROUNDS} · đúng ${wins}`)}
        <div class="scroll pad center">
          <div class="lives">${'❤️'.repeat(Math.max(0, lives))}${'🤍'.repeat(HANGMAN_LIVES - Math.max(0, lives))}</div>
          <div class="mask">${esc(won || lost ? [...w.w].join(' ') : revealMask(w.w, guessed))}</div>
          <div class="note" style="background:var(--sun-l)">💡 Nghĩa: <b>${esc(w.vi)}</b> · ${esc(w.ipa)}</div>
          ${won ? `<div class="answer ok"><div class="answer-title">🎉 Chính xác!</div><div class="italic">${esc(w.ex)}</div></div>`
            : lost ? `<div class="answer bad"><div class="answer-title">😅 Hết mạng rồi</div><div>Từ đúng là <b>${esc(w.w)}</b></div></div>` : ''}
          ${won || lost ? bigBtn(index < HANGMAN_ROUNDS - 1 ? 'Từ tiếp theo →' : 'Xem kết quả', 'nextWord', { color: won ? 'mint' : 'coral' })
            : `<div class="keys">${LETTERS.map((ch) => `<button class="key ${guessed.has(ch) ? (w.w.includes(ch) ? 'hit' : 'miss') : ''}" data-act="guess" data-c="${ch}" ${guessed.has(ch) ? 'disabled' : ''}>${ch}</button>`).join('')}</div>`}
        </div>
      </div>`;
    },
    actions: {
      guess: (d) => {
        const w = words[index].w;
        guessed.add(d.c);
        if (solved(w, guessed)) { wins++; speaker.say(w); }
      },
      nextWord: () => {
        if (index < HANGMAN_ROUNDS - 1) { index++; guessed = new Set(); return; }
        finish();
        return false;
      },
    },
  };
}
