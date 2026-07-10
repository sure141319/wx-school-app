const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/profile/profile.wxml'), 'utf8')

assert.match(
  wxml,
  /<view class="profile-ticket">[\s\S]*?校园卖家资料/,
  'profile identity should use a useful Chinese campus seller heading'
)

assert.doesNotMatch(
  wxml,
  /STUDENT SELLER|EDIT PROFILE|MY LISTINGS|>AC<|>AD<|>FB</i,
  'profile page should not add decorative English copy or abbreviations'
)

assert.doesNotMatch(
  wxml,
  /我的闲置/,
  'profile listings should not repeat the redundant 我的闲置 kicker above the section title'
)

assert.match(
  wxml,
  /<view class="profile-summary" bindtap="openProfileEditor">/,
  'profile summary row should open the profile editor so the avatar, text, and arrow share one tap target'
)

assert.doesNotMatch(
  wxml,
  /<view class="avatar-btn profile-summary-avatar" bindtap="openProfileEditor">/,
  'profile summary avatar should not bind separately because the parent row owns the tap target'
)

assert.match(
  wxml,
  /<view class="goods-line-main">[\s\S]*?<view class="goods-info">[\s\S]*?<view class="actions-row profile-actions-row">[\s\S]*?class="profile-action profile-action-primary"[\s\S]*?>编辑<\/[\s\S]*?class="profile-action profile-action-danger"/,
  'profile goods actions should sit to the right of each listing with the concise 编辑 label'
)

assert.match(wxml, /class="goods-line-index">0{{index \+ 1}}<\//, 'profile goods rows should retain the compact visual index')
assert.doesNotMatch(wxml, /class="profile-action profile-action-primary"[^>]*>编辑信息</, 'profile goods rows should use the concise 编辑 button label')

console.log('profile wxml tests passed')
