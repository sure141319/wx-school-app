const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const profileWxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')
const appWxss = fs.readFileSync(path.resolve(__dirname, '../app.wxss'), 'utf8')

function cssBlock(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = appWxss.match(new RegExp(`${escaped}\\s*\\{([\\s\\S]*?)\\n\\}`, 'm'))
  assert.ok(match, `${selector} style block should exist`)
  return match[1]
}

assert.match(
  profileWxml,
  /<text class="settings-icon settings-icon-account">@<\/text>\s*<text class="settings-label">绑定账号<\/text>/,
  'account settings row should use dedicated icon and label classes for stable vertical alignment'
)

assert.match(
  profileWxml,
  /<text class="settings-icon settings-icon-feedback">\?<\/text>\s*<text class="settings-label">意见反馈<\/text>/,
  'feedback settings row should use dedicated icon and label classes for stable vertical alignment'
)

assert.doesNotMatch(
  cssBlock('.wechat-bind-row'),
  /border|background|border-radius/,
  'wechat binding row should not render as an extra nested card inside the account binding modal'
)

assert.match(
  cssBlock('.wechat-bind-status'),
  /line-height:\s*1/,
  'bound status text should use a tight line-height so it aligns with row titles'
)

console.log('profile account bind layout tests passed')
