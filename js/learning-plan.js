// Pure rules for the structured path around the lessons: the placement test, the study calendar
// and the daily reminder. No DOM here, so `node web/test/logic.test.mjs` checks them.
import { distinctBy, shuffled, topicSentences } from './logic.js';

export const LEVEL_ORDER = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
export const PLACEMENT_PER_LEVEL = 3;
/** Correct answers out of PLACEMENT_PER_LEVEL needed to pass a level. */
export const PLACEMENT_PASS = 2;

const levelIndex = (lv) => LEVEL_ORDER.indexOf(lv);

/** Three "what does this sentence mean" questions per level, easiest level first. */
export function placementQuiz(topics) {
  return LEVEL_ORDER.flatMap((lv) => {
    const pool = distinctBy(
      topics.filter((t) => t.level === lv).flatMap(topicSentences)
        .filter(([en]) => { const n = en.split(' ').length; return n >= 3 && n <= 14; }),
      (s) => s[1],
    );
    if (pool.length < 4) return [];
    return shuffled(pool).slice(0, PLACEMENT_PER_LEVEL).map(([en, vi]) => ({
      level: lv,
      prompt: en,
      answer: vi,
      options: shuffled([...shuffled(pool.filter((s) => s[1] !== vi)).slice(0, 3).map((s) => s[1]), vi]),
    }));
  });
}

/** Whether the learner has already missed too many questions of `level` to pass it. */
export const levelFailed = (answers, level) =>
  answers.filter((a) => a.level === level && !a.ok).length > PLACEMENT_PER_LEVEL - PLACEMENT_PASS;

/** Highest level passed with every easier level passed too; '' when even A1 was missed. */
export function placementResult(answers) {
  let placed = '';
  for (const lv of LEVEL_ORDER) {
    const mine = answers.filter((a) => a.level === lv);
    if (!mine.length || mine.filter((a) => a.ok).length < PLACEMENT_PASS) break;
    placed = lv;
  }
  return placed;
}

/** The level the learner should study next after placing at `placed`. */
export const studyLevel = (placed) => LEVEL_ORDER[Math.min(levelIndex(placed) + 1, LEVEL_ORDER.length - 1)];


// ---- Study calendar (days are filled in by recordDay in logic.js) -------------

/** The last `weeks` weeks as columns of seven days ending today, oldest first. */
export function calendarDays(history = {}, today, weeks = 12) {
  const n = weeks * 7;
  return Array.from({ length: n }, (_, i) => { const day = today - n + 1 + i; return { day, xp: history[day] || 0 }; });
}

/** XP over the last seven days and the seven before that. */
export function weekTotals(history = {}, today) {
  const sum = (from, to) => { let s = 0; for (let d = from; d <= to; d++) s += history[d] || 0; return s; };
  return { thisWeek: sum(today - 6, today), lastWeek: sum(today - 13, today - 7) };
}

/** Heat level 0-4 for a day, relative to the daily goal. */
export const heat = (xp, goal) => (xp <= 0 ? 0 : xp < goal / 2 ? 1 : xp < goal ? 2 : xp < goal * 2 ? 3 : 4);

// ---- Daily reminder ---------------------------------------------------------

/** In the evening, with nothing studied yet today, a running streak is about to break. */
export const streakAtRisk = (p, hour) => p.streak > 0 && p.todayXp === 0 && hour >= 18;

const pad = (n) => String(n).padStart(2, '0');

/**
 * A calendar file with one event repeating every day at `time` ("HH:MM") and an alert when it starts.
 * Opening it on an iPhone or iPad adds a daily reminder to the Calendar app.
 */
export function reminderIcs(time, url, now = new Date()) {
  const [h, m] = time.split(':').map(Number);
  const date = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`;
  const stamp = now.toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
  return [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//SpeakEasy//Daily reminder//VI', 'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT',
    'UID:speakeasy-daily-reminder',
    `DTSTAMP:${stamp}`,
    `DTSTART:${date}T${pad(h)}${pad(m)}00`,
    'DURATION:PT15M',
    'RRULE:FREQ=DAILY',
    'SUMMARY:🔥 Học tiếng Anh 15 phút với SpeakEasy',
    `DESCRIPTION:Giữ chuỗi ngày học nhé! ${url}`,
    `URL:${url}`,
    'BEGIN:VALARM', 'ACTION:DISPLAY', 'DESCRIPTION:Đến giờ học tiếng Anh rồi!', 'TRIGGER:PT0M', 'END:VALARM',
    'END:VEVENT', 'END:VCALENDAR', '',
  ].join('\r\n');
}

