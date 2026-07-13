const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const profileTs = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.ts'), 'utf8')
const profileWxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')

assert.match(
  profileTs,
  /confirmUnbindWechat\(\)[\s\S]*?if \(!this\.data\.profile\.email\)[\s\S]*?请先绑定QQ邮箱/,
  'WeChat unbind should require email login to remain available'
)
assert.match(
  profileTs,
  /\/users\/me\/wechat-bind`[\s\S]*?method: 'DELETE'/,
  'WeChat unbind should call the authenticated delete endpoint'
)
assert.match(
  profileTs,
  /confirmUnbindEmail\(\)[\s\S]*?if \(!this\.data\.profile\.wechatOpenid\)[\s\S]*?请先绑定微信/,
  'email unbind should require WeChat login to remain available'
)
assert.match(
  profileTs,
  /\/users\/me\/email-bind`[\s\S]*?method: 'DELETE'/,
  'email unbind should call the authenticated delete endpoint'
)
assert.match(profileWxml, /bindtap="confirmUnbindWechat"/, 'bound WeChat row should expose unbind')
assert.match(profileWxml, /bindtap="confirmUnbindEmail"/, 'bound email row should expose unbind')

console.log('profile account unbind tests passed')
