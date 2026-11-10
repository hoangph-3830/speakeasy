// "Từ của ngày": a five-letter word puzzle, the same word for everyone each day, kept per day in
// progress so leaving and coming back keeps the board. Rules live in ../daily-word.js.
import {
  MAX_GUESSES, WORD_LENGTH, isOver, isWon, keyColours, puzzleWords, recordResult, scoreGuess, shareText, validGuess, wordOfDay,
} from '../daily-word.js';
import { epochDay } from '../logic.js';
import { speaker, toast } from '../speech.js';
import { Progress } from '../store.js';
import { accent, esc, topBar } from '../ui.js';

const ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];

export function dailyWordScreen(app) {
  let answer = null, typed = '', shake = false;
  const day = epochDay();
  const guesses = () => {
    const d = Progress.get().daily;
    return d && d.day === day ? d.guesses : [];
  };

  function submit() {
    const list = guesses();
    if (isOver(list, answer.w)) return;
    if (!validGuess(typed)) { shake = true; toast(`Cần đủ ${WORD_LENGTH} chữ cái.`); return; }
    const next = [...list, typed];
    typed = '';
    const fields = { daily: { day, guesses: next } };
    if (isOver(next, answer.w)) {
      const won = isWon(next, answer.w);
      fields.dailyStats = recordResult(Progress.get().dailyStats, day, won, next.length);
      if (won) Progress.addXp(5 + (MAX_GUESSES - next.length) * 2);
      speaker.say(answer.w);
    }
    Progress.set(fields);
  }

  const tile = (ch, state = '') => `<span class="dw-tile ${state}">${esc(ch || '')}</span>`;

  function board(list) {
    const rows = list.map((g) => [...g].map((ch, i) => tile(ch, scoreGuess(g, answer.w)[i])).join(''));
    if (rows.length < MAX_GUESSES && !isOver(list, answer.w))
      rows.push(Array.from({ length: WORD_LENGTH }, (_, i) => tile(typed[i], typed[i] ? 'typed' : '')).join(''));
    while (rows.length < MAX_GUESSES) rows.push(Array.from({ length: WORD_LENGTH }, () => tile('')).join(''));
    return rows.map((r, i) => `<div class="dw-row ${shake && i === list.length ? 'shake' : ''}">${r}</div>`).join('');
  }

  function result(list) {
    const stats = Progress.get().dailyStats;
    const won = isWon(list, answer.w);
    return `<div class="answer ${won ? 'ok' : 'bad'}">
      <div class="answer-title">${won ? `🎉 Giải được sau ${list.length} lần!` : '😅 Hôm nay chưa đoán ra'}</div>
      <div>Từ hôm nay: <b>${esc(answer.w)}</b> ${esc(answer.ipa)} · ${esc(answer.vi)}</div>
      ${answer.ex ? `<div class="muted italic">${esc(answer.ex)}</div>` : ''}
      <div class="row gap mt-s dw-stats">
        <div><b>${stats.played}</b><span class="tiny muted">đã chơi</span></div>
        <div><b>${stats.played ? Math.round((stats.won * 100) / stats.played) : 0}%</b><span class="tiny muted">thắng</span></div>
        <div><b>🔥 ${stats.streak}</b><span class="tiny muted">chuỗi</span></div>
        <div><b>${stats.best}</b><span class="tiny muted">kỷ lục</span></div>
      </div>
      <button class="btn mt-s" data-act="share">📤 Chia sẻ kết quả</button>
      <p class="muted tiny">Ngày mai có từ mới. Quay lại để giữ chuỗi nhé!</p>
    </div>`;
  }

  return {
    enter() { app.loadVocab().then(() => { answer = wordOfDay(puzzleWords(app.vocab.groups), day); app.update(); }); },
    view() {
      if (!answer) return `<div class="screen">${topBar('Từ của ngày')}<div class="empty"><div class="huge">🔠</div><p class="muted">Đang chuẩn bị…</p></div></div>`;
      const list = guesses();
      const over = isOver(list, answer.w);
      const keys = keyColours(list, answer.w);
      const view = `<div class="screen" ${accent('mint')}>
        ${topBar('🔠 Từ của ngày', `Đoán từ tiếng Anh ${WORD_LENGTH} chữ cái trong ${MAX_GUESSES} lần`)}
        <div class="scroll pad center" data-scroll="daily">
          <div class="dw-board">${board(list)}</div>
          <p class="muted tiny">🟩 đúng chỗ · 🟨 có trong từ nhưng sai chỗ · ⬜ không có</p>
          ${over ? result(list) : `<div class="dw-keys">${ROWS.map((row, r) => `<div class="dw-key-row">
            ${r === 2 ? '<button class="dw-key wide" data-act="enter">Đoán</button>' : ''}
            ${[...row].map((ch) => `<button class="dw-key ${keys[ch] || ''}" data-act="key" data-c="${ch}">${ch}</button>`).join('')}
            ${r === 2 ? '<button class="dw-key wide" data-act="back">⌫</button>' : ''}</div>`).join('')}</div>`}
        </div>
      </div>`;
      shake = false;
      return view;
    },
    actions: {
      key: (d) => { if (typed.length < WORD_LENGTH) typed += d.c; },
      back: () => { typed = typed.slice(0, -1); },
      enter: () => submit(),
      share: async () => {
        const text = shareText(guesses(), answer.w, day);
        try {
          if (navigator.share) await navigator.share({ text });
          else { await navigator.clipboard.writeText(text); toast('Đã chép kết quả, dán vào tin nhắn nhé!'); }
        } catch {
          // The learner closed the share sheet.
        }
        return false;
      },
    },
  };
}
