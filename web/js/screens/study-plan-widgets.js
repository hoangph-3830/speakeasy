// Pieces of the study plan shown on Home and Progress: placement prompt, streak warning,
// study calendar and the daily reminder setting.
import { calendarDays, heat, reminderIcs, streakAtRisk, weekTotals } from '../learning-plan.js';
import { LEVELS, epochDay } from '../logic.js';
import { toast } from '../speech.js';
import { Progress } from '../store.js';
import { esc } from '../ui.js';

/** Home: invite new learners to skip what they already know. */
export const placementCard = (p) => (p.placedLevel != null ? '' : `
  <button class="card next-card placement-card" data-act="nav" data-to="placement" style="--a:var(--sky);--al:var(--sky-l)">
    <div class="row center-v gap"><span class="emo-l">🧭</span>
      <div class="grow"><div class="t-sub">Kiểm tra trình độ · 3 phút</div>
      <div class="muted small">Biết tiếng Anh rồi? Làm bài ngắn để bỏ qua phần đã biết và học đúng cấp của bạn.</div></div>
      <span class="go">Bắt đầu →</span></div>
  </button>`);

/** Home: in the evening, before the streak breaks. */
export const streakWarning = (p, hour = new Date().getHours()) => (!streakAtRisk(p, hour) ? '' : `
  <div class="note warn">🔥 Chuỗi <b>${p.streak} ngày</b> sắp mất! Học một bài ngắn hoặc chơi một thử thách để giữ chuỗi nhé.
    <button class="btn small-btn mt-xs" data-act="nav" data-to="play">⚡ Chơi 1 phút</button></div>`);

/** Progress: the last 12 weeks, one square per day, darker for more XP. */
export function studyCalendar(p) {
  const today = epochDay();
  const days = calendarDays(p.history, today);
  const { thisWeek, lastWeek } = weekTotals(p.history, today);
  const studied = days.filter((d) => d.xp > 0).length;
  const trend = thisWeek > lastWeek ? '📈 hơn tuần trước' : thisWeek < lastWeek ? '📉 ít hơn tuần trước' : '';
  return `<div class="card">
    <div class="row center-v"><span class="semi grow">Tuần này ${thisWeek} XP · tuần trước ${lastWeek} XP</span><span class="tiny muted">${trend}</span></div>
    <div class="heatmap mt-s">${days.map((d) => `<i class="h${heat(d.xp, p.dailyGoal)}${d.day === today ? ' today' : ''}" title="${d.xp} XP"></i>`).join('')}</div>
    <div class="row center-v mt-xs tiny muted"><span class="grow">${studied} ngày học trong 12 tuần qua</span>
      Ít <i class="hm h1"></i><i class="hm h2"></i><i class="hm h3"></i><i class="hm h4"></i> Nhiều</div>
  </div>`;
}

/** Progress settings: placement retake and the daily reminder. */
export function planSettings(p) {
  const placed = p.placedLevel;
  return `<div class="semi mt">🧭 Trình độ</div>
    <div class="row center-v gap-s"><span class="grow muted small">${placed == null ? 'Chưa làm bài kiểm tra trình độ.'
      : placed ? `Đã xếp lớp: ${esc(placed)} · ${esc(LEVELS[placed].name)}` : 'Bắt đầu từ A1.'}</span>
      <button class="btn small-btn" data-act="nav" data-to="placement">${placed == null ? 'Làm bài' : 'Làm lại'}</button></div>

    <div class="semi mt">⏰ Nhắc học mỗi ngày</div>
    <div class="row center-v gap-s">
      <input class="field time" type="time" value="${esc(p.reminderTime || '20:00')}" data-input="reminderTime">
      <button class="btn small-btn grow" data-act="addReminder">📅 Thêm vào Lịch</button>
    </div>
    <p class="muted tiny">Thêm một sự kiện lặp lại hằng ngày vào app Lịch, có chuông báo đúng giờ. Trên iPhone/iPad: bấm nút, chọn “Thêm tất cả”. Muốn đổi giờ thì xoá sự kiện cũ trong Lịch rồi thêm lại.</p>`;
}

export const planActions = {
  reminderTime: (v) => { if (/^\d{2}:\d{2}$/.test(v)) Progress.set({ reminderTime: v }); return false; },
  addReminder: () => {
    const ics = reminderIcs(Progress.get().reminderTime || '20:00', location.href.split('#')[0]);
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
    a.download = 'speakeasy-nhac-hoc.ics';
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 60000);
    toast('Mở file vừa tải để thêm lời nhắc vào Lịch 📅');
    return false;
  },
};
