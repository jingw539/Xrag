const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const TARGETS = [
  path.join(ROOT, 'frontend', 'src'),
  path.join(ROOT, 'frontend', 'index.html'),
  path.join(ROOT, 'frontend', 'public')
]

const textExts = new Set(['.js', '.jsx', '.ts', '.tsx', '.vue', '.css', '.scss', '.html', '.json', '.md', '.yml', '.yaml', '.svg'])
const decoder = new TextDecoder('utf-8', { fatal: true })

const badFiles = []

function isTextFile(filePath) {
  if (fs.statSync(filePath).isDirectory()) return false
  const ext = path.extname(filePath).toLowerCase()
  return textExts.has(ext)
}

function walk(target) {
  if (!fs.existsSync(target)) return
  const stat = fs.statSync(target)
  if (stat.isDirectory()) {
    for (const name of fs.readdirSync(target)) {
      walk(path.join(target, name))
    }
    return
  }
  if (!isTextFile(target)) return
  const data = fs.readFileSync(target)
  try {
    const text = decoder.decode(data)
    if (text.includes('\ufffd') || text.includes('锟')) {
      badFiles.push({ file: target, reason: 'mojibake' })
    }
  } catch (err) {
    badFiles.push({ file: target, reason: 'not-utf8' })
  }
}

for (const t of TARGETS) walk(t)

if (badFiles.length) {
  console.error('Encoding check failed:')
  for (const item of badFiles) {
    console.error(`- ${item.file} [${item.reason}]`)
  }
  process.exit(1)
}

console.log('Encoding check passed.')
