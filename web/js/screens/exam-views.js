// Building blocks shared by exam practice and the timed test: the listening player, reading
// passages, multiple-choice questions, and the open speaking and writing tasks.
import { wordCount } from '../exam.js';
import { bestSentenceScore } from '../logic.js';
import { listen, speaker } from '../speech.js';
import { esc, micBtn, sayBtn, scored } from '../ui.js';

const PITCH = { M: 0.85, W: 1.2, N: 1 };
const LETTERS = 'ABCD';
let playing = 0;

/** Reads the lines one after another, a different pitch per speaker; a new call stops the old one. */
export async function playLines(lines, onDone) {
  const mine = ++playing;
  for (const l of lines) {
    if (mine !== playing) return;
    await speaker.sayAndWait(l.en, PITCH[l.who] ?? 1);
  }
  if (mine === playing) onDone?.();
}
export const stopLines = () => { playing++; speaker.stop(); };

/** Play button for a listening item; the script stays hidden until `reveal`. */
export const audioBlock = (item, reveal) => (!item.audio ? '' : `
  <div class="card exam-audio">
    <button class="btn" data-act="play" data-id="${esc(item.id)}">🔊 Nghe${item.audio.length > 1 ? ` (${item.audio.length} câu)` : ''}</button>
    <span class="muted tiny">Nghe được nhiều lần khi luyện; khi thi thật chỉ nghe một lần.</span>
    ${reveal ? `<details class="mt-s"><summary class="small bold">📜 Xem lời thoại</summary>${item.audio.map((l) =>
      `<p class="small"><b>${esc(l.who)}:</b> ${esc(l.en)}</p>`).join('')}</details>` : ''}
  </div>`);

export const passageBlock = (item) => (!item.passage ? '' : `
  <div class="card exam-passage">${esc(item.passage).split('\n').filter(Boolean).map((p) => `<p>${p}</p>`).join('')}</div>`);

/**
 * One question. `chosen` is the picked option index (or undefined); `reveal` shows right/wrong and
 * the explanation. Hidden options (TOEIC Part 2) show only their letters until revealed.
 */
export function questionBlock(item, i, chosen, reveal) {
  const q = item.questions[i];
  const key = `${item.id}:${i}`;
  return `<div class="exam-q">
    <div class="semi">${item.questions.length > 1 ? `${i + 1}. ` : ''}${esc(q.q)}</div>
    <div class="stack">${q.options.map((o, k) => {
      const state = reveal ? (k === q.answer ? 'right' : k === chosen ? 'wrong' : '') : k === chosen ? 'picked' : '';
      const label = item.hideOptions && !reveal ? LETTERS[k] : `${LETTERS[k]}. ${o}`;
      return `<button class="option ${state}" data-act="choose" data-key="${esc(key)}" data-k="${k}" ${reveal ? 'disabled' : ''}>${esc(label)}</button>`;
    }).join('')}</div>
    ${reveal && q.explain ? `<p class="small exam-explain">💡 ${esc(q.explain)}</p>` : ''}
  </div>`;
}

/** Speaking task: say the answer, see what was heard (and a score when reading aloud), then the sample. */
export function speakBlock(item, state) {
  const heard = state.heard;
  return `<div class="card">
    <div class="semi">${esc(item.prompt).replace(/\n/g, '<br>')}</div>
    ${item.promptVi ? `<p class="muted small">${esc(item.promptVi)}</p>` : ''}
    <p class="muted tiny">⏱️ Chuẩn bị ${item.prepareSeconds || 0} giây · nói ${item.speakSeconds || 45} giây. Bấm 🎤 rồi nói liền một mạch.</p>
    <div class="center">${micBtn('speak', 72, `data-id="${esc(item.id)}"`)}</div>
    ${heard ? (item.readAloud && state.result
      ? `<div class="card">${scored(state.result)}<div class="h-md">${state.result.score}</div></div>`
      : `<div class="card"><div class="muted tiny">Máy nghe được (${wordCount(heard)} từ):</div><p>${esc(heard)}</p></div>`) : ''}
    <button class="link bold" data-act="sample" data-id="${esc(item.id)}">${state.sample ? '▴ Ẩn bài mẫu' : '📄 Xem bài mẫu & mẹo'}</button>
    ${state.sample ? `<div class="card exam-sample">${sayBtn(item.sample)}<p>${esc(item.sample)}</p>
      ${(item.tips || []).map((t) => `<p class="small">✅ ${esc(t)}</p>`).join('')}</div>` : ''}
  </div>`;
}

/** Recognizes one spoken answer for a speaking task and stores it in `state`. */
export function recordAnswer(item, state, update) {
  listen(item.readAloud ? item.sample : item.prompt.split('\n')[0], {
    onResult: (alts) => {
      state.heard = alts[0];
      if (item.readAloud) state.result = bestSentenceScore(item.sample, alts);
      update();
    },
  });
}

/** Writing task: a draft box with a live word count, then the model answer and a self-check list. */
export const writeBlock = (item, state) => `<div class="card">
  <div class="semi">${esc(item.prompt).replace(/\n/g, '<br>')}</div>
  ${item.promptVi ? `<p class="muted small">${esc(item.promptVi)}</p>` : ''}
  <textarea class="field exam-write" rows="10" data-input="draft" data-id="${esc(item.id)}" placeholder="Viết bài của bạn ở đây…" spellcheck="false">${esc(state.draft || '')}</textarea>
  <div class="tiny muted"><span class="wc">${wordCount(state.draft || '')}</span> / tối thiểu ${item.minWords || 0} từ</div>
  <button class="link bold" data-act="sample" data-id="${esc(item.id)}">${state.sample ? '▴ Ẩn bài mẫu' : '📄 Xem bài mẫu & tự chấm'}</button>
  ${state.sample ? `<div class="card exam-sample"><p class="muted tiny">Bài mẫu (${wordCount(item.sample)} từ)</p>
    ${esc(item.sample).split('\n').filter(Boolean).map((p) => `<p>${p}</p>`).join('')}
    ${(item.checklist || []).length ? `<div class="semi mt-s">Tự kiểm tra bài của bạn</div>${item.checklist.map((c) => `<label class="row gap-s small"><input type="checkbox"> ${esc(c)}</label>`).join('')}` : ''}
  </div>` : ''}
</div>`;
