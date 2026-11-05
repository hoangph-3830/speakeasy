// Progress, review deck and AI settings, kept in localStorage on this device
// (the web counterpart of the Android SharedPreferences stores).
import {
  ACTIVITIES, activity, addXp, cardsFor, defaultProgress, epochDay, questCount, rollOver, schedule,
} from './logic.js';

function read(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Private mode or full storage: keep working in memory.
  }
}

const listeners = new Set();
export const onStoreChange = (fn) => listeners.add(fn);
const notify = () => listeners.forEach((fn) => fn());

// ---- Progress -------------------------------------------------------------

let progress = rollOver({ ...defaultProgress(), ...read('se.progress', {}) }, epochDay());

const saveProgress = (p) => {
  progress = p;
  write('se.progress', p);
  notify();
};
const current = () => rollOver(progress, epochDay());

export const Progress = {
  get: () => (progress = current()),

  addXp(amount) {
    if (amount > 0) saveProgress(addXp(current(), amount, epochDay()));
  },

  /** Counts toward today's quests. */
  count(key, n = 1) {
    const p = current();
    saveProgress({ ...p, counters: { ...p.counters, [key]: (p.counters[key] || 0) + n } });
  },

  /** Claims a finished quest's reward; returns the XP given (0 if not finished or already claimed). */
  claim(quest) {
    const p = current();
    if (p.claimed.includes(quest.id) || questCount(p, quest.id) < quest.target) return 0;
    saveProgress({ ...p, claimed: [...p.claimed, quest.id] });
    this.addXp(quest.reward);
    return quest.reward;
  },

  /** Keeps the best score per pronunciation lesson. */
  recordSound(lessonId, score) {
    const p = current();
    if ((p.soundScores[lessonId] ?? -1) < score) saveProgress({ ...p, soundScores: { ...p.soundScores, [lessonId]: score } });
  },

  /** Marks an activity done and returns the XP earned: full XP the first time, a small bonus on replays. */
  complete(topicId, activityId, bonus = 0) {
    const key = `${topicId}:${activityId}`;
    const first = !progress.done.includes(key);
    if (first) saveProgress({ ...current(), done: [...progress.done, key] });
    const earned = (first ? activity(activityId).xp : 2) + bonus;
    this.addXp(earned);
    return earned;
  },

  /** Records a finished item that is not a topic activity, e.g. "story:station". */
  markDone(key) {
    if (!progress.done.includes(key)) saveProgress({ ...current(), done: [...progress.done, key] });
  },

  toggleWord(word) {
    const p = current();
    const has = p.learnedWords.includes(word);
    saveProgress({ ...p, learnedWords: has ? p.learnedWords.filter((w) => w !== word) : [...p.learnedWords, word] });
  },

  set(fields) {
    saveProgress({ ...current(), ...fields });
  },

  reset() {
    saveProgress(rollOver(defaultProgress(), epochDay()));
  },
};

// ---- Review deck ----------------------------------------------------------

let cards = read('se.review', []);
const saveCards = (c) => {
  cards = c;
  write('se.review', c);
  notify();
};

export const Review = {
  all: () => cards,
  today: () => epochDay(),
  due: () => cards.filter((c) => c.due <= epochDay()),

  /** Adds the topic's cards that are not in the deck yet; they are due immediately. */
  enroll(topic) {
    return this.add(cardsFor(topic));
  },

  /** Adds any cards not in the deck yet (due immediately); returns how many were new. */
  add(list) {
    const known = new Set(cards.map((c) => c.id));
    const fresh = list
      .filter((c) => !known.has(c.id) && known.add(c.id))
      .map((c) => ({ ...c, interval: 0, ease: 2.5, reps: 0, due: epochDay() }));
    if (fresh.length) saveCards([...cards, ...fresh]);
    return fresh.length;
  },

  has: (id) => cards.some((c) => c.id === id),

  grade(card, grade) {
    const next = schedule(card, grade, epochDay());
    saveCards(cards.map((c) => (c.id === card.id ? next : c)));
    return next;
  },

  reset: () => saveCards([]),
};

// ---- AI settings ----------------------------------------------------------

let ai = { level: 'MEDIUM', autoSpeak: true, apiKey: '', newsProxy: '', ...read('se.ai', {}) };

export const AiSettings = {
  get: () => ai,
  set(fields) {
    ai = { ...ai, ...fields };
    write('se.ai', ai);
    notify();
  },
};

// ---- Backup ---------------------------------------------------------------

/** Everything the learner has done, as one JSON file they can keep or move to another device. */
export const exportBackup = () =>
  JSON.stringify({ app: 'speakeasy', version: 1, progress: current(), review: cards }, null, 2);

export function importBackup(text) {
  const data = JSON.parse(text);
  if (data.app !== 'speakeasy' || !data.progress) throw new Error('File không phải bản sao lưu SpeakEasy.');
  saveProgress(rollOver({ ...defaultProgress(), ...data.progress }, epochDay()));
  saveCards(Array.isArray(data.review) ? data.review : []);
}

export const activityCount = ACTIVITIES.length;
