// Builds picturebooks.json from Global Storybooks / African Storybook markdown (CC BY / CC BY-NC):
// English originals from global-asp/asp-source/en and Vietnamese translations from global-asp/global-asp/vi.
// Usage: node web/tools/build-picturebooks.cjs <folder with en/NNNN.md, vi/NNNN.md and img/NNNN.txt>
// (img/NNNN.txt lists the files in gsn-imagebank/NNNN; books without pictures read as text only.)
const fs = require('fs');
const path = require('path');

const dir = process.argv[2];
const root = path.resolve(__dirname, '../..');
// Images stay on the CDN; bundling every page would add tens of megabytes to both apps.
const IMAGES = 'https://cdn.jsdelivr.net/gh/global-asp/gsn-imagebank@master';

/** Title, page texts and the credit lines of one story file. */
function parse(md) {
  const text = md.replace(/\r\n/g, '\n');
  const title = (text.match(/^#\s+(.+)$/m) || [])[1]?.trim() || '';
  const sections = text.split(/^##\s*$/m).slice(1).map((s) => s.trim());
  const credits = {};
  const pages = [];
  for (const s of sections) {
    const bullets = [...s.matchAll(/^\*\s*([^:]+):\s*(.+)$/gm)];
    if (bullets.length && bullets.length === s.split('\n').filter(Boolean).length) {
      for (const [, k, v] of bullets) credits[k.trim().toLowerCase()] = v.replace(/\[|\]/g, '').trim();
    } else pages.push(s.split('\n').map((l) => l.trim()).filter(Boolean).join(' '));
  }
  return { title, pages, credits };
}

const words = (pages) => pages.join(' ').split(/\s+/).filter(Boolean).length;
/** Picture books are short; length is a fair guide to how hard they read. */
const levelFor = (n) => (n <= 80 ? 'A1' : n <= 250 ? 'A2' : n <= 500 ? 'B1' : 'B2');

const books = [];
const skipped = [];
for (const file of fs.readdirSync(path.join(dir, 'vi')).filter((f) => /^\d{4}\.md$/.test(f)).sort()) {
  const id = file.slice(0, 4);
  const enFile = path.join(dir, 'en', file);
  if (!fs.existsSync(enFile)) { skipped.push(`${id}: no English`); continue; }
  const en = parse(fs.readFileSync(enFile, 'utf8'));
  const vi = parse(fs.readFileSync(path.join(dir, 'vi', file), 'utf8'));
  if (!en.pages.length || en.pages.length !== vi.pages.length) {
    skipped.push(`${id}: ${en.pages.length} English pages vs ${vi.pages.length} Vietnamese`);
    continue;
  }
  const n = words(en.pages);
  const listFile = path.join(dir, 'img', `${id}.txt`);
  const have = new Set(fs.existsSync(listFile) ? fs.readFileSync(listFile, 'utf8').split(/\s+/).filter(Boolean) : []);
  const img = (k) => {
    const name = `${String(k).padStart(2, '0')}.jpg`;
    return have.has(name) ? `${IMAGES}/${id}/${name}` : null;
  };
  books.push({
    id,
    title: en.title,
    titleVi: vi.title,
    level: levelFor(n),
    words: n,
    cover: img(1) || img(2),
    license: (en.credits.license || vi.credits.license || 'CC-BY').replace(/^CC-/, 'CC '),
    text: en.credits.text || '',
    illustration: en.credits.illustration || '',
    translation: vi.credits.translation || '',
    pages: en.pages.map((p, i) => ({ en: p, vi: vi.pages[i], img: img(i + 2) })),
  });
}
const ORDER = ['A1', 'A2', 'B1', 'B2'];
books.sort((a, b) => ORDER.indexOf(a.level) - ORDER.indexOf(b.level) || a.words - b.words);

const out = `{"source":"Global Storybooks · African Storybook (CC BY / CC BY-NC)","books":[\n${books.map((b) => JSON.stringify(b)).join(',\n')}\n]}\n`;
fs.writeFileSync(path.join(root, 'web/data/picturebooks.json'), out);
fs.writeFileSync(path.join(root, 'app/src/main/assets/picturebooks.json'), out);
const by = ORDER.map((l) => `${l}:${books.filter((b) => b.level === l).length}`).join(' ');
console.log(`${books.length} books (${by}), ${Math.round(out.length / 1024)} KB`);
if (skipped.length) console.log('skipped:', skipped.join('; '));
