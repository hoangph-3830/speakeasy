// Rules shared by the TOEIC and IELTS practice: drawing a timed test, scoring it, turning raw
// scores into an estimated TOEIC score or IELTS band, and checking exam files (built-in or imported).
// Pure functions, checked by `node web/test/logic.test.mjs`. The data shape is web/data/exam-*.json.
import { shuffled } from './logic.js';

export const SKILLS = {
  listening: { title: 'Nghe', emoji: '🎧' },
  reading: { title: 'Đọc', emoji: '📖' },
  speaking: { title: 'Nói', emoji: '🎤' },
  writing: { title: 'Viết', emoji: '✍️' },
};

/** Listening and reading items carry questions; speaking and writing items are open tasks. */
export const isQuestionItem = (item) => !item.kind;

/** The items of a timed test: `pick` items drawn at random from each section, in section order. */
export function buildTest(exam) {
  return exam.sections.flatMap((s) =>
    shuffled(s.items.filter(isQuestionItem)).slice(0, exam.test?.pick?.[s.id] || 0).map((item) => ({ section: s, item })));
}

/** Correct answers per skill. `answers` maps "itemId:questionIndex" to the chosen option index. */
export function scoreBySkill(entries, answers) {
  const out = {};
  for (const { section, item } of entries)
    item.questions.forEach((q, i) => {
      const s = (out[section.skill] ??= { correct: 0, total: 0 });
      s.total++;
      if (answers[`${item.id}:${i}`] === q.answer) s.correct++;
    });
  return out;
}

/** TOEIC listening or reading, 5–495 in steps of 5, scaled linearly from the share answered right. */
export function toeicEstimate(correct, total) {
  if (!total) return 5;
  return Math.max(5, Math.min(495, Math.round((5 + (490 * correct) / total) / 5) * 5));
}

// Raw score out of 40 → band, from the commonly published IELTS conversion tables.
const LISTENING_BANDS = [[39, 9], [37, 8.5], [35, 8], [32, 7.5], [30, 7], [26, 6.5], [23, 6], [18, 5.5], [16, 5], [13, 4.5], [10, 4], [8, 3.5], [6, 3], [4, 2.5]];
const READING_BANDS = [[39, 9], [37, 8.5], [35, 8], [33, 7.5], [30, 7], [27, 6.5], [23, 6], [19, 5.5], [15, 5], [13, 4.5], [10, 4], [8, 3.5], [6, 3], [4, 2.5]];

/** IELTS band for a part, scaling a short test up to the real 40 questions first. */
export function ieltsBand(skill, correct, total) {
  if (!total) return 0;
  const raw40 = Math.round((40 * correct) / total);
  const table = skill === 'reading' ? READING_BANDS : LISTENING_BANDS;
  return (table.find(([min]) => raw40 >= min) || [0, raw40 > 0 ? 2 : 0])[1];
}

/** Estimated result of a finished test, per skill, in the exam's own scale. */
export function estimate(examId, bySkill) {
  return Object.entries(bySkill).map(([skill, { correct, total }]) => ({
    skill, correct, total,
    score: examId === 'ielts' ? ieltsBand(skill, correct, total) : toeicEstimate(correct, total),
  }));
}

export const wordCount = (text) => (String(text).trim().match(/[A-Za-z0-9'’-]+/g) || []).length;

/** Problems in an exam file, empty when it can be used. Also guards files learners import. */
export function examProblems(exam) {
  const problems = [];
  if (!exam || typeof exam !== 'object') return ['not an object'];
  if (!exam.id || !exam.title || !Array.isArray(exam.sections)) return ['needs id, title and sections'];
  const ids = new Set();
  for (const s of exam.sections) {
    if (!SKILLS[s.skill]) problems.push(`${s.id}: unknown skill ${s.skill}`);
    for (const item of s.items || []) {
      if (ids.has(item.id)) problems.push(`duplicate item id ${item.id}`);
      ids.add(item.id);
      if (item.kind === 'speak' || item.kind === 'write') {
        if (!item.prompt || !item.sample) problems.push(`${item.id}: needs prompt and sample`);
        continue;
      }
      if (!item.questions?.length) problems.push(`${item.id}: no questions`);
      for (const q of item.questions || [])
        if (!q.q || !Array.isArray(q.options) || !(q.answer >= 0 && q.answer < q.options.length)) problems.push(`${item.id}: bad question "${q.q}"`);
    }
  }
  for (const [id, n] of Object.entries(exam.test?.pick || {})) {
    const s = exam.sections.find((x) => x.id === id);
    if (!s) problems.push(`test picks unknown section ${id}`);
    else if (n > s.items.filter(isQuestionItem).length) problems.push(`test picks ${n} from ${id}, which has fewer`);
  }
  return problems;
}
