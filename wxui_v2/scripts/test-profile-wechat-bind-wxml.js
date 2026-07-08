const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')

assert.match(
  wxml,
  /<view class="settings-item" bindtap="openAccountBindModal">/,
  'profile page should expose account binding as a settings entry'
)

assert.match(
  wxml,
  /<view class="modal-dialog account-bind-modal" wx:if="{{showAccountBindModal}}">/,
  'profile page should render a standalone account binding modal'
)

assert.match(
  wxml,
  /绑定账号/,
  'account binding entry and modal should use the title 绑定账号'
)

assert.match(
  wxml,
  /bindtap="bindWechat"/,
  'account binding modal should expose a bindWechat tap target'
)

assert.match(
  wxml,
  /bindtap="bindEmail"/,
  'account binding modal should expose a bindEmail tap target'
)

console.log('profile wechat bind wxml tests passed')
