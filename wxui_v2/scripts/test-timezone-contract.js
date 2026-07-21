const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.join(__dirname, '..')
const appTs = fs.readFileSync(path.join(root, 'app.ts'), 'utf8')
const detailTs = fs.readFileSync(path.join(root, 'pages/goods/detail.ts'), 'utf8')

assert.match(appTs, /BEIJING_UTC_OFFSET_MINUTES\s*=\s*8\s*\*\s*60/)
assert.match(appTs, /getUTCFullYear\(\)/)
assert.match(appTs, /getUTCMonth\(\)/)
assert.match(appTs, /getUTCDate\(\)/)

assert.match(detailTs, /localDateTime\s*=\s*text\.match/)
assert.match(detailTs, /BEIJING_UTC_OFFSET_MILLISECONDS/)
assert.match(detailTs, /getUTCMonth\(\)/)
assert.match(detailTs, /getUTCHours\(\)/)
assert.doesNotMatch(detailTs, /date\.getHours\(\)/)

console.log('timezone contract checks passed')
