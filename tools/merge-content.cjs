// Merges generated topic and vocabulary files into the app content, shared by Android and web.
// Usage: node web/tools/merge-content.cjs <folder with topics-*.json and vocab-*.json>
const fs = require('fs');
const path = require('path');

const dir = process.argv[2];
const root = path.resolve(__dirname, '../..');
const assets = path.join(root, 'app/src/main/assets');
const read = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));
const files = (prefix) => fs.readdirSync(dir).filter((f) => f.startsWith(prefix) && f.endsWith('.json')).sort();
const problems = [];

// ---- Topics -------------------------------------------------------------------
const content = read(path.join(assets, 'content.json'));
const NEW_CATEGORIES = [
  { id: 'home', name: 'Gia đình & nhà cửa', nameEn: 'Family & home', emoji: '🏠', color: '#FFD166' },
  { id: 'health', name: 'Sức khỏe', nameEn: 'Health', emoji: '🩺', color: '#F2667A' },
];
for (const c of NEW_CATEGORIES) if (!content.categories.some((x) => x.id === c.id)) content.categories.push(c);
const ids = new Set(content.topics.map((t) => t.id));
const phrases = new Set(content.topics.flatMap((t) => t.phrases.map((p) => p.en.toLowerCase())));
for (const f of files('topics-')) {
  for (const t of read(path.join(dir, f))) {
    if (ids.has(t.id)) continue;
    const where = `${f}:${t.id}`;
    if (!content.categories.some((c) => c.id === t.category)) problems.push(`${where} unknown category ${t.category}`);
    for (const k of ['title', 'titleVi', 'emoji', 'level']) if (!t[k]) problems.push(`${where} missing ${k}`);
    if (t.phrases?.length !== 6 || t.words?.length !== 6 || !(t.native?.length >= 1)) problems.push(`${where} wrong counts`);
    const lines = t.dialogue?.lines || [];
    if (lines.length < 6 || lines[0].s !== 'A' || !lines.some((l) => l.s === 'B')) problems.push(`${where} bad dialogue`);
    for (const l of lines) if (!l.en || !l.vi || !['A', 'B'].includes(l.s)) problems.push(`${where} bad line`);
    for (const p of t.phrases) if (phrases.has(p.en.toLowerCase())) problems.push(`${where} duplicate phrase "${p.en}"`);
    t.phrases.forEach((p) => phrases.add(p.en.toLowerCase()));
    ids.add(t.id);
    content.topics.push(t);
  }
}
// Group topics by category so lists read in order.
const order = content.categories.map((c) => c.id);
content.topics.sort((a, b) => order.indexOf(a.category) - order.indexOf(b.category));

// ---- Vocabulary -----------------------------------------------------------------
const POS = new Set(['n', 'v', 'adj', 'adv', 'prep', 'pron', 'conj', 'det', 'num', 'phr', 'excl']);
const seen = new Set();
const groups = [];
let dropped = 0;
for (const f of files('vocab-')) {
  for (const g of read(path.join(dir, f))) {
    const words = [];
    for (const w of g.words) {
      const key = w.w.trim().toLowerCase();
      if (seen.has(key)) { dropped++; continue; }
      if (!w.ipa || !w.vi || !w.ex || !POS.has(w.pos)) problems.push(`${f}:${g.id}:${w.w} incomplete`);
      seen.add(key);
      words.push({ w: w.w.trim(), ipa: w.ipa, pos: w.pos, vi: w.vi, ex: w.ex, exVi: w.exVi || '' });
    }
    // Later files may top up a group that already exists.
    const existing = groups.find((x) => x.id === g.id);
    if (existing) existing.words.push(...words);
    else groups.push({ id: g.id, title: g.title, emoji: g.emoji, words });
  }
}

const write = (name, data) => {
  const text = `${JSON.stringify(data, null, 2)}\n`;
  fs.writeFileSync(path.join(assets, name), text);
  fs.writeFileSync(path.join(root, 'web/data', name), text);
};
write('content.json', content);
// The word list is large; one group per line keeps it compact but still diffable.
const vocabText = `{"groups":[\n${groups.map((g) => JSON.stringify(g)).join(',\n')}\n]}\n`;
fs.writeFileSync(path.join(assets, 'vocab.json'), vocabText);
fs.writeFileSync(path.join(root, 'web/data', 'vocab.json'), vocabText);

console.log(`topics: ${content.topics.length} in ${content.categories.length} categories`);
console.log(`vocab: ${groups.length} groups, ${seen.size} words (${dropped} duplicates dropped)`);
console.log(problems.length ? `PROBLEMS (${problems.length}):\n${problems.slice(0, 40).join('\n')}` : 'no problems');
