// Round-based word challenges: "Điền từ vào câu" (fill the gap), "Xếp chữ cái" (letter scramble)
// and "Nghe & viết từ" (spelling from sound).
import {
  ANAGRAM_ROUNDS, CLOZE_ROUNDS, SPELLING_ROUNDS, anagramWords, clozeRound, scrambleLetters, spelledRight, spellingWords,
} from '../challenges.js';
import { speaker } from '../speech.js';
import { accent, bigBtn, esc, sayBtn, topBar } from '../ui.js';
import { showResult } from './challenge-timed.js';

const loading = (title, emoji) => `<div class="screen">${topBar(title)}<div class="empty"><div class="huge">${emoji}</div><p class="muted">Đang chuẩn bị…</p></div></div>`;
const progressLine = (i, total, score) => `Câu ${i + 1}/${total} · đúng ${score}`;
const verdict = (ok, body) => `<div class="answer ${ok ? 'ok' : 'bad'}"><div class="answer-title">${ok ? '🎉 Chính xác!' : '😅 Chưa đúng'}</div>${body}</div>`;

/**
 * Shared round loop: `make()` builds the list of rounds, `onStart()` resets the screen's own state;
 * the screen calls next() after each answer.
 */
function rounds(app, id, total, make, onStart = () => {}) {
  const s = { list: null, index: 0, score: 0 };
  s.start = () => { s.list = make(); s.index = 0; s.score = 0; onStart(); };
  s.next = () => {
    if (s.index < total - 1) { s.index++; return true; }
    showResult(app, id, {
      title: 'Xong rồi!', score: s.score, label: `Đúng ${s.score}/${total}`, xp: s.score,
      restart: () => { s.start(); app.update(); },
    });
    return false;
  };
  return s;
}

export function clozeScreen(app) {
  let picked = null;
  const s = rounds(app, 'cloze', CLOZE_ROUNDS, () => Array.from({ length: CLOZE_ROUNDS }, () => clozeRound(app.vocab.groups)), () => { picked = null; });
  return {
    enter() { app.loadVocab().then(() => { s.start(); app.update(); }); },
    view() {
      if (!s.list) return loading('Điền từ vào câu', '🧩');
      const r = s.list[s.index];
      return `<div class="screen" ${accent('peach')}>
        ${topBar('🧩 Điền từ vào câu', progressLine(s.index, CLOZE_ROUNDS, s.score))}
        <div class="scroll pad">
          <div class="prompt cloze">${esc(r.sentence)}</div>
          <div class="stack">${r.options.map((o, i) => {
            const state = picked == null ? '' : o === r.answer ? 'right' : i === picked ? 'wrong' : '';
            return `<button class="option ${state}" data-act="pick" data-i="${i}" ${picked != null ? 'disabled' : ''}>${esc(o)}</button>`;
          }).join('')}</div>
          ${picked != null ? `${verdict(r.options[picked] === r.answer, `<div><b>${esc(r.word.w)}</b> ${esc(r.word.ipa)} · ${esc(r.word.vi)}</div><div class="muted">${esc(r.translation || '')}</div>`)}
            ${bigBtn(s.index < CLOZE_ROUNDS - 1 ? 'Câu tiếp →' : 'Xem kết quả', 'next')}` : ''}
        </div>
      </div>`;
    },
    actions: {
      pick: (d) => {
        if (picked != null) return false;
        picked = +d.i;
        const r = s.list[s.index];
        if (r.options[picked] === r.answer) s.score++;
        speaker.say(r.word.ex);
      },
      next: () => { if (s.next()) { picked = null; return; } return false; },
    },
  };
}

