// Practice one exam section, one item per page, with the answer and explanation shown right away.
import { isQuestionItem, wordCount } from '../exam.js';
import { QUEST, percent } from '../logic.js';
import { Progress } from '../store.js';
import { accent, bar, bigBtn, completeDialog, esc, topBar } from '../ui.js';
import { loadExams } from './exam-hub.js';
import {
  audioBlock, passageBlock, playLines, questionBlock, recordAnswer, speakBlock, stopLines, writeBlock,
} from './exam-views.js';

export function examPracticeScreen(app, examId, sectionId) {
  let section = null, index = 0;
  const answers = {};   // "itemId:q" -> chosen option
  const tasks = {};     // itemId -> { heard, result, draft, sample }
  const task = (id) => (tasks[id] ??= {});

  function finish() {
    const items = section.items.filter(isQuestionItem);
    const total = items.reduce((n, it) => n + it.questions.length, 0);
    const right = items.reduce((n, it) => n + it.questions.filter((q, i) => answers[`${it.id}:${i}`] === q.answer).length, 0);
    let message = `Bạn đã làm xong ${section.items.length} bài.`;
    if (total) {
      const score = percent(right, total);
      const best = Progress.get().examBest || {};
      if ((best[section.id] ?? -1) < score) Progress.set({ examBest: { ...best, [section.id]: score } });
      message = `Đúng ${right}/${total} câu (${score}%).`;
    }
    Progress.addXp(Math.max(5, right));
    completeDialog({ title: 'Xong phần luyện! 🎓', message, xp: Math.max(5, right), onDismiss: app.back, onAgain: () => { index = 0; Object.keys(answers).forEach((k) => delete answers[k]); app.update(); } });
  }

  return {
    enter() {
      loadExams(app).then((list) => {
        section = list.find((e) => e.id === examId)?.sections.find((s) => s.id === sectionId) || null;
        app.update();
      });
    },
    leave: stopLines,
    view() {
      if (!section) return `<div class="screen">${topBar('Luyện thi')}<p class="muted pad">Đang tải…</p></div>`;
      const item = section.items[index];
      const last = index === section.items.length - 1;
      let body;
      if (item.kind === 'speak') body = speakBlock(item, task(item.id));
      else if (item.kind === 'write') body = writeBlock(item, task(item.id));
      else {
        const done = item.questions.every((q, i) => answers[`${item.id}:${i}`] != null);
        body = `${audioBlock(item, done)}${passageBlock(item)}
          ${item.questions.map((q, i) => questionBlock(item, i, answers[`${item.id}:${i}`], answers[`${item.id}:${i}`] != null)).join('')}`;
      }
      return `<div class="screen" ${accent('sky')}>
        ${topBar(section.title, `Bài ${index + 1}/${section.items.length}`)}
        <div class="pad-x">${bar((index + 1) / section.items.length)}</div>
        <div class="scroll pad exam" data-scroll="ex-${esc(item.id)}">
          ${item.title ? `<div class="tiny bold muted">${esc(item.title)}</div>` : ''}
          ${body}
          <div class="row gap mt">
            ${index > 0 ? '<button class="btn outline grow" data-act="prev">← Bài trước</button>' : ''}
            ${bigBtn(last ? 'Hoàn thành' : 'Bài tiếp →', 'next', { color: last ? 'mint' : 'sky' })}
          </div>
        </div>
      </div>`;
    },
    actions: {
      play: () => {
        const item = section.items[index];
        playLines(item.audio, () => Progress.count(QUEST.LISTEN));
        return false;
      },
      choose: (d) => { if (answers[d.key] == null) answers[d.key] = +d.k; },
      speak: () => { const item = section.items[index]; recordAnswer(item, task(item.id), app.update); return false; },
      sample: (d) => { const t = task(d.id); t.sample = !t.sample; },
      draft: (v, el) => {
        task(el.dataset.id).draft = v;
        el.parentElement.querySelector('.wc').textContent = wordCount(v);
        return false;
      },
      prev: () => { stopLines(); index--; },
      next: () => {
        stopLines();
        if (index < section.items.length - 1) { index++; return; }
        finish();
        return false;
      },
    },
  };
}
