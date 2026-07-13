const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const profileTs = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.ts'), 'utf8')
const profileWxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')
const appWxss = fs.readFileSync(path.resolve(__dirname, '../app.wxss'), 'utf8')

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
assert.match(
  profileWxml,
  /wx:else class="account-bind-state-actions"[\s\S]*?未绑定[\s\S]*?bindtap="toggleBindEmailForm"[\s\S]*?showBindEmailForm \? '收起' : '去绑定'/,
  'unbound email should render a compact state with an explicit expand action'
)
assert.match(
  profileWxml,
  /<block wx:elif="{{showBindEmailForm}}">[\s\S]*?绑定QQ邮箱[\s\S]*?<text wx:else class="wechat-bind-desc account-bind-empty-desc">/,
  'email binding fields should render only after the compact state is expanded'
)
assert.match(
  profileTs,
  /unbindEmail\(\)[\s\S]*?showBindEmailForm: false/,
  'successful email unbind should return to the compact state'
)
assert.match(
  appWxss,
  /\.account-unbind-button,\s*\.account-bind-toggle-button\s*\{[\s\S]*?display:\s*inline-flex[\s\S]*?height:\s*54rpx[\s\S]*?align-items:\s*center[\s\S]*?justify-content:\s*center[\s\S]*?line-height:\s*1\.2/,
  'account action button labels should be vertically centered with explicit flex geometry'
)

console.log('profile account unbind tests passed')