export function anagramScreen(app) {
  let letters = [], placed = [], checked = null;
  const setUp = () => { letters = scrambleLetters(s.list[s.index].w); placed = []; checked = null; };
  const s = rounds(app, 'anagram', ANAGRAM_ROUNDS, () => anagramWords(app.vocab.groups), setUp);
  return {
    enter() { app.loadVocab().then(() => { s.start(); app.update(); }); },
    view() {
      if (!s.list) return loading('Xếp chữ cái', '🔀');
      const w = s.list[s.index];
      return `<div class="screen" ${accent('sky')}>
        ${topBar('🔀 Xếp chữ cái', progressLine(s.index, ANAGRAM_ROUNDS, s.score))}
        <div class="scroll pad center">
          <div class="note" style="background:var(--sun-l)">💡 Nghĩa: <b>${esc(w.vi)}</b> · ${esc(w.ipa)}</div>
          <div class="tray letters">${placed.map((i) => `<button class="word letter filled" data-act="unplace" data-i="${i}">${letters[i]}</button>`).join('')}</div>
          <div class="tiles letters">${letters.map((ch, i) => (placed.includes(i) ? '' : `<button class="word letter" data-act="place" data-i="${i}">${ch}</button>`)).join('')}</div>
          ${checked == null
            ? `${bigBtn('Kiểm tra', 'check', { disabled: placed.length !== letters.length })}<button class="link mt" data-act="skip">Bỏ qua từ này</button>`
            : `${verdict(checked, `<div>Từ đúng: <b>${esc(w.w)}</b></div><div class="muted italic">${esc(w.ex || '')}</div>`)}
              ${bigBtn(s.index < ANAGRAM_ROUNDS - 1 ? 'Từ tiếp →' : 'Xem kết quả', 'next')}`}
        </div>
      </div>`;
    },
    actions: {
      place: (d) => { if (checked == null) placed.push(+d.i); },
      unplace: (d) => { if (checked == null) placed = placed.filter((i) => i !== +d.i); },
      check: () => {
        const w = s.list[s.index].w;
        checked = placed.map((i) => letters[i]).join('') === w;
        if (checked) s.score++;
        speaker.say(w);
      },
      skip: () => { checked = false; speaker.say(s.list[s.index].w); },
      next: () => { if (s.next()) { setUp(); return; } return false; },
    },
  };
}

export function spellingScreen(app) {
  let typed = '', result = null;
  const sayWord = () => setTimeout(() => speaker.say(s.list[s.index].w), 300);
  const s = rounds(app, 'spelling', SPELLING_ROUNDS, () => spellingWords(app.vocab.groups), () => { typed = ''; result = null; sayWord(); });
  return {
    enter() { app.loadVocab().then(() => { s.start(); app.update(); }); },
    view() {
      if (!s.list) return loading('Nghe & viết từ', '👂');
      const w = s.list[s.index];
      return `<div class="screen" ${accent('mint')}>
        ${topBar('👂 Nghe & viết từ', progressLine(s.index, SPELLING_ROUNDS, s.score))}
        <div class="scroll pad center">
          <p class="muted">Nghe rồi gõ lại từ tiếng Anh. Nghe bao nhiêu lần cũng được.</p>
          <div class="row center-h gap-l">${sayBtn(w.w, { size: 72 })}${sayBtn(w.w, { size: 72, slow: true })}</div>
          <input class="field spell" data-input="typed" value="${esc(typed)}" placeholder="Gõ từ bạn nghe được…" autocapitalize="off" autocorrect="off" autocomplete="off" spellcheck="false" ${result != null ? 'readonly' : ''}>
          ${result == null
            ? bigBtn('Kiểm tra', 'check', { disabled: !typed.trim() })
            : `${verdict(result, `<div><b>${esc(w.w)}</b> ${esc(w.ipa)} · ${esc(w.vi)}</div>`)}
              ${bigBtn(s.index < SPELLING_ROUNDS - 1 ? 'Từ tiếp →' : 'Xem kết quả', 'next')}`}
        </div>
      </div>`;
    },
    actions: {
      typed: (v, el) => {
        typed = v;
        const btn = el.closest('.scroll').querySelector('[data-act=check]');
        if (btn) btn.disabled = !v.trim();
      },
      check: () => {
        result = spelledRight(typed, s.list[s.index].w);
        if (result) s.score++;
      },
      next: () => {
        if (!s.next()) return false;
        typed = ''; result = null;
        sayWord();
      },
    },
  };
}
