const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')

assert.match(
  wxml,
  /bindtap="bindWechat"/,
  'profile editor should expose a bindWechat tap target'
)

assert.match(
  wxml,
  /微信快捷登录已绑定/,
  'profile editor should show the current WeChat binding state'
)

assert.match(
  wxml,
  /绑定微信/,
  'profile editor should show a clear bind WeChat action'
)

console.log('profile wechat bind wxml tests passed')
