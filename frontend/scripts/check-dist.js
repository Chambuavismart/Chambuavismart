const fs = require('fs');
const path = require('path');

function getLatestMTime(dir) {
  let latest = 0;
  const stack = [dir];
  while (stack.length) {
    const cur = stack.pop();
    const stat = fs.statSync(cur);
    if (stat.isDirectory()) {
      for (const name of fs.readdirSync(cur)) {
        // Skip dist output
        if (name === 'dist' || name === 'node_modules') continue;
        stack.push(path.join(cur, name));
      }
    } else {
      if (stat.mtimeMs > latest) latest = stat.mtimeMs;
    }
  }
  return latest;
}

function main() {
  const root = path.resolve(__dirname, '..');
  const srcDir = path.join(root, 'src');
  const distIndex = path.join(root, 'dist', 'app', 'index.html');

  if (!fs.existsSync(distIndex)) {
    console.log('[CHECK_DIST] dist/app/index.html not found. Run: npm run build');
    process.exitCode = 2;
    return;
  }

  const latestSrc = getLatestMTime(srcDir);
  const distStat = fs.statSync(distIndex);
  const distTime = distStat.mtimeMs;

  if (distTime + 1000 < latestSrc) { // allow 1s skew
    const srcDate = new Date(latestSrc).toLocaleString();
    const distDate = new Date(distTime).toLocaleString();
    console.log(`[CHECK_DIST] OUTDATED: Latest src change ${srcDate} is newer than dist ${distDate}. Run: npm run build`);
    process.exitCode = 1;
  } else {
    const distDate = new Date(distTime).toLocaleString();
    console.log(`[CHECK_DIST] OK: dist is up-to-date. Last build: ${distDate}`);
  }
}

main();
