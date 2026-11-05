// Builds videos.json: recent videos from official English-learning YouTube channels, each checked
// through YouTube oEmbed so the title and channel are real. Videos play in YouTube's own embedded
// player; nothing is downloaded or re-hosted.
// Usage: node web/tools/build-videos.cjs <oembed.tsv>   (lines: handle, videoId, channel, width, height, title)
const fs = require('fs');
const path = require('path');

const CHANNELS = [
  { handle: 'SuperSimpleSongs', name: 'Super Simple Songs', level: 'A1', kids: true, about: 'Bài hát tiếng Anh vui nhộn cho bé và người mới bắt đầu' },
  { handle: 'sesamestreet', name: 'Sesame Street', level: 'A1', kids: true, about: 'Elmo, Big Bird và các bạn: chữ cái, số đếm, kỹ năng sống' },
  { handle: 'VOALearningEnglish', name: 'VOA Learning English', level: 'A2', about: 'Tin tức đọc chậm, rõ, từ vựng có giới hạn cho người học' },
  { handle: 'bbclearningenglish', name: 'BBC Learning English', level: 'B1', about: 'Từ vựng qua tin tức, 6 Minute English, phát âm, ngữ pháp' },
  { handle: 'TEDEd', name: 'TED-Ed', level: 'B2', about: 'Phim hoạt hình giải thích khoa học, lịch sử, tư duy' },
];

const root = path.resolve(__dirname, '../..');
const rows = fs.readFileSync(process.argv[2], 'utf8').split('\n').filter(Boolean).map((l) => l.split('\t'));
const channels = CHANNELS.map((c) => ({
  ...c,
  videos: rows
    .filter(([h, , author]) => h === c.handle && author.startsWith(c.name.split(' ')[0]))
    // Shorts are vertical and too fast for learners.
    .filter(([, , , w, h, title]) => +w >= +h && !/#shorts?\b/i.test(title))
    .slice(0, 16)
    .map(([, id, , , , title]) => ({ id, title: title.trim() })),
}));

const out = `${JSON.stringify({ updated: new Date().toISOString().slice(0, 10), channels }, null, 1)}\n`;
fs.writeFileSync(path.join(root, 'web/data/videos.json'), out);
fs.writeFileSync(path.join(root, 'app/src/main/assets/videos.json'), out);
console.log(channels.map((c) => `${c.name}: ${c.videos.length}`).join(' · '));
