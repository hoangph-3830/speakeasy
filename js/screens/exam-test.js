// Timed practice test ("Luyện đề"): a fresh draw of listening and reading items, no feedback until
// submitting, then an estimated TOEIC score or IELTS band per skill and a full review.
import { SKILLS, buildTest, estimate, scoreBySkill } from '../exam.js';
import { epochDay } from '../logic.js';
import { Progress } from '../store.js';
import { accent, bigBtn, esc, openDialog, topBar } from '../ui.js';
import { loadExams } from './exam-hub.js';
import { audioBlock, passageBlock, playLines, questionBlock, stopLines } from './exam-views.js';

export function examTestScreen(app, examId) {
  let exam = null, entries = null, answers = {}, submitted = false, results = null, endAt = 0, timer = null;

  function start() {
    entries = buildTest(exam);
    answers = {};
    submitted = false;
    results = null;
    endAt = Date.now() + exam.test.minutes * 60000;
    clearInterval(timer);
    timer = setInterval(() => {
      const left = Math.max(0, endAt - Date.now());
      const el = document.querySelector('.exam-clock');
      if (el) el.textContent = `⏱️ ${Math.floor(left / 60000)}:${String(Math.floor(left / 1000) % 60).padStart(2, '0')}`;
      if (left <= 0) { submit(); app.update(); }
    }, 1000);
  }

  function submit() {
    // The time-out can submit while the "submit anyway?" dialog is still open: count it once.
    if (submitted) return;
    clearInterval(timer);
    stopLines();
    submitted = true;
    results = estimate(exam.id, scoreBySkill(entries, answers));
    const history = [...(Progress.get().examHistory || []), { exam: exam.id, day: epochDay(), results }].slice(-20);
    Progress.set({ examHistory: history });
    Progress.addXp(10 + results.reduce((n, r) => n + r.correct, 0));
  }

  const total = () => entries.reduce((n, e) => n + e.item.questions.length, 0);
  const answered = () => Object.keys(answers).length;

  const resultCard = () => `<div class="card exam-result">
    <div class="t-title">Kết quả ước lượng</div>
    <div class="row gap">${results.map((r) => `<div class="grow center"><div class="muted small">${SKILLS[r.skill].emoji} ${SKILLS[r.skill].title}</div>
      <div class="h-md">${exam.id === 'ielts' ? `Band ${r.score}` : r.score}</div><div class="tiny muted">${r.correct}/${r.total} câu đúng</div></div>`).join('')}</div>
    ${exam.id === 'toeic' ? `<p class="small">Tổng ước lượng: <b>${results.reduce((n, r) => n + r.score, 0)}</b> / 990</p>` : ''}
    <p class="muted tiny">Đây là quy đổi gần đúng từ đề rút gọn, chỉ để theo dõi tiến bộ, không thay cho điểm thi chính thức.</p>
    ${bigBtn('Làm đề mới', 'again', { color: 'coral' })}
  </div>`;

  return {
    enter() {
      loadExams(app).then((list) => { exam = list.find((e) => e.id === examId); if (exam?.test) start(); app.update(); });
    },
    leave() { clearInterval(timer); stopLines(); },
    view() {
      if (!entries) return `<div class="screen">${topBar('Luyện đề')}<p class="muted pad">Đang tải…</p></div>`;
      return `<div class="screen" ${accent('coral')}>
        ${topBar(exam.test.title || 'Luyện đề', submitted ? 'Xem lại bài làm' : `${total()} câu · ${exam.test.minutes} phút`,
          submitted ? '' : '<b class="exam-clock">⏱️</b>')}
        <div class="scroll pad exam" data-scroll="exam-test">
          ${submitted ? resultCard() : '<p class="muted small">Bài nghe chỉ nên bấm nghe một lần, như khi thi thật. Nộp bài khi xong hoặc khi hết giờ.</p>'}
          ${entries.map(({ section, item }, n) => `<div class="card exam-entry">
            <div class="tiny bold muted">${n + 1}. ${esc(section.title)}</div>
            ${audioBlock(item, submitted)}${passageBlock(item)}
            ${item.questions.map((q, i) => questionBlock(item, i, answers[`${item.id}:${i}`], submitted)).join('')}
          </div>`).join('')}
          ${submitted ? '' : bigBtn(`Nộp bài (${answered()}/${total()})`, 'submit', { color: 'coral' })}
        </div>
      </div>`;
    },
    actions: {
      play: (d) => { playLines(entries.find((e) => e.item.id === d.id).item.audio); return false; },
      choose: (d) => { if (!submitted) answers[d.key] = +d.k; },
      submit: () => {
        const left = total() - answered();
        if (!left) { submit(); return; }
        openDialog({
          title: 'Nộp bài?', body: `<p>Còn ${left} câu chưa làm.</p>`,
          buttons: [{ label: 'Làm tiếp' }, { label: 'Nộp luôn', primary: true, color: 'coral', onClick: () => { submit(); app.update(); } }],
        });
        return false;
      },
      again: () => { start(); },
    },
  };
}
