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
  /class="actions-row profile-actions-row"[\s\S]*?class="profile-action profile-action-primary"[\s\S]*?class="profile-action profile-action-danger"/,
  'profile goods actions should use a dedicated action band instead of crowding the title row'
)

console.log('profile wxml tests passed')
