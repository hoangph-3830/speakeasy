// Tap-to-translate: an offline English→Vietnamese lookup over the 3000-word list and lesson words,
// with an online fallback (Google Translate's free endpoint, then Gemini) for anything else.
import { AiSettings } from './store.js';

const IRREGULAR = {
  am: 'be', is: 'be', are: 'be', was: 'be', were: 'be', been: 'be', being: 'be',
  has: 'have', had: 'have', does: 'do', did: 'do', done: 'do',
  went: 'go', gone: 'go', goes: 'go', saw: 'see', seen: 'see', came: 'come', took: 'take', taken: 'take',
  made: 'make', got: 'get', gotten: 'get', gave: 'give', given: 'give', knew: 'know', known: 'know',
  thought: 'think', told: 'tell', said: 'say', found: 'find', left: 'leave', felt: 'feel', kept: 'keep',
  brought: 'bring', bought: 'buy', began: 'begin', begun: 'begin', became: 'become', ran: 'run',
  wrote: 'write', written: 'write', sat: 'sit', stood: 'stand', heard: 'hear', held: 'hold', met: 'meet',
  paid: 'pay', sent: 'send', spent: 'spend', built: 'build', lost: 'lose', meant: 'mean', led: 'lead',
  read: 'read', spoke: 'speak', spoken: 'speak', ate: 'eat', eaten: 'eat', drank: 'drink', drunk: 'drink',
  drove: 'drive', driven: 'drive', flew: 'fly', flown: 'fly', fell: 'fall', fallen: 'fall', grew: 'grow',
  grown: 'grow', chose: 'choose', chosen: 'choose', broke: 'break', broken: 'break', wore: 'wear',
  worn: 'wear', won: 'win', sold: 'sell', taught: 'teach', caught: 'catch', fought: 'fight', slept: 'sleep',
  woke: 'wake', woken: 'wake', forgot: 'forget', forgotten: 'forget', understood: 'understand', shown: 'show',
  hid: 'hide', hidden: 'hide', rose: 'rise', risen: 'rise', threw: 'throw', thrown: 'throw', drew: 'draw',
  drawn: 'draw', swam: 'swim', sang: 'sing', sung: 'sing', rang: 'ring', shot: 'shoot', struck: 'strike',
  sought: 'seek', fed: 'feed', fled: 'flee', hung: 'hang', laid: 'lay', lain: 'lie', lit: 'light',
  children: 'child', people: 'person', men: 'man', women: 'woman', feet: 'foot', teeth: 'tooth',
  mice: 'mouse', lives: 'life', wives: 'wife', knives: 'knife', leaves: 'leaf', halves: 'half',
  better: 'good', best: 'good', worse: 'bad', worst: 'bad', further: 'far', farther: 'far',
  "isn't": 'be', "aren't": 'be', "wasn't": 'be', "don't": 'do', "doesn't": 'do', "didn't": 'do',
  "can't": 'can', "won't": 'will', "i'm": 'i', "it's": 'it', "they're": 'they', "we're": 'we',
};

export const normalizeWord = (w) =>
  w.toLowerCase().replace(/[’‘]/g, "'").replace(/^[^a-z]+|[^a-z']+$/g, '').replace(/^'+|'+$/g, '');

/** Base forms a word could come from, most likely first ("stopped" → stopped, stop, stope…). */
export function lemmas(raw) {
  const w = normalizeWord(raw);
  if (!w) return [];
  const out = [w];
  const add = (x) => x && x.length > 1 && !out.includes(x) && out.push(x);
  add(IRREGULAR[w]);
  const base = w.endsWith("'s") ? w.slice(0, -2) : w;
  add(base);
  const doubled = (s) => (/([b-df-hj-np-tv-z])\1$/.test(s) ? s.slice(0, -1) : null);
  if (base.endsWith('ies')) add(base.slice(0, -3) + 'y');
  if (base.endsWith('ves')) { add(base.slice(0, -3) + 'f'); add(base.slice(0, -3) + 'fe'); }
  if (base.endsWith('es')) add(base.slice(0, -2));
  if (base.endsWith('s') && !base.endsWith('ss')) add(base.slice(0, -1));
  if (base.endsWith('ied')) add(base.slice(0, -3) + 'y');
  if (base.endsWith('ed')) {
    const stem = base.slice(0, -2);
    add(doubled(stem)); add(stem); add(stem + 'e');
  }
  if (base.endsWith('ing')) {
    const stem = base.slice(0, -3);
    add(doubled(stem)); add(stem); add(stem + 'e');
    if (stem.endsWith('y')) add(stem.slice(0, -1) + 'ie');
  }
  for (const suf of ['est', 'er']) {
    if (!base.endsWith(suf)) continue;
    const stem = base.slice(0, -suf.length);
    if (stem.endsWith('i')) add(stem.slice(0, -1) + 'y');
    add(doubled(stem)); add(stem); add(stem + 'e');
  }
  if (base.endsWith('ly')) {
    const stem = base.slice(0, -2);
    if (stem.endsWith('i')) add(stem.slice(0, -1) + 'y');
    add(stem); add(stem + 'le');
  }
  return out;
}

let index = null;

/** Builds the lookup table once from the vocabulary groups and every topic's words. */
export function buildIndex(vocab, content) {
  index = new Map();
  for (const g of vocab?.groups || [])
    for (const w of g.words) if (!index.has(w.w.toLowerCase())) index.set(w.w.toLowerCase(), { ...w, group: g.id });
  for (const t of content?.topics || [])
    for (const w of t.words) {
      const key = w.word.toLowerCase();
      if (!index.has(key)) index.set(key, { w: w.word, ipa: w.ipa, pos: '', vi: w.vi, ex: w.example, exVi: '' });
    }
  return index;
}

export const hasIndex = () => !!index;

/** Dictionary entry for a word as it appears in a text, or null when it is not in the offline list. */
export function lookup(raw) {
  if (!index) return null;
  for (const l of lemmas(raw)) {
    const hit = index.get(l);
    if (hit) return hit;
  }
  return null;
}

// ---- Online translation ---------------------------------------------------------

const cache = new Map();

async function google(text) {
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=vi&dt=t&q=${encodeURIComponent(text)}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  return (data?.[0] || []).map((part) => part?.[0] || '').join('').trim();
}

async function gemini(text) {
  const key = AiSettings.get().apiKey.trim();
  if (!key) throw new Error('no key');
  const res = await fetch('https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
    body: JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: `Translate into natural Vietnamese. Reply with the translation only.\n\n${text}` }] }],
      generationConfig: { temperature: 0.2 },
    }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  return (data?.candidates?.[0]?.content?.parts || []).map((p) => p.text || '').join('').trim();
}

/** Vietnamese for any English text; remembered for the session. */
export async function translate(text) {
  const t = text.trim();
  if (!t) return '';
  if (cache.has(t)) return cache.get(t);
  let vi = '';
  try {
    vi = await google(t);
  } catch {
    vi = await gemini(t).catch(() => '');
  }
  if (!vi) throw new Error('Không dịch được lúc này. Kiểm tra mạng rồi thử lại.');
  cache.set(t, vi);
  return vi;
}
