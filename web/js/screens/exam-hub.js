// "Luyện thi": TOEIC and IELTS (and exams a learner imports for themself), each split into
// practice by section and a timed test. Imported exams stay in this browser only.
import { SKILLS, examProblems, isQuestionItem } from '../exam.js';
import { toast } from '../speech.js';
import { Progress } from '../store.js';
import { accent, badge, bar, esc, openDialog, topBar } from '../ui.js';

const BUILT_IN = ['toeic', 'ielts'];
const LOCAL_KEY = 'se.exams';

export function localExams() {
  try { return JSON.parse(localStorage.getItem(LOCAL_KEY) || '[]'); } catch { return []; }
}
function saveLocal(list) {
  try { localStorage.setItem(LOCAL_KEY, JSON.stringify(list)); } catch { toast('Máy hết chỗ lưu, không lưu được đề.'); }
}

/** Built-in exams load once on first visit; imported ones are read fresh every time. */
export async function loadExams(app) {
  app.builtInExams ??= await Promise.all(BUILT_IN.map((id) => fetch(`data/exam-${id}.json`, { cache: 'no-cache' }).then((r) => r.json())));
  return [...app.builtInExams, ...localExams().map((e) => ({ ...e, local: true }))];
}

export function examListScreen(app) {
  let exams = null;
  return {
    enter() { loadExams(app).then((e) => { exams = e; app.update(); }); },
    view: () => `<div class="screen" ${accent('sky')}>
      ${topBar('🎓 Luyện thi', 'TOEIC · IELTS · đề của bạn')}
      <div class="scroll pad" data-scroll="exams">
        ${!exams ? '<p class="muted">Đang tải…</p>' : exams.map((e) => `
          <button class="card tile" data-act="nav" data-to="exam/${esc(e.id)}">
            ${badge(e.emoji || '📝', 'var(--sky-l)', 56)}
            <div class="grow"><div class="t-sub">${esc(e.title)}${e.local ? ' <span class="tiny muted">(đề của bạn)</span>' : ''}</div>
              <div class="muted small">${esc(e.about || '')}</div></div>
            <span class="go">→</span>
          </button>`).join('')}
        <label class="btn outline mt">📥 Nhập đề riêng (file .json)<input type="file" accept="application/json,.json" data-input="importExam" hidden></label>
        <p class="muted tiny">Đề nhập chỉ lưu trong trình duyệt trên máy này, không đăng lên mạng. Câu hỏi ở đây là bài soạn mới theo dạng đề thi, không phải đề thi chính thức của ETS hay IELTS.</p>
      </div>
    </div>`,
    actions: {
      importExam: async (v, el) => {
        const file = el.files?.[0];
        if (!file) return;
        try {
          const exam = JSON.parse(await file.text());
          const problems = examProblems(exam);
          if (problems.length) throw new Error(`File đề chưa đúng định dạng: ${problems.slice(0, 2).join('; ')}`);
          if (BUILT_IN.includes(exam.id)) exam.id = `${exam.id}-${Date.now()}`;
          saveLocal([...localExams().filter((x) => x.id !== exam.id), exam]);
          exams = await loadExams(app);
          toast(`Đã nhập “${exam.title}” ✅`);
          app.update();
        } catch (e) {
          toast(e.message || 'Không đọc được file đề.');
        }
      },
    },
  };
}

export function examScreen(app, id) {
  let exam = null;
  return {
    enter() { loadExams(app).then((list) => { exam = list.find((e) => e.id === id) || null; app.update(); }); },
    view() {
      if (!exam) return `<div class="screen">${topBar('Luyện thi')}<p class="muted pad">Đang tải…</p></div>`;
      const best = Progress.get().examBest || {};
      const last = (Progress.get().examHistory || []).filter((h) => h.exam === exam.id).slice(-1)[0];
      return `<div class="screen" ${accent('sky')}>
        ${topBar(`${exam.emoji || '📝'} ${exam.title}`, exam.about || '')}
        <div class="scroll pad" data-scroll="exam-${esc(exam.id)}">
          ${exam.test ? `<button class="card next-card" data-act="nav" data-to="exam/${esc(exam.id)}/test" style="--a:var(--coral);--al:var(--coral-l)">
            <div class="tiny bold" style="color:var(--a)">LUYỆN ĐỀ CÓ BẤM GIỜ · ${exam.test.minutes} PHÚT</div>
            <div class="t-sub">${esc(exam.test.title || 'Đề luyện')}</div>
            <div class="muted small">${last ? `Lần gần nhất: ${last.results.map((r) => `${SKILLS[r.skill].title} ${r.score}`).join(' · ')}` : 'Mỗi lần làm là một đề mới, chấm điểm và quy đổi ước lượng.'}</div>
          </button>` : ''}
          ${Object.entries(SKILLS).map(([skill, s]) => {
            const sections = exam.sections.filter((x) => x.skill === skill);
            if (!sections.length) return '';
            return `<div class="section"><h2>${s.emoji} ${s.title}</h2></div>${sections.map((x) => {
              const b = best[x.id];
              const count = x.items.filter(isQuestionItem).reduce((n, it) => n + it.questions.length, 0);
              return `<button class="card tile" data-act="nav" data-to="exam/${esc(exam.id)}/s/${esc(x.id)}">
                <div class="grow"><div class="t-sub">${esc(x.title)}</div>
                  <div class="muted small">${esc(x.about || '')}</div>
                  <div class="tiny muted mt-xs">${count ? `${count} câu hỏi` : `${x.items.length} bài`}</div>
                  ${b != null ? `<div class="mt-s">${bar(b / 100, b >= 80 ? 'mint' : b >= 50 ? 'peach' : 'coral')}</div>` : ''}</div>
                ${b != null ? `<b>${b}%</b>` : '<span class="go">→</span>'}
              </button>`;
            }).join('')}`;
          }).join('')}
          ${exam.local ? '<button class="link mt" data-act="remove" style="color:var(--coral)">Xoá đề này khỏi máy</button>' : ''}
        </div>
      </div>`;
    },
    actions: {
      remove: () => {
        openDialog({
          title: 'Xoá đề này?', body: `<p>“${esc(exam.title)}” sẽ bị xoá khỏi máy này.</p>`,
          buttons: [{ label: 'Huỷ' }, { label: 'Xoá', primary: true, color: 'coral', onClick: () => { saveLocal(localExams().filter((x) => x.id !== exam.id)); app.nav('exam'); } }],
        });
        return false;
      },
    },
  };
}
