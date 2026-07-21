const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const appTs = fs.readFileSync(path.join(__dirname, '..', 'app.ts'), 'utf8')

assert.match(appTs, /onShow\(\)[\s\S]*checkAndShowAnnouncement/)
assert.match(appTs, /cancelText:\s*'忽略'/)
assert.match(appTs, /confirmText:\s*'已读'/)
assert.match(appTs, /if \(result\.confirm\)[\s\S]*wx\.setStorageSync/)
assert.doesNotMatch(appTs, /if \(result\.cancel\)[\s\S]*wx\.setStorageSync/)
assert.match(appTs, /readState\.date === today && readState\.revision === announcement\.revision/)
assert.match(appTs, /catch \(_error\)[\s\S]*公告加载失败不应阻塞小程序/)

console.log('announcement flow checks passed')
